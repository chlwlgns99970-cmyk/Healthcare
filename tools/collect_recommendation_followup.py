"""Finite public RDA sample-page collection; exact dish titles only.

These are public HTML sample screens, not authenticated API requests. The five
published category catalogues define the complete, bounded request scope.
Nutrition, recipe health claims and recipe amounts never become FoodItem values.
"""
import argparse
import concurrent.futures
import hashlib
import json
import re
import urllib.request
from collect_public_recipe_evidence import ROOT, OUT, CACHE, CHECKED, plain, canonical, read_csv, write_csv, observed_cooking

VERSION = 'rda-public-diet-followup-v1'
BASE = 'https://api.nongsaro.go.kr/sample/rest/recomendDiet/recomendDiet.jsp'
BOUNDS = {'254001':11, '254002':10, '254003':5, '254004':3, '254005':1}
MANIFEST = OUT/'followup-public-diet-source-audit.json'
requests = []

def cached(key, url, online):
    path=CACHE/(key+'.html')
    if not path.exists():
        if not online:raise FileNotFoundError(path)
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}), timeout=20) as response:
            raw=response.read()
        path.write_bytes(raw)
    raw=path.read_bytes()
    requests.append(dict(cacheKey=key,sourceUrl=url,sha256=hashlib.sha256(raw).hexdigest(),bytes=len(raw),checkedAt=CHECKED))
    return raw.decode('utf-8','strict'),hashlib.sha256(raw).hexdigest()

def index(code,page,online):
    url=f'{BASE}?dietSeCode={code}&pageNo={page}'
    text,digest=cached(f'nongsaro-diet-index-{code}-{page}',url,online)
    if page==1:
        published={int(v)for v in re.findall(r'fncGoPage\((\d+)\)',text)}
        assert max(published or {1})==BOUNDS[code],(code,published)
    return [dict(dietId=rid,menuNames=plain(name),sourceUrl=f'{BASE}?cntntsNo={rid}',indexUrl=url,indexSha256=digest)
        for rid,name in re.findall(r'<a href="javascript:detailMove\(\'(\d+)\'\);">(.*?)</a>',text,re.S)]

def tabs(row,online):
    url=row['sourceUrl']+'&tabNo=0'
    text,digest=cached(f'nongsaro-diet-{row["dietId"]}-0',url,online)
    return [dict(dietId=row['dietId'],tabNo=tab,name=plain(name),sourceUrl=row['sourceUrl']+'&tabNo='+tab)
        for tab,name in re.findall(r'<a href="javascript:tabMove\(\'(\d+)\'\);">(.*?)</a>',text,re.S)]

def detail(row,online):
    text,digest=cached(f'nongsaro-diet-{row["dietId"]}-{row["tabNo"]}',row['sourceUrl'],online)
    sections=re.findall(r'<strong>식단구성(?:&nbsp;|\s)*</strong>(.*?)<br\s*/?>',text,re.S)
    methods=re.findall(r'<strong>식단소개(?:&nbsp;|\s)*</strong>(.*?)<br\s*/?>',text,re.S)
    assert len(sections)==2,(row,sections)
    assert canonical(plain(sections[0]))==canonical(row['name']),row
    assert plain(sections[1]),row
    record=dict(recipeId=f'RDA-DIET-{row["dietId"]}-{row["tabNo"]}',name=row['name'],
        mainIngredientText=plain(sections[1]),additionalIngredientText='',cookingText='',
        methodText=plain(methods[1]) if len(methods)>1 else '',sourceName='농촌진흥청 공개 추천식단 개별 조리자료',
        sourceUrl=row['sourceUrl'],sourceReference=f'공개 추천식단 {row["dietId"]} 메뉴 {row["tabNo"]}',
        sourceSha256=digest,checkedAt=CHECKED,parserVersion=VERSION)
    record['observedCookingStyle']=observed_cooking(record)
    return record

def parallel(items,fn):
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:return list(pool.map(fn,items))

def collect(online):
    CACHE.mkdir(parents=True,exist_ok=True)
    templates=read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')
    foods={r['id']:r for r in read_csv(ROOT/'app/src/main/assets/fooddata/food_items.csv')}
    linked={r['mealTemplateId']:r['foodItemId'] for r in read_csv(ROOT/'app/src/main/assets/fooddata/meal_template_ingredients.csv')}
    names={canonical(t['name']) for t in templates if not foods[linked[t['id']]]['brand']}
    pages=[(code,page) for code,last in BOUNDS.items() for page in range(1,last+1)]
    rows=[row for group in parallel(pages,lambda p:index(*p,online)) for row in group]
    byid={r['dietId']:r for r in rows}
    selected=[r for r in byid.values() if any(canonical(n)in names for n in r['menuNames'].split(','))]
    print(json.dumps(dict(indexPages=len(pages),diets=len(byid),exactSelectedDiets=len(selected)),ensure_ascii=False),flush=True)
    all_tabs=[r for rs in parallel(selected,lambda r:tabs(r,online))for r in rs]
    selected_tabs={ (r['dietId'],r['tabNo']):r for r in all_tabs if canonical(r['name'])in names }
    facts=parallel(selected_tabs.values(),lambda r:detail(r,online))
    facts.sort(key=lambda r:r['recipeId'])
    write_csv(OUT/'official-followup-public-recipe-index.csv',sorted(byid.values(),key=lambda r:r['dietId']),list(rows[0]))
    (CACHE/'followup-public-diet-parsed.json').write_text(json.dumps(facts,ensure_ascii=False,indent=2),encoding='utf-8')
    factual=[{k:v for k,v in row.items() if k!='methodText'}for row in facts]
    if factual:write_csv(OUT/'official-followup-public-recipe-facts.csv',factual,list(factual[0]))
    unique={r['cacheKey']:r for r in requests}
    report=dict(checkedAt=CHECKED,parserVersion=VERSION,publishedFinitePageBounds=BOUNDS,
        indexPages=len(pages),indexedDiets=len(byid),selectedExactDiets=len(selected),
        selectedExactDishRecipes=len(facts),distinctExactDishNames=len({canonical(r['name'])for r in facts}),
        authenticatedApiUsed=False,apiKeyUsed=False,nutritionOrServingImported=False,
        sourceIngredientStatus='PUBLIC_REFERENCE_RECIPE_NOT_PRODUCT_DECLARATION',
        requests=sorted(unique.values(),key=lambda r:r['cacheKey']))
    MANIFEST.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items()if k!='requests'},ensure_ascii=False),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--collect',action='store_true')
    args=parser.parse_args();collect(args.collect)
