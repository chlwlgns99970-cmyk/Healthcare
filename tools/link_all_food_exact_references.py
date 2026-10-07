"""Reuse one validated complete recipe only for exact qualified names and categories."""
import collections,csv,json
from pathlib import Path
from finish_recipe_reference_mapping import keys
ROOT=Path(__file__).resolve().parents[1];AS=ROOT/'app/src/main/assets/fooddata';OUT=ROOT/'data-source/all-food-detail-audit'
def rows(p):
    with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
def run():
    foods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)]
    fi={r['id']:r for r in foods};refs=rows(OUT/'baseline-official_recipe_reference_estimates.csv');groups=collections.defaultdict(list)
    for r in refs:groups[(r['foodId'],r['recipeId'])].append(r)
    index=collections.defaultdict(dict)
    for (fid,rid),composition in groups.items():
        if composition[0]['recipeComplete']!='true' or composition[0]['compositionKind']!='REFERENCE_RECIPE':continue
        for key in keys(composition[0]['recipeName']):index[(key,fi[fid]['category'])].setdefault(rid,composition)
    existing={(r['foodId'],r['recipeId']) for r in refs};links=[];added=[]
    for food in foods:
        if food['brand'] or food['sourceType'] not in ('K-FIND','RDA-MENUZEN'):continue
        candidates={rid:rs for key in keys(food['name']) for rid,rs in index.get((key,food['category']),{}).items() if (food['id'],rid) not in existing}
        if not candidates:continue
        # Deterministic institution/code order; one existing WHOLE recipe, never merged ingredients.
        rid,composition=sorted(candidates.items(),key=lambda x:(not x[0].startswith('MENUZEN-'),x[0]))[0]
        assert all(r['recipeComplete']=='true' and r['compositionKind']=='REFERENCE_RECIPE' for r in composition)
        for original in composition:
            row=dict(original,foodId=food['id'],foodReferenceKcal=food['energyKcal'],foodReferenceAmount=food['referenceAmount'],foodReferenceUnit=food['unit'])
            added.append(row)
        links.append(dict(foodId=food['id'],name=food['name'],category=food['category'],referenceId=rid,referenceName=composition[0]['recipeName'],source=composition[0]['sourceInstitution'],sourceUrl=composition[0]['recipeUrl'],sourceSha256=composition[0]['recipeSha256'],ingredientRows=len(composition),matchingMethod='EXACT_QUALIFIED_NAME_AND_CATEGORY_EXISTING_COMPLETE_RECIPE',checkedAt='2026-10-05',newFood=False))
    result=refs+added
    assert len({(r['foodId'],r['recipeId'],r['ingredientName']) for r in result})==len(result)
    with (AS/'official_recipe_reference_estimates.csv').open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(refs[0]));writer.writeheader();writer.writerows(result)
    (OUT/'new-exact-reference-links.json').write_text(json.dumps(links,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    target=ROOT/'app/src/androidTest/assets/all-food-detail-new-reference-links.json';target.parent.mkdir(exist_ok=True,parents=True)
    target.write_text(json.dumps(links,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(newReferenceFoodLinks=len(links),newRows=len(added),rows=len(result)),ensure_ascii=False))
if __name__=='__main__':run()
