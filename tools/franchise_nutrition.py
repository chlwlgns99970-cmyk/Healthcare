"""Official nutrition refresh, separate from menu identity and legacy app fields.

Only reviewed exact-ID/name contracts can fill values. Missing sources are never
evidence that a brand does not publish nutrition. Existing facts survive errors.
"""
import concurrent.futures
import hashlib
import io
import functools
import json
import math
import re
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from lxml import html

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / 'data-source/franchise-sync/nutrition-sources.json'
STATUSES = {'NUTRITION_COMPLETE', 'NUTRITION_PARTIAL', 'NUTRITION_NOT_PUBLISHED',
            'NUTRITION_SOURCE_FOUND_UNMATCHED', 'NUTRITION_SOURCE_MISSING'}
FIELDS = ('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams', 'sodiumMilligrams')


def number(value, suffix):
    match = re.fullmatch(r'\s*(\d+(?:\.\d+)?|\.\d+)\s*' + re.escape(suffix) + r'\s*', str(value))
    return float(match[1]) if match else None


def parse_starbucks(menu, markup):
    assert menu['brand'] == '스타벅스'
    code = parse_qs(urlparse(menu['sourceUrl']).query)['product_cd'][0]
    assert menu['externalId'] in ('drink:' + code, 'food:' + code)
    products = []
    text = markup.decode('utf-8') if isinstance(markup, bytes) else markup
    for raw in html.fromstring(text).xpath('//script[@type="application/ld+json"]/text()'):
        document = json.loads(raw)
        products.extend(p for p in document.get('@graph', []) if p.get('@type') == 'Product')
    matches = [p for p in products if str(p.get('sku')) == code and p.get('name', '').strip() == menu['name'].strip()]
    assert len(matches) == 1, 'Official product ID/name did not match'
    product = matches[0]
    props = {}
    for prop in product.get('additionalProperty', []):
        key = prop['name']
        assert key not in props, 'Conflicting official nutrition properties'
        props[key] = prop['value']
    result = {field: number(props[label], unit) if label in props else None for field, label, unit in (
        ('energyKcal', '칼로리', 'kcal'), ('carbohydrateGrams', '탄수화물', 'g'),
        ('proteinGrams', '단백질', 'g'), ('fatGrams', '지방', 'g'), ('sodiumMilligrams', '나트륨', 'mg'))}
    # Saturated fat is not total fat; sugar is not carbohydrate.
    amount = re.fullmatch(r'\s*(\d+(?:\.\d+)?)\s*(g|ml)\s*', str(props.get('용량', '')))
    result.update(servingAmount=float(amount[1]) if amount else None,
                  servingUnit=amount[2] if amount else None, servingDescription=props.get('사이즈', ''))
    # The same official page embeds the literal used by its nutrition renderer.
    # Decode JSON only; never execute a script or use the alternate _L basis.
    text = markup.decode('utf-8') if isinstance(markup, bytes) else markup
    match = re.search(r'view:\s*remapView\(', text)
    if match:
        raw, _ = json.JSONDecoder().raw_decode(text[match.end():])
        assert str(raw['PRODUCT_CD']) == code and raw['PRODUCT_NM'].strip() == menu['name'].strip()
        for field, key, unit in (('energyKcal','KCAL','kcal'), ('carbohydrateGrams','CHABO','g'),
                                  ('proteinGrams','PROTEIN','g'), ('fatGrams','FAT','g'), ('sodiumMilligrams','SODIUM','mg')):
            value = number(str(raw.get(key, '')) + unit, unit)
            if result[field] is not None and value is not None:
                assert result[field] == value, 'Conflicting official nutrition basis'
            if result[field] is None:
                result[field] = value
        raw_amount = number(str(raw.get('STANDARD', '')) + str(raw.get('UNIT', '')), str(raw.get('UNIT', ''))) if raw.get('UNIT') in ('g','ml') else None
        if raw_amount is not None and raw_amount > 0:
            assert result['servingAmount'] is None or result['servingAmount'] == raw_amount
            result.update(servingAmount=raw_amount, servingUnit=raw['UNIT'])
    return result


def parse_hansot(menu, markup):
    assert menu['brand'] == '한솥'
    assert urlparse(menu['sourceUrl']).path == '/menu/menu_view/' + menu['externalId']
    root = html.fromstring(markup.decode('utf-8') if isinstance(markup, bytes) else markup)
    # Only this product's menu title and calorie block; no unrelated promotion.
    titles = root.xpath('//section[contains(@class,"menu_view")]//h3[@class="he_tit"]')
    all_titles = [' '.join(t.xpath('.//span[@class="dp2"]//text()')).strip() for t in titles]
    assert menu['name'] in all_titles, 'Official Hansot detail title did not match'
    values = root.xpath('//div[contains(@class,"menu_info") and contains(@class,"quantity")]/p/span/text()')
    assert len(values) == 1
    kcal = number(values[0] + 'kcal', 'kcal')
    assert kcal is not None
    # The published detail has kcal but no explicit amount: never invent 1 serving.
    return dict(energyKcal=kcal, carbohydrateGrams=None, proteinGrams=None, fatGrams=None,
                sodiumMilligrams=None, servingAmount=None, servingUnit=None, servingDescription='')


def parse_mcdonalds(menu, payload):
    assert menu['brand'] == '맥도날드'
    data = json.loads(payload)
    assert data['resultCode'] == 100
    rows = data['resultObject']['list']
    assert len(rows) == data['resultObject']['totalCount']
    normalize = lambda value: re.sub(r'[\s®™]', '', value)
    matches = [r for r in rows if normalize(r['menuName']) == normalize(menu['name'])]
    assert len(matches) == 1, 'Nutrition name/variant not uniquely matched'
    row = matches[0]
    values = dict(part.split(';', 1) for part in row['nutritionFacts'].split(',') if ';' in part)
    def value(label, suffix):
        # Percent daily values follow the actual amount. Less-than/ranges remain unknown.
        return number(values.get(label, '').split('(')[0], suffix)
    weight = re.fullmatch(r'(\d+(?:\.\d+)?)(g|ml)', values.get('중량', ''))
    return dict(energyKcal=value('열량', 'Kcal') if '열량' in values else number(str(row['calorie'])+'kcal','kcal'),
                carbohydrateGrams=value('탄수화물','g'), proteinGrams=value('단백질','g'), fatGrams=value('지방','g'),
                sodiumMilligrams=value('나트륨','mg'), servingAmount=float(weight[1]) if weight else None,
                servingUnit=weight[2] if weight else None, servingDescription='',
                nutritionProductId=row['plu'], matchedBy='EXACT_NAME_AND_VARIANT')


PARSERS = {'starbucks-product-jsonld': parse_starbucks, 'hansot-detail-kcal': parse_hansot,
           'mc-nutrition-json': parse_mcdonalds}


def dunkin_pages(url, get):
    records = []
    expected_total = None
    last_page = 1
    page = 1
    while page <= last_page:
        page_url = url if page == 1 else url + '?page=' + str(page)
        payload = get(page_url)
        root = html.fromstring(payload.decode('utf-8'))
        data = json.loads(root.xpath('//*[@id="app"]/@data-page')[0])['props']['productsNutrition']
        meta = data['meta']
        assert meta['current_page'] == page and meta['path'] == url and 1 <= meta['last_page'] <= 64
        if expected_total is None:expected_total = meta['total'];last_page = meta['last_page']
        assert expected_total == meta['total'] and last_page == meta['last_page']
        for row in data['data']:
            records.append(dict(row, _sourceUrl=page_url, _sourceSha256=hashlib.sha256(payload).hexdigest()))
        page += 1
    assert len(records) == expected_total and len({r['id'] for r in records}) == expected_total
    return json.dumps(records, ensure_ascii=False).encode()


def parse_dunkin(menu, payload):
    assert menu['brand'] == '던킨'
    rows = json.loads(payload)
    match = [r for r in rows if 'products:'+str(r['id']) == menu['externalId'] and r['TITLE'].strip() == menu['name'].strip()]
    assert len(match) == 1, 'Dunkin official product ID/name not matched'
    row = match[0]
    amount = number(str(row.get('NUTRITION_SERVING',''))+'g','g')
    return dict(energyKcal=number(str(row.get('NUTRITION_KCAL',''))+'kcal','kcal'),
                carbohydrateGrams=None, fatGrams=None,  # Official FAT column is saturated fat.
                proteinGrams=number(str(row.get('NUTRITION_PROTEIN',''))+'g','g'),
                sodiumMilligrams=number(str(row.get('NUTRITION_NATRIUM',''))+'mg','mg'),
                servingAmount=amount if amount and amount > 0 else None, servingUnit='g' if amount and amount > 0 else None,
                servingDescription='공식 1회 제공량', sourceUrl=row['_sourceUrl'], sourceSha256=row['_sourceSha256'])


PARSERS['dunkin-nutrition-pages'] = parse_dunkin


@functools.lru_cache(maxsize=2)
def salady_pdf_records(payload):
    from pypdf import PdfReader
    reader=PdfReader(io.BytesIO(payload))
    assert 1 <= len(reader.pages) <= 64
    records=[]
    for index,page in enumerate(reader.pages):
        text=page.extract_text()
        assert all(label in text for label in ('제공량(g)','열량(Kcal)','탄수화물(g)','단백질(g)','지방(g)','나트륨(mg)'))
        brand='샐러디&샌드위치' if 'SALADY&SANDWICH' in text else '샐러디'
        page_rows=[]
        for line in text.splitlines():
            match=re.fullmatch(r'(.+?)\s+((?:\d+(?:\.\d+)?\s+){7}\d+(?:\.\d+)?)',line.strip())
            if match:
                values=[float(x) for x in match[2].split()]
                assert len(values)==8
                page_rows.append(dict(brand=brand,name=match[1],values=values,page=index+1))
        assert page_rows,'Nutrition PDF table structure changed'
        records.extend(page_rows)
    return records


def parse_salady(menu,payload):
    assert menu['brand'] in ('샐러디','샐러디&샌드위치')
    normalize=lambda name:re.sub(r'\s','',name)
    assert normalize(menu['name'])!='아메리카노','Official PDF explicitly specifies HOT; menu variant unspecified'
    matches=[r for r in salady_pdf_records(payload) if r['brand']==menu['brand'] and normalize(r['name'])==normalize(menu['name'])]
    assert len(matches)==1,'PDF brand/name/variant did not uniquely match'
    row=matches[0];amount,kcal,carbs,sugar,protein,fat,saturated,sodium=row['values']
    return dict(energyKcal=kcal,carbohydrateGrams=carbs,proteinGrams=protein,fatGrams=fat,sodiumMilligrams=sodium,
                servingAmount=amount,servingUnit='g',servingDescription='공식 기본 구성 · 추가 토핑·음료는 별도 · 베이스 변경 시 영양값이 달라집니다.',
                sourcePage=row['page'],matchedBy='EXACT_BRAND_NAME_AND_VARIANT')


PARSERS['salady-nutrition-pdf']=parse_salady


def validate_nutrition(fact, allowed_hosts):
    uri = urlparse(fact['sourceUrl'])
    assert uri.scheme in ('https', 'http') and not uri.username and not uri.password
    assert uri.hostname in allowed_hosts
    assert datetime.fromisoformat(fact['checkedAt'].replace('Z', '+00:00')).utcoffset() is not None
    assert re.fullmatch('[a-f0-9]{64}', fact['sourceSha256'])
    for key in FIELDS:
        value = fact.get(key)
        assert value is None or isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and 0 <= value <= 100000
    amount = fact.get('servingAmount')
    assert amount is None and fact.get('servingUnit') is None or isinstance(amount, (int, float)) and not isinstance(amount, bool) and math.isfinite(amount) and 0 < amount <= 10000 and fact.get('servingUnit') in ('g', 'ml', '개', '인분', '잔')


def status(fact):
    if fact.get('energyKcal') is not None and fact.get('servingAmount') is not None:
        return 'NUTRITION_COMPLETE'
    return 'NUTRITION_PARTIAL' if any(fact.get(k) is not None for k in FIELDS) else 'NUTRITION_SOURCE_FOUND_UNMATCHED'


def refresh(menus, registry=None, fetcher=None):
    registry = registry if registry is not None else json.loads(REGISTRY.read_text(encoding='utf-8'))
    sources = {b['brandId']: b for b in registry}
    checked = datetime.now(timezone.utc).isoformat()
    audit = []

    def fetch(url):
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'HealthcareMenuAudit/1.0 (official nutrition refresh)'}), timeout=12) as response:
            assert response.status == 200 and urlparse(response.url).hostname == urlparse(url).hostname
            payload = response.read(2000001)
            assert 0 < len(payload) <= 2000000
            return payload

    get = fetcher or fetch
    shared = {}
    for source in registry:
        if source.get('adapter') in PARSERS and source.get('url') and any(m['brandId']==source['brandId'] for m in menus):
            try: shared[source['url']] = dunkin_pages(source['url'], get) if source['adapter']=='dunkin-nutrition-pages' else get(source['url'])
            except Exception as error: shared[source['url']] = error

    def one(original):
        menu = dict(original)
        source = sources.get(menu['brandId'])
        adapter = source.get('adapter') if source else None
        evidence = dict(id=menu['id'], brandId=menu['brandId'], brand=menu['brand'], name=menu['name'])
        if adapter in PARSERS:
            try:
                url = source.get('url') or menu['sourceUrl']
                assert urlparse(url).hostname in source['allowedHosts']
                payload = shared[url] if url in shared else get(url)
                if isinstance(payload, Exception):raise payload
                fact = PARSERS[adapter](menu, payload)
                fact.update(sourceUrl=fact.get('sourceUrl',url), checkedAt=checked,
                            sourceSha256=fact.get('sourceSha256',hashlib.sha256(payload).hexdigest()), officialProductId=menu['externalId'])
                fact['sourceType']='PDF' if adapter=='salady-nutrition-pdf' else 'JSON' if adapter=='mc-nutrition-json' else 'HTML'
                validate_nutrition(fact, source['allowedHosts'])
                menu['officialNutrition'] = fact
                menu['nutritionStatus'] = status(fact)
                evidence.update(result='MATCHED', nutritionStatus=menu['nutritionStatus'], sourceUrl=fact['sourceUrl'])
            except Exception as error:
                # Never erase previously verified official values on fetch/schema errors.
                menu['nutritionStatus'] = status(menu['officialNutrition']) if menu.get('officialNutrition') else 'NUTRITION_SOURCE_FOUND_UNMATCHED'
                evidence.update(result='FAILED_RETAINED', nutritionStatus=menu['nutritionStatus'], error=type(error).__name__, reason=str(error)[:300])
        else:
            menu['nutritionStatus'] = status(menu['officialNutrition']) if menu.get('officialNutrition') else 'NUTRITION_SOURCE_FOUND_UNMATCHED' if source and source.get('evidenceStatus')=='SOURCE_FOUND' else 'NUTRITION_SOURCE_MISSING'
            evidence.update(result='SOURCE_MISSING', nutritionStatus=menu['nutritionStatus'])
        return menu, evidence

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(one, menus))
    refreshed = [m for m, _ in rows]
    audit = [a for _, a in rows]
    counts = Counter(m['nutritionStatus'] for m in refreshed)
    assert all(m['nutritionStatus'] in STATUSES for m in refreshed)
    known = {k: sum(m.get('officialNutrition', {}).get(k) is not None for m in refreshed) for k in FIELDS + ('servingAmount',)}
    old={m['id']:m.get('officialNutrition',{}) for m in menus}
    linked=lambda m:any(m.get(k) is not None for k in FIELDS)
    newly=sum(linked(m.get('officialNutrition',{})) and not linked(old[m['id']]) for m in refreshed)
    changed=sum(linked(old[m['id']]) and any(old[m['id']].get(k)!=m.get('officialNutrition',{}).get(k) for k in FIELDS+('servingAmount','servingUnit')) for m in refreshed)
    return refreshed, dict(total=len(refreshed), states=dict(counts), known=known,
                           nutritionLinkedMenus=sum(linked(m.get('officialNutrition',{})) for m in refreshed),
                           newlyVerifiedMenus=newly, nutritionChangedMenus=changed,
                           recordable=sum(m.get('officialNutrition', {}).get('energyKcal') is not None and m.get('officialNutrition', {}).get('servingAmount') is not None for m in refreshed),
                           matched=sum(a['result']=='MATCHED' for a in audit), failed=sum(a['result']=='FAILED_RETAINED' for a in audit), menus=audit)
