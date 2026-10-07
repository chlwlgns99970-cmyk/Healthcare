"""Targeted public official product investigation; cached once and exact identity only.

No recipe, ingredient, or allergy value is inferred from a product name. Sources
and failed requests live in the investigation, while exact positive declarations
alone are exported to the producer CSV. Current menus do not prove K-FIND's older
nutrition version; successful evidence retains staleCandidate=true.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import re
import urllib.error
import urllib.parse
import urllib.request
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'app/build/product-targeted'
MANIFEST = CACHE / 'manifest.json'
OUTPUT = ROOT / 'data-source/food-quality/product-targeted-evidence.csv'
AUDIT = ROOT / 'data-source/food-quality/product-targeted-investigation.json'
DATE = '2026-10-04'
VERSION = 'targeted-product-2026-10-04-v1'
FIELDS = 'foodItemId,sourceFoodCode,brand,name,ingredientText,ingredients,allergenText,allergens,ingredientStatus,allergenStatus,sourceUrl,checkedAt,sourceDate,parserVersion,evidenceKind,mayContainAllergens,crossContactText,staleCandidate,sourceHash'.split(',')
EXCLUDED = {'할리스','커피빈','빽다방','파스쿠찌','드롭탑','바나프레소','요거프레소','이디야','투썸플레이스'}


def read_csv(path):
    with path.open(encoding='utf-8-sig', newline='') as handle:
        return list(csv.DictReader(handle))


def target_foods():
    research = read_csv(ROOT / 'data-source/recommendation/recommendation-ingredient-research.csv')
    ids = {r['foodItemId'] for r in research if r['reason'] == 'OFFICIAL_PRODUCT_COMPOSITION_UNRESOLVED' and r['brand'] not in EXCLUDED}
    result = {}
    for path in (ROOT / 'app/src/main/assets/fooddata').glob('*.csv'):
        for row in read_csv(path):
            if row.get('id') in ids and row.get('sourceFoodCode'):
                result[row['id']] = row
    return [result[k] for k in sorted(result)]


def decode(raw):
    try:
        return raw.decode('utf-8-sig')
    except UnicodeDecodeError:
        return raw.decode('cp949', errors='replace')


def fetch_one(url, form=None, json_data=None):
    CACHE.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8')) if MANIFEST.exists() else {}
    body = json.dumps(json_data, ensure_ascii=False, sort_keys=True).encode() if json_data is not None else urllib.parse.urlencode(form).encode() if form else None
    key = url if body is None else 'POST ' + url + ' ' + hashlib.sha256(body).hexdigest()[:12]
    if key in manifest:
        return manifest[key]
    entry = {'url': url, 'requestKey': key, 'method': 'POST' if body is not None else 'GET', 'checkedAt': DATE, 'parserVersion': VERSION}
    if form:
        entry['publicForm'] = form
    headers = {'User-Agent': 'Mozilla/5.0', 'Accept': 'application/json,text/html,*/*'}
    if json_data is not None:
        entry['publicJsonBody'] = json_data
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(urllib.parse.quote(url, safe=':/?&=+%'), data=body, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read()
            digest = hashlib.sha256(raw).hexdigest()
            filename = hashlib.sha256(key.encode()).hexdigest()[:16] + '.raw'
            (CACHE / filename).write_bytes(raw)
            entry.update(status=response.status, finalUrl=response.url, contentType=response.headers.get('Content-Type',''),
                         sourceHash=digest, byteCount=len(raw), file=filename)
    except (urllib.error.URLError, TimeoutError, OSError, UnicodeError) as error:
        entry.update(status=getattr(error, 'code', None), error=type(error).__name__ + ': ' + str(error))
    return entry


def fetch_many(urls):
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8')) if MANIFEST.exists() else {}
    pending = [u for u in dict.fromkeys(urls) if u not in manifest]
    with ThreadPoolExecutor(max_workers=6) as executor:
        for entry in executor.map(fetch_one, pending):
            manifest[entry.get('requestKey', entry['url'])] = entry
            MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + '\n', encoding='utf-8')
            print(entry['url'], entry.get('status'), entry.get('byteCount'), entry.get('error',''), flush=True)
    CACHE.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return manifest


def cached(url):
    entry = json.loads(MANIFEST.read_text(encoding='utf-8')).get(url, {})
    raw = (CACHE / entry['file']).read_bytes() if entry.get('file') else b''
    if raw and hashlib.sha256(raw).hexdigest() != entry['sourceHash']:
        raise ValueError('source hash mismatch: ' + url)
    return decode(raw), entry


def inspect(url):
    source, entry = cached(url)
    print(json.dumps(entry, ensure_ascii=False))
    stripped = re.sub(r'<script\b.*?</script>|<style\b.*?</style>', '', source, flags=re.S|re.I)
    print('TEXT', ' '.join(html.unescape(re.sub('<[^>]+>', ' ', stripped)).split())[:8000])
    print('LINKS', re.findall(r'(?:href|src)=[\"\']([^\"\']+)', source, re.I)[:160])


def clean_text(value):
    return ' '.join(html.unescape(re.sub(r'<[^>]+>', ' ', value or '')).split())


def canonical_menu(food, raw):
    """Strip only an explicitly recorded classification, never guess a dish alias."""
    value = food['name']
    representative = raw.get('대표식품명', '')
    removed = []
    if representative and value.startswith(representative + '_'):
        value = value[len(representative) + 1:]
        removed.append(representative)
    if value.startswith('간편조리세트_') and '_간편조리세트_' in raw.get('식품명', ''):
        value = value[len('간편조리세트_'):]
        removed.append('간편조리세트')
    return value, removed


def parse_pizzaschool(source, expected_name):
    from collect_coffee_bakery_metadata import normalize, allergen_declaration
    title = re.search(r'<title[^>]*>(.*?)</title>', source, re.S|re.I)
    name = clean_text(title[1]).removesuffix(' – 피자스쿨') if title else ''
    if normalize(name) != normalize(expected_name):
        return None
    sections = {}
    for match in re.finditer(r'<h4[^>]*>(.*?)</h4>.*?<div[^>]+class=[\"\']iconlist_content\s*[\"\'][^>]*>(.*?)</div>', source, re.S):
        sections[clean_text(match[1])] = clean_text(match[2])
    allergy_cells = []
    for table in re.findall(r'<table\b[^>]*>(.*?)</table>', source, re.S|re.I):
        if '알레르기 유발 물질' not in clean_text(table):
            continue
        for tr in re.findall(r'<tr\b[^>]*>(.*?)</tr>', table, re.S|re.I):
            th = re.search(r'<th\b[^>]*>(.*?)</th>', tr, re.S|re.I)
            td = re.search(r'<td\b[^>]*>(.*?)</td>', tr, re.S|re.I)
            if th and clean_text(th[1]) and td:
                allergy_cells.append((clean_text(th[1]), clean_text(td[1])))
    labels = ', '.join(label for _, label in allergy_cells)
    allergens, status = allergen_declaration(labels)
    toppings = {clean_text(t) for t in sections.get('주요토핑','').split(',') if clean_text(t)}
    # Country-of-origin ingredients are explicit source nouns, not inferred recipes.
    origin = sections.get('원산지','')
    toppings |= set(re.findall(r'\(([가-힣]+)\s*:', origin))
    return {'officialName': name, 'ingredients': '|'.join(sorted(toppings)),
            'ingredientText': ' | '.join(k + ': ' + sections[k] for k in ('설명','주요토핑','원산지') if sections.get(k)),
            'ingredientStatus': 'PARTIAL_DESCRIPTION' if sections else 'UNKNOWN',
            'allergenText': ' | '.join(component + ': ' + label for component,label in allergy_cells),
            'allergens': allergens, 'allergenStatus': status}


class ParserTests(unittest.TestCase):
    def test_component_label_preserves_extra_positive_allergens(self):
        source = ('<title>치즈오븐스파게티 – 피자스쿨</title>'
                  '<h4>주요토핑</h4><div class="iconlist_content "><p>스파게티면, 치즈</p></div>'
                  '<table><tr><td>알레르기 유발 물질</td></tr>'
                  '<tr><th>소스</th><td>밀/대두/우유/돼지고기/아황산류/조개류(굴)</td></tr></table>')
        row = parse_pizzaschool(source, '치즈오븐스파게티')
        self.assertEqual(row['allergenStatus'], 'CONFIRMED_LABEL')
        self.assertEqual(set(row['allergens'].split('|')), {'밀','대두','우유','돼지고기','아황산류','조개류'})
        self.assertEqual(set(row['ingredients'].split('|')), {'스파게티면','치즈'})

    def test_wrong_identity_and_missing_allergy_never_certify_safety(self):
        source = ('<title>치즈오븐스파게티 – 피자스쿨</title>'
                  '<h4>주요토핑</h4><div class="iconlist_content ">치즈</div>')
        self.assertIsNone(parse_pizzaschool(source, '치즈오븐크림스파게티'))
        row = parse_pizzaschool(source, '치즈오븐스파게티')
        self.assertEqual(row['allergenStatus'], 'UNKNOWN')
        self.assertEqual(row['allergens'], '')


def generate():
    from collect_coffee_bakery_metadata import Cache, tlj_list, normalize, allergen_declaration
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    foods = target_foods()
    if AUDIT.exists():
        existing = json.loads(AUDIT.read_text(encoding='utf-8'))
        ids = {r['foodItemId'] for r in existing.get('investigations', [])}
        if ids:
            foods = [r for path in (ROOT/'app/src/main/assets/fooddata').glob('*.csv') for r in read_csv(path)
                     if r.get('id') in ids and r.get('sourceFoodCode')]
    raw_index = {r['foodItemId']:r for r in read_csv(ROOT/'data-source/food-quality/raw-identity-fields.csv')}
    tlj_cache = Cache()
    tlj_catalogue = tlj_list(tlj_cache)
    tlj_url = 'https://www.tlj.co.kr/product/result.asp'
    tlj_entry = tlj_cache.entries['GET ' + tlj_url]
    ps_url = 'http://pizzaschool.net/menu/치즈오븐스파게티/'
    burger_detail_key = next(k for k in manifest if k.startswith('POST ') and 'BKR0634.json' in k)
    burger_allergy_key = next(k for k in manifest if k.startswith('POST ') and 'BKR0347.json' in k)
    burger_detail = json.loads(cached(burger_detail_key)[0])['body']
    burger_meta = json.loads(cached(burger_allergy_key)[0])['body']
    alvolo_url = 'https://api2.pizzaalvolo.co.kr/api/v1/customer/common/product/list?regDate=&isEcoupon=n'
    alvolo = json.loads(cached(alvolo_url)[0])['data']['productList']
    mcd_url = 'https://www.mcdonalds.co.kr/api/v1/kor/product/allergy'
    mcd = json.loads(cached(mcd_url)[0])['resultObject']['list']
    paths = {
        'GS 리테일 심플리쿡': ['https://www.gsfresh.com/','https://www.gsretail.com/gsretail/ko/brand/gs-supermarket/fresh-product/simply-cook','https://hpsimg.gsretail.com/public/js/index.03857aa4.js'],
        '피자알볼로': [alvolo_url,'https://api2.pizzaalvolo.co.kr/api/v1/customer/common/product/noticeinfo'],
        '피자스쿨': ['http://pizzaschool.net/menu/', ps_url],
        '봉수아피자': ['https://bongsuapizza.com/사이드','https://bongsuapizza.com/원산지표시판',
                       'https://lh3.googleusercontent.com/Aj6Il2yJyr_XHD2QwS7cSUE9LPTTM8bkebBB9HUKdx-DhoOChpZ2XYaouIwVCLVAJXR--lKyag68J8NPh3HBiR1LtmW5I8YR9O0gO5aFNgCcPE2Jf0Cy24U=w800'],
        '롤링핀': ['https://rollingpin.co.kr/default/sub/menu/page03.php'],
        '맥도날드': [mcd_url,'https://www.mcdonalds.co.kr/kor/menu/sides'],
        '버거킹': [burger_detail_key, burger_allergy_key],
        '따삐오': ['https://www.tapiau.co.kr/','http://www.tapiau.co.kr/','https://spcsamlip.co.kr/brand/store','https://spcsamlip.co.kr/brand/tapiau/'],
        '크로플덕오리아가씨': ['https://croffleduck.co.kr/','http://croffleduck.co.kr/'],
    }
    rows, investigations = [], []
    for food in sorted(foods, key=lambda r:r['id']):
        raw = raw_index[food['id']]
        menu, stripped = canonical_menu(food, raw)
        info = {'foodItemId':food['id'],'sourceFoodCode':food['sourceFoodCode'],'brand':food['brand'],
                'name':food['name'],'canonicalMenuName':menu,'removedExplicitClassification':stripped,
                'rawFoodName':raw.get('식품명',''),'rawReferenceWeight':raw.get('식품중량',''),
                'rawDataCreatedAt':raw.get('데이터생성일자',''), 'ingredientOutcome':'UNKNOWN','allergenOutcome':'UNKNOWN',
                'exactOfficialName':'','officialCode':'','checkedAt':DATE}
        row = dict.fromkeys(FIELDS,'') | {'foodItemId':food['id'],'sourceFoodCode':food['sourceFoodCode'],
              'brand':food['brand'],'name':food['name'],'ingredientStatus':'UNKNOWN','allergenStatus':'UNKNOWN',
              'checkedAt':DATE,'parserVersion':VERSION,'staleCandidate':'false'}
        brand = food['brand']
        request_keys = paths.get(brand, [])
        reason = 'NO_EXACT_CURRENT_OFFICIAL_PRODUCT'
        if brand == '뚜레쥬르':
            exact = [r for r in tlj_catalogue if normalize(r['name']) == normalize(menu)]
            if exact:
                raise ValueError('An exact TLJ item needs detail review before export: ' + menu)
            info.update(scannedOfficialMenus=len(tlj_catalogue), reviewedSources=[tlj_entry],
                        reusedCache='data-source/franchise/raw/coffee-bakery-request-manifest.json',
                        note='442 official current menu detail pages were previously cached; no exact menu title. No fresh catalogue collection.')
            row['sourceUrl'] = tlj_url
            row['sourceHash'] = tlj_entry['sourceHash']
            reason = 'NO_EXACT_CURRENT_OFFICIAL_MENU_AFTER_442_DETAIL_CACHE_REVIEW'
        elif brand == '피자스쿨':
            parsed = parse_pizzaschool(cached(ps_url)[0], menu)
            if not parsed:
                raise ValueError('Pizza School exact title changed')
            row.update({k:v for k,v in parsed.items() if k in FIELDS})
            row.update(sourceUrl=ps_url, sourceHash=manifest[ps_url]['sourceHash'], staleCandidate='true')
            info.update(exactOfficialName=parsed['officialName'],officialCode='WORDPRESS-725',
                        note='All declared component allergen rows are captured. Ingredients are partial toppings/country-of-origin evidence; no full recipe is claimed. Current 400g menu does not verify older K-FIND 420g analysis.')
            reason = 'EXACT_CURRENT_MENU_PARTIAL_INGREDIENTS_AND_FULL_DECLARED_ALLERGEN_TABLE'
        elif brand == '버거킹':
            if burger_detail['menuCd'] != '2200284' or normalize(burger_detail['menuNm']) != normalize(menu):
                raise ValueError('Burger King exact code/title mismatch')
            labels = [r['allergyNm'] for r in burger_meta['allAllergyList'] if normalize(r['menuNm']) == normalize(menu)]
            if len(labels) != 1:
                raise ValueError('Burger King label absent/ambiguous')
            allergens,status = allergen_declaration(labels[0])
            origins = [r for r in burger_meta['originInfoList'] if normalize(menu) in [normalize(t) for t in r['pattiNote'].split(':')[-1].split(',')]]
            row.update(ingredientText=' | '.join('원산지: '+r['pattiDivNm']+' — '+r['pattiNote'] for r in origins),
                       ingredients='|'.join(sorted({r['pattiDivNm'] for r in origins})),ingredientStatus='PARTIAL_DESCRIPTION' if origins else 'UNKNOWN',
                       allergenText=labels[0],allergens=allergens,allergenStatus=status,
                       sourceUrl=manifest[burger_allergy_key]['url'],sourceHash=manifest[burger_allergy_key]['sourceHash'],staleCandidate='true')
            info.update(exactOfficialName=burger_detail['menuNm'],officialCode='2200284',
                        userProductUrl='https://www.burgerking.co.kr/menu/detail/2200284',
                        note='Anonymous official menu detail code and exact name were checked. Allergy table is exact menu name. Explicit country-of-origin association alone supplies 닭고기; item_ingredient_list marketing tags are excluded.')
            reason = 'EXACT_CURRENT_MENU_CODE_ORIGIN_AND_ALLERGEN_DECLARATION'
        elif brand == '피자알볼로':
            exact = [r for r in alvolo if normalize(r['name']) == normalize(menu)]
            if exact:
                raise ValueError('Alvolo exact item needs composition review')
            info.update(scannedOfficialMenus=len(alvolo), candidates=[{'officialCode':r['no'],'officialName':r['name']} for r in alvolo if '김치볶음밥' in r['name']],
                        note='맘맘 promotional menu and 김치볶음밥+계란후라이 differ from source identity; no identity/product-code mapping is published. Do not transfer their labels.')
            reason = 'OFFICIAL_CURRENT_VARIANTS_DIFFER_NO_EXACT_PRODUCT_IDENTITY'
        elif brand == '봉수아피자':
            source,_ = cached(request_keys[0])
            if not re.search(r'>\s*국물떡볶이\s*</span>', source):
                raise ValueError('Bongsua exact menu card changed')
            info.update(exactOfficialName='국물떡볶이', note='Exact current side card contains only title/photo. Official origin image was visually reviewed and does not list 국물떡볶이. No ingredient or allergen label is published on these sources.')
            reason = 'EXACT_OFFICIAL_MENU_CARD_NO_PRODUCT_COMPOSITION_OR_ALLERGEN_LABEL'
        elif brand == '파리바게뜨':
            if '그릭요거트' in menu:
                request_keys = ['https://www.paris.co.kr/?s=플레인+저당+그릭요거트+컵케이크','https://www.paris.co.kr/?s=그릭요거트']
                info['candidates'] = ['저당 그릭요거트 케이크(조각)','[파란라벨]저당 그릭요거트 케이크']
            else:
                request_keys = ['https://www.paris.co.kr/?s=갈릭스틱토스트','https://www.paris.co.kr/?s=갈릭']
            info['note'] = 'Official exact-title search returned 0 products. Broader literal keyword search was also reviewed; other product titles do not match.'
            reason = 'OFFICIAL_EXACT_NAME_SEARCH_ZERO_RESULTS'
        elif brand == '롤링핀':
            info['note'] = 'Official brunch menu lists 11 named cards including 그린 샐러드, but no 과일리코타 샐러드. The different salad description is not reused.'
            info['scannedOfficialMenus'] = 11
        elif brand == '맥도날드':
            exact = [r for r in mcd if normalize(r['menuName']) == normalize(menu)]
            if exact:
                raise ValueError('McDonalds exact item needs label review')
            info.update(scannedOfficialAllergenMenus=len(mcd),candidates=[r['menuName'] for r in mcd if '스낵랩' in r['menuName']],
                        note='The K-FIND analysis uses a generic 닭고기또띠아샌드위치 title. No official source maps that code to either current named snack wrap; no fuzzy ingredient/allergen join.')
            reason = 'GENERIC_ANALYZED_FOOD_NAME_NO_EXACT_BRAND_MENU_IDENTITY_MAP'
        elif brand == '프레시지':
            request_keys = ['https://corp.fresheasy.co.kr/','https://brand.naver.com/fresheasy']
            if '바질크림' in menu:
                request_keys += ['https://brand.naver.com/fresheasy/products/8670535121']
                info.update(candidate={'officialProductCode':'8670535121','officialName':'프레시지 바질크림 빠네 파스타 냉동 밀키트 500g, 1개','currentPackageWeight':'500g'},
                            note='Corporate site links this official brand store. Current frozen 500g variant cannot verify original 2022 analysis recipe of 776g; the candidate is not joined.')
                reason = 'OFFICIAL_BASE_NAME_DIFFERENT_PRODUCT_VARIANT_RECIPE_UNVERIFIED'
            elif '볼로네제' in menu:
                request_keys += ['https://brand.naver.com/fresheasy/search?q=볼로네제라구파스타','https://brand.naver.com/fresheasy/search?q=볼로네제%20라구%20파스타']
                info['note'] = 'Official store exact normalized-title search and spaced-title search both explicitly report no search result.'
                reason = 'OFFICIAL_EXACT_NAME_SEARCH_ZERO_RESULTS'
            else:
                request_keys += ['https://brand.naver.com/fresheasy/search?q=매콤밀푀유나베','https://brand.naver.com/fresheasy/search?q=매콤%20밀푀유나베']
                info['note'] = 'Official store exact normalized-title search and spaced-title search both explicitly report no search result.'
                reason = 'OFFICIAL_EXACT_NAME_SEARCH_ZERO_RESULTS'
        elif brand == 'HY 잇츠온':
            request_keys = [k for k,e in manifest.items() if e.get('publicJsonBody',{}).get('keyword') == menu]
            if len(request_keys) != 1:
                raise ValueError('Missing exact HY public search query: ' + menu)
            response = json.loads(cached(request_keys[0])[0])
            if response.get('resultCode') != '0000' or response.get('hasError') or response.get('body',{}).get('timeoutYn') == 'Y':
                raise ValueError('HY search is not a successful result')
            if response['body']['shopping']['shoppingCount'] != 0:
                raise ValueError('HY returned candidate requiring exact product review')
            info.update(officialSearchQuery=response['body']['header']['keyword'],officialSearchCount=0,
                        note='Verified anonymous official search API resultCode=0000, hasError=false, shoppingCount=0, no timeout. Client-rendered initial zero counts alone were not used.')
            reason = 'OFFICIAL_EXACT_NAME_SEARCH_ZERO_RESULTS'
        elif brand == 'GS 리테일 심플리쿡':
            info['note'] = 'Official GSfresh mall explicitly shows service-termination page. Current GS Retail website contains no exact named product menu/label for these older analyzed products.'
            reason = 'OFFICIAL_PRODUCT_SHOP_SERVICE_ENDED_NO_ACCESSIBLE_EXACT_LABEL'
        elif brand == '따삐오':
            info['note'] = 'Candidate brand domain DNS fails. Official SPC Samlip store-brand catalogue was reviewed; no 따삐오 product menu or 구운햄치즈 토스트 label. Legacy brand path returns 404.'
            reason = 'LEGACY_BRAND_PRODUCT_PAGE_UNAVAILABLE_NO_EXACT_LABEL'
        elif brand == '크로플덕오리아가씨':
            info['note'] = 'Identified brand-domain HTTPS request fails expired-certificate verification; HTTP returns 403. Product label could not be read. Third-party menus are excluded.'
            reason = 'OFFICIAL_DOMAIN_CERTIFICATE_EXPIRED_AND_HTTP_FORBIDDEN'
        if brand != '뚜레쥬르':
            info['reviewedSources'] = [manifest[k] for k in request_keys]
            if not row['sourceUrl'] and request_keys:
                selected = request_keys[0]
                row['sourceUrl'] = manifest[selected]['url']
                row['sourceHash'] = manifest[selected].get('sourceHash','')
        info.update(reason=reason, ingredientOutcome=row['ingredientStatus'],allergenOutcome=row['allergenStatus'])
        scope = 'EXACT_BRAND_NORMALIZED_MENU_NAME' if info['exactOfficialName'] else 'NO_OFFICIAL_PRODUCT_IDENTITY_JOIN'
        if info['officialCode']:
            scope += '|OFFICIAL_MENU_CODE:' + str(info['officialCode'])
        row['evidenceKind'] = scope + '|' + reason
        if row['staleCandidate'] == 'true':
            row['evidenceKind'] += '|CURRENT_OFFICIAL_MENU_RECIPE_VERSION_UNVERIFIED'
        rows.append(row)
        investigations.append(info)
    with OUTPUT.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS, lineterminator='\n')
        writer.writeheader(); writer.writerows(rows)
    report = {'checkedAt':DATE,'parserVersion':VERSION,'scope':'27 recommendation rows, 26 unique non-cafe official-product targets from the original 44 unresolved product-composition recommendation rows.',
              'counts':{'recommendationRows':27,'uniqueFoodItems':len(rows),'brands':len({r['brand'] for r in rows}),
                        'partialIngredientEvidence':sum(r['ingredientStatus']=='PARTIAL_DESCRIPTION' for r in rows),
                        'completeIngredientDeclarations':0,'confirmedAllergenDeclarations':sum(r['allergenStatus']=='CONFIRMED_LABEL' for r in rows),
                        'unknownIngredientRows':sum(r['ingredientStatus']=='UNKNOWN' for r in rows),
                        'unknownAllergenRows':sum(r['allergenStatus']=='UNKNOWN' for r in rows)},
              'investigations':investigations,'publicRequestManifest':str(MANIFEST.relative_to(ROOT)).replace('\\','/'),
              'requestCount':len(manifest),'requests':list(manifest.values()),'csvSha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
              'identityPolicy':'Exact bundled foodItemId and sourceFoodCode. Remove only explicit original classification prefixes. Never join historical generic food titles, changed product variants, promotional versions or fuzzy names.',
              'ingredientPolicy':'Only literal source toppings or product-associated country-of-origin ingredient nouns. Product titles, marketing tags, flavors and allergy labels are not ingredient inputs.',
              'allergenPolicy':'Whole product/component declaration captured only for exact identity. Canonical supported tags plus other declared positive allergens are retained. Missing labels remain UNKNOWN; cross-contact is separate.',
              'sourceDatePolicy':'No source recipe/publication date is invented. checkedAt is acquisition date; URLs, photo dates and script build dates do not establish product recipe date.',
              'nutritionChanges':0,'privateOrAuthenticatedRequests':0,'apiKeysUsed':0,'focusedParserTests':2}
    AUDIT.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True)+'\n', encoding='utf-8')
    print(json.dumps(report['counts'], ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['fetch','inspect','targets','burger-list','generate','test'])
    parser.add_argument('urls', nargs='*')
    args = parser.parse_args()
    if args.action == 'generate':
        generate()
    elif args.action == 'test':
        unittest.main(argv=['targeted-products'], exit=False)
    elif args.action == 'burger-list':
        form = {'message': json.dumps({'header': {'trcode': 'BKR0632', 'login_session_id': ''}, 'body': {'menuKeywordList': []}})}
        entry = fetch_one('https://web-prd.burgerking.co.kr/burgerking/BKR0632.json', form)
        manifest = json.loads(MANIFEST.read_text(encoding='utf-8')) if MANIFEST.exists() else {}
        manifest[entry['requestKey']] = entry
        MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        print(entry)
    elif args.action == 'fetch':
        fetch_many(args.urls)
    elif args.action == 'inspect':
        for url in args.urls:
            inspect(url)
    else:
        print(json.dumps(target_foods(), ensure_ascii=False, indent=2))
