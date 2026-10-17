"""Official pizza detail identity/size and per-serving nutrition; no list coverage claim."""
import re
from urllib.parse import urlparse, parse_qs
from lxml import html

HEADERS = ['제품명','사이즈','총중량(g)','조각수','1회제공량(조각)','1회분중량(g)',
           '열량(kcal/1회분)','당류(g/1회분)','단백질(g/1회분)','포화지방(g/1회분)','나트륨(mg/1회분)']

# Exact display/table pairs reviewed on the same official product detail.
# Do not strip "피자" globally or merge names across different product IDs.
TABLE_NAMES = {
    'menu_detail_pizza.php:37': ('리얼치즈', '리얼치즈피자'),
    'menu_detail_pizza.php:38': ('리얼페페로니', '리얼페페로니피자'),
    'menu_detail_pizza.php:40': ('슈퍼슈프림', '슈퍼슈프림피자'),
    'menu_detail_pizza.php:42': ('하와이안', '하와이안피자'),
    'menu_detail_pizza.php:36': ('할라불고기', '할라불고기피자'),
}
def norm(value):
    return re.sub(r'\s+', '', value)


def identity(brand, payload):
    """Current sale title/ID/sizes are independent of a possibly stale fact table."""
    assert brand.get('brand', brand.get('name')) == '청년피자', 'Wrong brand'
    uri = urlparse(brand['sourceUrl']);query = parse_qs(uri.query, keep_blank_values=True)
    assert uri.scheme == 'https' and uri.netloc in ('youngmanpizza.co.kr','www.youngmanpizza.co.kr')
    assert uri.path in ('/sub01/menu_detail_pizza.php','/sub01/menu_detail_pizza2.php') and not uri.fragment
    assert set(query) <= {'seq','search_type','search_text','page_num'}
    assert len(query.get('seq', [])) == 1 and re.fullmatch(r'[1-9][0-9]*', query['seq'][0])
    root = html.fromstring(re.sub(r'<!--.*?-->','', payload.decode('utf-8') if isinstance(payload,bytes) else payload, flags=re.S))
    title = root.xpath('//section[contains(concat(" ",normalize-space(@class)," ")," menu_detail ")]//div[@class="info_title"]/p')
    assert len(title)==1
    name = ' '.join(title[0].text_content().split());assert name
    sale_sizes = root.xpath('//ul[@class="info_price"]/li/div[@class="size"]/text()')
    sale_sizes = [s.strip() for s in sale_sizes];assert sale_sizes and all(s in ('R','L') for s in sale_sizes)
    assert len(sale_sizes)==len(set(sale_sizes))
    product_key=uri.path.rsplit('/',1)[1]+':'+query['seq'][0]
    return root,name,sale_sizes,product_key


def details(brand, payload):
    root,name,sale_sizes,product_key=identity(brand,payload)
    tables = root.xpath('//div[@class="sec3_table"]/table');assert len(tables)==1
    table = tables[0]
    assert [norm(n.text_content()) for n in table.xpath('./thead/tr/th')] == HEADERS, 'Official serving/column schema changed'
    product = table.xpath('./tbody/tr/th');assert len(product)==1, 'Detail/table product mismatch'
    pair=(norm(name),norm(product[0].text_content()))
    assert pair[0]==pair[1] or TABLE_NAMES.get(product_key)==pair, 'Detail/table product mismatch'
    result={}
    for tr in table.xpath('./tbody/tr'):
        cells = [n.text_content().strip() for n in tr.xpath('./td')]
        if not any(cells):continue
        assert len(cells)==10 and cells[0] in sale_sizes and cells[0] not in result, 'Size/row mismatch'
        assert all(re.fullmatch(r'\d+(?:\.\d+)?', s) for s in cells[1:]), 'Unknown values must not become zero'
        size=cells[0];total,pieces,portion,grams,kcal,sugar,protein,sat,sodium=map(float,cells[1:])
        assert total>0 and pieces>0 and pieces.is_integer() and portion>0 and portion.is_integer() and portion<=pieces
        assert 0<grams<=total and all(0<=n<=100_000 for n in (kcal,sugar,protein,sat,sodium))
        external=product_key+':'+size
        result[size]=dict(name=name+' ('+size+')',externalId=external,
            fact=dict(energyKcal=kcal,carbohydrateGrams=None,proteinGrams=protein,fatGrams=None,
                sodiumMilligrams=sodium,sugarsGrams=sugar,saturatedFatGrams=sat,
                servingAmount=grams,servingUnit='g',
                servingDescription=f'공식 {size} 사이즈 1회 제공량 {portion:g}조각 ({grams:g}g) 기준 · 피자 전체 중량 아님'))
    assert set(result)==set(sale_sizes), 'Every offered size requires its own explicit serving row'
    return result


def rows(brand, markup):
    _,name,sizes,product_key=identity(brand,markup)
    return [(name+' ('+size+')','피자',brand['sourceUrl'],product_key+':'+size) for size in sizes]


def parse(menu, payload):
    matches=[d for d in details(menu,payload).values() if d['externalId']==menu['externalId'] and norm(d['name'])==norm(menu['name'])]
    assert len(matches)==1, 'Exact official product ID/name/size required'
    return matches[0]['fact']
