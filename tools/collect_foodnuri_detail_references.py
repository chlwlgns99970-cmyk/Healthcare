"""Index the public age-meal catalog, then fetch exact unbranded-food/title matches."""
import collections,concurrent.futures,csv,hashlib,json,re,urllib.request
from pathlib import Path
from urllib.parse import urljoin,parse_qs,urlparse
from lxml import html
from finish_recipe_reference_mapping import keys
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/all-food-detail-audit/foodnuri';AS=ROOT/'app/src/main/assets/fooddata'
BASE='https://www.foodnuri.go.kr';LIST=BASE+'/portal/age/ageFood/list.do?menuNo=300147'
def text(e):return re.sub(r'\s+',' ',e.text_content()).strip()
def fetch(url):
    name=hashlib.sha256(url.encode()).hexdigest()[:20];p=OUT/(name+'.html')
    if not p.exists():p.write_bytes(urllib.request.urlopen(url,timeout=30).read())
    raw=p.read_bytes();return html.fromstring(raw.decode('utf-8')),dict(url=url,rawFile=str(p.relative_to(ROOT)),sha256=hashlib.sha256(raw).hexdigest())
def save(n,value):(OUT/n).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def run():
    OUT.mkdir(exist_ok=True,parents=True)
    first,_=fetch(LIST)
    pages=[int(parse_qs(urlparse(a.get('href')).query).get('pageIndex',['1'])[0]) for a in first.xpath('//a[contains(@href,"ageFood/list.do")]')]
    last=max(pages);assert 1<=last<=100
    catalog={};receipts=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for doc,receipt in pool.map(fetch,[LIST+'&pageIndex='+str(i) for i in range(1,last+1)]):
            receipts.append(receipt)
            for a in doc.xpath('//a[contains(@href,"ageFood/view.do")]'):
                href=urljoin(BASE,a.get('href'));key=parse_qs(urlparse(href).query)['key'][0]
                title=text(a)
                if title:catalog[key]=dict(key=key,name=title,url=BASE+'/portal/age/ageFood/view.do?key='+key+'&menuNo=300147')
    save('catalog.json',list(catalog.values()));save('list-captures.json',receipts)
    foods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in csv.DictReader((AS/n).open(encoding='utf-8-sig'))]
    index=collections.defaultdict(list)
    for f in foods:
        if f['sourceType'] not in ('K-FIND','RDA-MENUZEN') or f['brand']:continue
        for k in keys(f['name']):index[k].append(f)
        if f['name']=='스파게티_토마토소스':index['토마토스파게티'].append(f)
    residual=json.loads((ROOT/'data-source/recipe-residual-93/remaining-91-foods.json').read_text(encoding='utf-8'))
    residualKeys={k for r in residual for k in keys(r['name'])}
    selected=[r for r in catalog.values() if keys(r['name']) & (set(index)|residualKeys)]
    output=[]
    for item in selected:
        try:
            doc,receipt=fetch(item['url']);title=text(doc.xpath('//h2[@class="subject"]')[0])
            assert keys(title)==keys(item['name'])
            tables=doc.xpath('//table[.//th[contains(.,"1인분")]]')
            ingredients=text(tables[0]) if tables else ''
            basis=text(doc.xpath('//div[@class="thum_info"]')[0]) if doc.xpath('//div[@class="thum_info"]') else ''
            targets={f['id']:f for k in keys(title) for f in index.get(k,[])}
            output.append(dict(**item,**{k:v for k,v in receipt.items() if k!='url'},title=title,rawIngredientText=ingredients,rawBasis=basis,foodIds=sorted(targets),residualNameMatched=bool(keys(title)&residualKeys),status='CAPTURED' if ingredients else 'NO_TEXT_INGREDIENT_TABLE',checkedAt='2026-10-05'))
        except Exception as error:output.append(dict(**item,status='FAILED',error=str(error)))
    save('exact-reference-source-candidates.json',output)
    print(json.dumps(dict(catalog=len(catalog),listPages=last,exactCandidates=len(selected),captured=sum(r['status']=='CAPTURED' for r in output),residualMatches=[r['name'] for r in output if r.get('residualNameMatched')]),ensure_ascii=False))
if __name__=='__main__':run()
