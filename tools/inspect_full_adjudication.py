from pathlib import Path
import csv,json,collections,re,sys
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/full-adjudication';OUT.mkdir(exist_ok=True)
def read(p):
    with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
if sys.argv[1]=='baseline':
    for source,name in [('data-source/recipe-calories/audit.json','recipe-before.json'),('app/build/catalog-qa/final-franchise-menu-category-audit.csv','menu-before.csv'),('app/src/main/assets/fooddata/recipe_ingredient_estimates.csv','references-before.csv')]:
        target=OUT/name
        if not target.exists():target.write_bytes((ROOT/source).read_bytes())
    a=json.loads((OUT/'recipe-before.json').read_text(encoding='utf-8'))
    terms=collections.Counter((i['ingredient'],i['reason']) for r in a['audit'] for i in r['ingredients'])
    print('unique fragments',len(terms))
    (OUT/'original-parser-fragments.json').write_text(json.dumps([dict(fragment=k[0],status=k[1],count=v) for k,v in sorted(terms.items(),key=lambda x:(x[0][1],-x[1]))],ensure_ascii=False,indent=2),encoding='utf-8')
    recipes={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv') for r in read(p)}
    print('kimbap',[(r['recipeId'],r['name'],r['mainIngredientText'],r['additionalIngredientText']) for r in recipes.values() if '김밥' in r['name']])
elif sys.argv[1]=='fragments':
    rows=json.loads((OUT/'original-parser-fragments.json').read_text(encoding='utf-8'))
    for r in rows:
        if r['status']!='LINKED' and r['count']>=3:print(r['count'],r['status'],r['fragment'])
elif sys.argv[1]=='menus':
    m={r['foodItemId']:r for r in read(ROOT/'app/src/main/assets/fooddata/food_metadata.csv')}
    for r in read(OUT/'menu-before.csv'):
        if not r['menuCategory']:
            metadata=m.get(r['menuId'],{})
            print(r['menuId'],r['brand'],r['name'],'::',metadata.get('ingredientText',''),'::',metadata.get('sourceReference',''))
