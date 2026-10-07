"""Collect public RDA/MFDS recipe facts for exact, partial-composition joins.

No nutrition, serving conversion or actual-menu allergy certification is imported.
Raw pages are cached outside tracked source; normalized factual fields + digest are retained.
Network collection is explicit (--collect); offline regeneration reads the same snapshots.
"""
from pathlib import Path
import argparse, concurrent.futures, csv, hashlib, html, json, math, re, urllib.parse, urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recommendation'
CACHE = ROOT / 'app/build/food-quality-qa/recipe-source'
VERSION = 'public-recipe-v1'
CHECKED = '2026-10-04'

def read_csv(path):
    with path.open(encoding='utf-8-sig', newline='') as f: return list(csv.DictReader(f))

def write_csv(path, rows, fields):
    with path.open('w', encoding='utf-8', newline='') as f:
        w = csv.DictWriter(f, fieldnames=fields); w.writeheader(); w.writerows(rows)

def plain(value):
    # RDA uses literal Korean labels such as <만두소>; these are source text, not HTML.
    return re.sub(r'\s+', ' ', html.unescape(re.sub(r'<[A-Za-z!/][^>]*>', ' ', value))).strip()

def canonical(value):
    # Formatting only. Do not remove ingredient/brand/flavour qualifiers.
    return re.sub(r'\s+', '', html.unescape(value)).casefold()

def recipe_name(value):
    # RDA explicitly labels preparation variants; this is still a reference recipe, not exact product.
    return re.sub(r'\s*[<\[]방법\s*\d+[>\]]\s*$', '', value).strip()

def get(key, url, collect):
    path = CACHE / (key + '.html')
    if not path.exists():
        if not collect: raise FileNotFoundError(path)
        request = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
        with urllib.request.urlopen(request, timeout=25) as response: raw = response.read()
        path.write_bytes(raw)
    raw = path.read_bytes()
    return raw.decode('utf-8', 'replace'), hashlib.sha256(raw).hexdigest()

def rda_list_url(page, size=70):
    fields = {'searchName':'','searchArea':'','fcode1':'','fcode2':'','fcode3':'','fcode4':'',
              'mcode1':'','mcode2':'','ccode1':'','ccode2':'','ccode3':'','page':str(page),
              'cboAlign':'1','pagesize':str(size)}
    filters = urllib.parse.quote(json.dumps({'groupOp':'AND', 'rules':[
        {'field':key,'op':'eq','data':value} for key,value in fields.items()
    ]}, ensure_ascii=False, separators=(',',':')))
    params = dict(fields, menuId='PS03520', searchInclude='All', searchKind='FOOD', filters=filters)
    return 'https://www.nics.go.kr/food/kfi/tfSrch08/list?' + urllib.parse.urlencode(params)

def index_page(page, collect):
    url = rda_list_url(page)
    text, digest = get(f'rda-all-index-{page}', url, collect)
    rows = []
    for match in re.finditer(r"<span class=['\"]f_name_em['\"]>(.*?)</span>\s*<span[^>]*>\s*(\d+)\s*</span>", text, re.S):
        name, code = plain(match.group(1)), match.group(2)
        rows.append({'recipeId':'RDA-'+code, 'name':name, 'sourceUrl':
                     'https://www.nics.go.kr/food/kfi/tfSrch08/view?menuId=PS03520&tfcode='+code,
                     'indexPage':page, 'indexSha256':digest, 'checkedAt':CHECKED, 'parserVersion':VERSION})
    if not rows: raise ValueError(f'No recipes parsed on RDA index page {page}')
    return rows

def rda_detail(row, collect):
    code=row['recipeId'].split('-')[1]
    text, digest = get('rda-'+code, row['sourceUrl'], collect)
    fields = {plain(m.group(1)):plain(m.group(2)) for m in re.finditer(
        r'<th[^>]*>(.*?)</th>\s*<td[^>]*>(.*?)</td>', text, re.S)}
    name = re.sub(r'\s+English$', '', fields.get('음식명','')).strip()
    assert canonical(name) == canonical(row['name']), (row['name'], name)
    assert fields.get('식재료') or fields.get('부재료'), row['recipeId']
    return {'recipeId':row['recipeId'], 'name':name, 'mainIngredientText':fields.get('식재료',''),
            'additionalIngredientText':fields.get('부재료',''), 'cookingText':fields.get('조리법',''),
            'methodText':fields.get('조리방법',''), 'sourceName':'농촌진흥청 농식품 올바로 전통향토음식',
            'sourceUrl':row['sourceUrl'], 'sourceReference':fields.get('출처',''),
            'sourceSha256':digest, 'checkedAt':CHECKED, 'parserVersion':VERSION}

def mfds_detail(number, collect):
    url='https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no='+str(number)
    text,digest=get('mfds-'+str(number),url,collect)
    match=re.search(r'<h1[^>]*>(.*?)</h1>',text,re.S)
    ingredients=re.search(r'<div class="ingredients">.*?<p>(.*?)</p>',text,re.S)
    methods=re.search(r'<div class="direction">.*?<pre>(.*?)</pre>',text,re.S)
    if not match or not ingredients: return None
    return {'recipeId':'MFDS-'+str(number), 'name':plain(match.group(1)),
            'mainIngredientText':plain(ingredients.group(1)), 'additionalIngredientText':'',
            'cookingText':'', 'methodText':plain(methods.group(1)) if methods else '',
            'sourceName':'식품의약품안전처 식품안전나라 공개 조리법', 'sourceUrl':url,
            'sourceReference':'공개 조리법 rcp_menu_no='+str(number),
            'sourceSha256':digest,'checkedAt':CHECKED,'parserVersion':VERSION}

def observed_cooking(row):
    text=row.get('methodText','');field=row.get('cookingText','')
    if '튀기' in field or re.search(r'튀김기름|기름에.{0,30}튀|튀겨|튀긴다|튀긴 후',text):return 'DEEP_FRIED'
    if '수증기로 찌' in field or re.search(r'찜통|찜기에|쪄낸|찐다',text):return 'STEAMED'
    if '굽는 음식' in field or re.search(r'오븐에|구워|굽는다',text):return 'GRILLED'
    if '끓이는 음식' in field or re.search(r'조린다|끓인다|끓여',text):return 'SIMMERED'
    if '기름을 이용' in field or re.search(r'볶는다|볶아|지진다|부친다',text):return 'PAN_COOKED'
    if '가열하지 않는' in field:return 'UNHEATED'
    return 'UNKNOWN'

def parallel(items, task, workers=4):
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool: return list(pool.map(task,items))

def collect(online):
    CACHE.mkdir(parents=True,exist_ok=True)
    # Source catalogue reports 3248 records. 47 finite pages; never brute-force IDs.
    index=[row for page in parallel(range(1,48),lambda p:index_page(p,online)) for row in page]
    unique={row['recipeId']:row for row in index}
    assert len(unique) == 3248, len(unique)
    write_csv(OUT/'official-public-recipe-index.csv', list(unique.values()), list(index[0]))
    templates=read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')
    reviewed=read_csv(OUT/'reviewed-recipe-identity-aliases.csv')
    assert all(r['stableTemplateId'] in {t['id'] for t in templates} for r in reviewed)
    for r in reviewed:assert unique[r['sourceRecipeId']]['name']==r['sourceRecipeName']
    names={canonical(t['name']) for t in templates}
    def aliases(name):
        return {canonical(n) for n in [recipe_name(name)] + re.split('[(),]',recipe_name(name)) if n.strip()}
    # A parenthetical synonym is accepted only when explicitly present in the source title.
    matched=[r for r in unique.values() if any(aliases(t['name']) & aliases(r['name']) for t in templates)]
    extra_ids={r['sourceRecipeId'] for r in reviewed}-{r['recipeId'] for r in matched}
    matched+=[unique[rid] for rid in sorted(extra_ids)]
    print(json.dumps({'catalogue':len(unique),'exactRecipeMatches':len(matched)},ensure_ascii=False),flush=True)
    details=parallel(matched,lambda r:rda_detail(r,online))
    # Existing finite MFDS catalogue capture contributes extra exact general-dish matches.
    details += [r for r in parallel(range(1,351),lambda n:mfds_detail(n,online)) if r]
    details.sort(key=lambda r:r['recipeId'])
    # Full method text is temporary parse evidence, not redistributed recipe instructions.
    (CACHE/'public-recipe-parsed.json').write_text(json.dumps(details,ensure_ascii=False,indent=2),encoding='utf-8')
    facts=[dict({k:v for k,v in row.items() if k!='methodText'}, observedCookingStyle=observed_cooking(row)) for row in details]
    write_csv(OUT/'official-public-recipe-facts.csv',facts,list(facts[0]))
    print(json.dumps({'recipeFacts':len(details),'exactTemplateCoverage':len({canonical(recipe_name(r['name'])) for r in details}&names)},ensure_ascii=False),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--collect',action='store_true')
    args=parser.parse_args();collect(args.collect)
