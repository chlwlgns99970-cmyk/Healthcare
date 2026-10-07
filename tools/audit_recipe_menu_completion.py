"""Request baseline and every formerly unknown menu, using Kotlin runtime output."""
from pathlib import Path
import csv,json,hashlib,sys
from collections import Counter
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-menu-completion';OUT.mkdir(exist_ok=True)
def read(p):
    with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
runtime=ROOT/'app/build/catalog-qa/final-franchise-menu-category-audit.csv'
if sys.argv[1]=='baseline':
    assert not (OUT/'menu-before.csv').exists()
    (OUT/'menu-before.csv').write_bytes(runtime.read_bytes())
    hashes={name:hashlib.sha256((ROOT/'app/src/main/assets/fooddata'/name).read_bytes()).hexdigest() for name in ('food_items.csv','product_items.csv','franchise_official_items.csv')}
    (OUT/'immutable-food-hashes.json').write_text(json.dumps(hashes,indent=2),encoding='utf-8')
    print('unknown baseline',sum(not r['menuCategory'] for r in read(runtime)))
else:
    before=read(OUT/'menu-before.csv');after=read(runtime);byid={r['menuId']:r for r in after}
    metadata={r['foodItemId']:r for r in read(ROOT/'app/src/main/assets/fooddata/food_metadata.csv')}
    reviews={r['menuId']:r for r in read(OUT/'reviewed-menu-categories.csv')} if (OUT/'reviewed-menu-categories.csv').exists() else {}
    rows=[]
    for old in before:
        if old['menuCategory']:continue
        current=byid[old['menuId']];name=current['name'];m=metadata.get(old['menuId'],{})
        reason='AMBIGUOUS_NAME_OR_UNSUPPORTED_DISH_KIND'
        if current['menuCategory']:reason='OFFICIAL_DESCRIPTION_REVIEW' if old['menuId'] in reviews else 'EXPLICIT_INDIVIDUAL_DISH_NAME'
        elif any(t in name for t in ('세트','팩','콤보','모둠')):reason='MIXED_SET_NOT_SINGLE_DISH'
        elif any(t in name for t in ('수프','스프')):reason='NO_EXISTING_SOUP_MENU_CATEGORY'
        elif any(t in name for t in ('추가','토핑','사리','g','EA','조각')):reason='INGREDIENT_TOPPING_OR_PORTION_IDENTITY'
        review=reviews.get(old['menuId'],{})
        rows.append(dict(menuId=old['menuId'],brand=old['brand'],name=name,beforeCategory='',candidateCategory=current['menuCategory'],
            evidence=review.get('evidence') or m.get('ingredientText') or name,
            source=review.get('sourceUrl') or m.get('sourceReference',''),classifiable=bool(current['menuCategory']),reason=reason))
    with (OUT/'all-unknown-menu-audit.csv').open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
    summary=dict(beforeUnknown=len(rows),automaticResolved=sum(r['classifiable'] and r['reason']=='EXPLICIT_INDIVIDUAL_DISH_NAME' for r in rows),
        officialDescriptionResolved=sum(r['classifiable'] and r['reason']=='OFFICIAL_DESCRIPTION_REVIEW' for r in rows),
        previousUnknownRemaining=sum(not r['classifiable'] for r in rows),newUnknown=sum(not r['menuCategory'] and r['menuId'] not in {r['menuId'] for r in before} for r in after),
        afterUnknown=sum(not r['menuCategory'] for r in after),remainingReasons=dict(Counter(r['reason'] for r in rows if not r['classifiable'])))
    for name,digest in json.loads((OUT/'immutable-food-hashes.json').read_text()).items():assert hashlib.sha256((ROOT/'app/src/main/assets/fooddata'/name).read_bytes()).hexdigest()==digest
    summary['allExistingFoodAssetBytesUnchanged']=True
    (OUT/'menu-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(summary,ensure_ascii=False))
