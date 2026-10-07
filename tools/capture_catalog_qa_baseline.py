"""Capture this request's immutable inputs and recalculate source coverage."""
from pathlib import Path
import csv, hashlib, json, shutil, subprocess, sys
from generate_franchise_brand_audit import parse_catalog
from generate_food_metadata import normalize

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets/fooddata'
BUILD=ROOT/'app/build/catalog-qa'
OUT=ROOT/'data-source/catalog-qa'

def read(path):
    with path.open(encoding='utf-8-sig',newline='') as stream:return list(csv.DictReader(stream))

def capture():
    target=OUT/'baseline.json'
    assert not target.exists(),'This request baseline already exists; do not recapture.'
    BUILD.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
    original=BUILD/'baseline';original.mkdir(exist_ok=False)
    for base in ('app/src/main','data-source/food-quality','data-source/recommendation','data-source/franchise','tools'):
        for path in (ROOT/base).rglob('*'):
            if path.is_file() and path.suffix in {'.kt','.xml','.csv','.json','.py','.properties','.md'} and '__pycache__' not in path.parts:
                dest=original/path.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
    foods=[r for name in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in read(ASSETS/name)]
    meta={r['foodItemId']:r for r in read(ASSETS/'food_metadata.csv')}
    catalog=parse_catalog((ROOT/'app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt').read_text(encoding='utf-8-sig'))
    brands={r['name'] for r in catalog}
    nutrition_ids={r['id'] for r in foods if r.get('brand') in brands and r['sourceType'] in ('K-FIND','OFFICIAL-BRAND-NUTRITION')}
    menus={}
    for name in ('official-menu-snapshot.csv','additional-menu-snapshot.csv','quality-menu-snapshot.csv','legacy-menu-snapshot.csv'):
        for row in read(ROOT/'data-source/franchise'/name):menus.setdefault((row['brand'],normalize(row['name'])),row.get('id') or row['menuId'])
    for b in catalog:
        for name in b['references']:menus.setdefault((b['name'],normalize(name)),f"official-menu-{normalize(b['name'])}-{normalize(name)}")
    ids=set(nutrition_ids)|set(menus.values())
    def known(rows):
        return dict(ingredientPartial=sum(r['ingredientStatus']!='COMPLETE_DECLARATION' and bool(r['ingredients']) for r in rows),
                    ingredientComplete=sum(r['ingredientStatus']=='COMPLETE_DECLARATION' for r in rows),
                    allergenEvidence=sum(r['allergenStatus']!='UNKNOWN' for r in rows),
                    foodGroups=sum(bool(r['foodGroups']) for r in rows))
    links=read(ASSETS/'meal_template_ingredients.csv');templates=read(ASSETS/'meal_templates.csv')
    rec={r['id']:[i['foodItemId'] for i in links if i['mealTemplateId']==r['id']] for r in templates}
    checked=subprocess.run([sys.executable,str(ROOT/'tools/check_food_data_quality.py')],capture_output=True,text=True,check=True,encoding='utf-8')
    quality=json.loads(checked.stdout)
    by_id={r['id']:r for r in foods};assert len(by_id)==len(foods)==31849
    report={'checkedAt':'2026-10-04','requestAttachment':'84097512-b718-4d0f-b5a3-849562f8b9a5',
        'foods':dict(total=len(foods),kcal=quality['kcalUsable'],macroComplete=quality['macroComplete'],
                     provenance=sum(bool(meta.get(r['id'],{}).get('sourceReference')) for r in foods),
                     **known([meta[r['id']] for r in foods])),
        'retail':{'total':len(read(ASSETS/'product_items.csv'))},
        'franchise':dict(brands=len(brands),menus=len(ids),kcal=len(nutrition_ids),
            nutritionMissing=len(ids)-len(nutrition_ids),menuOnly=len(menus),
            **known([meta[i] for i in ids])),
        'recommendation':dict(total=len(rec),ingredients=sum(any(meta[i]['ingredients'] for i in ii) for ii in rec.values()),
            foodGroups=sum(any(meta[i]['foodGroups'] for i in ii) for ii in rec.values()),
            officialAllergens=sum(all(meta[i]['allergenStatus']=='CONFIRMED_LABEL' for i in ii) for ii in rec.values())),
        'readOnlyFoodQualityAudit':quality,
        'assetHashes':{p.name:hashlib.sha256(p.read_bytes()).hexdigest().upper() for p in ASSETS.iterdir() if p.is_file()},
        'immutableInputDirectory':str(original.relative_to(ROOT)),
        'note':'Actual source coverage recalculated before edits. Runtime serving/category/style audits are captured separately from these same assets.'}
    assert report['foods']['ingredientPartial']==577 and report['foods']['ingredientComplete']==2
    assert report['franchise']['brands']==63 and report['franchise']['menus']==3979
    assert report['recommendation']['ingredients']==163 and report['recommendation']['foodGroups']==163
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':capture()
