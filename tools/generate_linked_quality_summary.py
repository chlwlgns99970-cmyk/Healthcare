"""Summarize the same exact identity links used by the runtime, without name joins."""
import json
from pathlib import Path
from generate_food_metadata import read, normalize, build
from generate_franchise_brand_audit import parse_catalog

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/fooddata'
OUT = ROOT / 'data-source/food-quality'

def main():
    metadata = {row['foodItemId']: row for row in read(ASSETS / 'food_metadata.csv')}
    foods = sum((read(ASSETS / name) for name in ('food_items.csv','product_items.csv','franchise_official_items.csv')), [])
    catalog = parse_catalog((ROOT / 'app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt').read_text(encoding='utf-8-sig'))
    brands = {row['name'] for row in catalog}
    franchise_food_ids = {row['id'] for row in foods if row.get('brand') in brands and row['sourceType'] in ('K-FIND','OFFICIAL-BRAND-NUTRITION')}
    menus = sum((read(ROOT / 'data-source/franchise' / name) for name in
                 ('official-menu-snapshot.csv','additional-menu-snapshot.csv','quality-menu-snapshot.csv','legacy-menu-snapshot.csv')), [])
    menu_by_name = {}
    for row in sorted(menus, key=lambda row: row.get('id') or row.get('menuId','')):
        menu_by_name.setdefault((row['brand'],normalize(row['name'])), row.get('id') or row['menuId'])
    for brand in catalog:
        for name in brand['references']:
            menu_by_name.setdefault((brand['name'],normalize(name)), f"official-menu-{normalize(brand['name'])}-{normalize(name)}")
    def counts(ids):
        rows = [metadata[identity] for identity in ids if identity in metadata]
        return dict(identities=len(ids), linkedMetadata=len(rows),
            ingredientKnown=sum(bool(row['ingredients']) for row in rows),
            ingredientComplete=sum(row['ingredientStatus']=='COMPLETE_DECLARATION' for row in rows),
            allergenEvidence=sum(row['allergenStatus']!='UNKNOWN' for row in rows),
            allergenCompleteLabel=sum(row['allergenStatus']=='CONFIRMED_LABEL' for row in rows),
            foodGroups=sum(bool(row['foodGroups']) for row in rows),
            staleCandidates=sum(row['staleCandidate']=='true' for row in rows))
    links = read(ASSETS / 'meal_template_ingredients.csv')
    template_ids = {row['id'] for row in read(ASSETS / 'meal_templates.csv')}
    by_template = {identity: [row['foodItemId'] for row in links if row['mealTemplateId']==identity] for identity in template_ids}
    research = read(ROOT / 'data-source/recommendation/recommendation-ingredient-research.csv')
    recipe_summary = json.loads((ROOT / 'data-source/recommendation/ingredient-evidence-summary.json').read_text(encoding='utf-8'))
    recommendations = dict(templates=len(template_ids), researched=len(research),
        someVerifiedIngredients=sum(any(metadata.get(food,{}).get('ingredients') for food in ids) for ids in by_template.values()),
        foodGroups=sum(any(metadata.get(food,{}).get('foodGroups') for food in ids) for ids in by_template.values()),
        allergenEvidence=sum(any(metadata.get(food,{}).get('allergenStatus','UNKNOWN')!='UNKNOWN' for food in ids) for ids in by_template.values()),
        allergenCompleteLabel=sum(bool(ids) and all(metadata.get(food,{}).get('allergenStatus')=='CONFIRMED_LABEL' for food in ids) for ids in by_template.values()),
        researchSummary=recipe_summary,
        beforeAllergenPolicy={'legacyCompleteFlag':37,'explicitOfficialDeclarations':2,
            'note':'Positive tags alone no longer certify a full declaration. Every exact linked food needs a confirmed label.'})
    baseline_dir = ROOT / 'app/build/data-quality-qa'
    baseline_summary_path = OUT / 'quality-baseline.json'
    if baseline_summary_path.exists():
        baseline = json.loads(baseline_summary_path.read_text(encoding='utf-8'))
    else:
        baseline_foods = sum((read(baseline_dir / ('before-'+name)) for name in
                             ('food_items.csv','product_items.csv','franchise_official_items.csv')), [])
        assert len(baseline_foods) == 31680, 'Captured task baseline is required on first generation'
        baseline_rows, conflicts = build(baseline_foods,read(OUT/'raw-identity-fields.csv'),[],
            read(baseline_dir/'before-verified-food-groups.csv'),
            read(ROOT/'data-source/recommendation/official-ingredient-verification.csv'),
            read(baseline_dir/'before-meal_template_ingredients.csv'),[])
        assert not conflicts
        baseline_metadata = {row['foodItemId']:row for row in baseline_rows}
        old_franchise = {row['id'] for row in baseline_foods if row.get('brand') in brands
                         and row['sourceType'] in ('K-FIND','OFFICIAL-BRAND-NUTRITION')}
        baseline = dict(checkedAt='2026-10-04', foodMetrics=json.loads((OUT/'food-quality-audit.json').read_text(encoding='utf-8'))['before'],
            recommendation=dict(someVerifiedIngredients=sum(any(baseline_metadata.get(food,{}).get('ingredients') for food in ids) for ids in by_template.values()),
                foodGroups=sum(any(baseline_metadata.get(food,{}).get('foodGroups') for food in ids) for ids in by_template.values()),
                allergenEvidence=sum(any(baseline_metadata.get(food,{}).get('allergenStatus','UNKNOWN')!='UNKNOWN' for food in ids) for ids in by_template.values()),
                allergenCompleteLabel=sum(bool(ids) and all(baseline_metadata.get(food,{}).get('allergenStatus')=='CONFIRMED_LABEL' for food in ids) for ids in by_template.values())),
            franchise=dict(ingredientKnown=sum(bool(baseline_metadata[identity]['ingredients']) for identity in old_franchise),
                allergenEvidence=sum(baseline_metadata[identity]['allergenStatus']!='UNKNOWN' for identity in old_franchise)),
            scope='Captured start-of-task assets; source-verified components are distinct from historic name-only COMPLETE flags.')
        baseline_summary_path.write_text(json.dumps(baseline,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    output = dict(checkedAt='2026-10-04', wholeFoodAudit='food-quality-audit.json', baseline=baseline,
        franchise=dict(nutritionIdentities=counts(franchise_food_ids),
            catalogMenus=counts(set(menu_by_name.values())),
            combinedSourceEntries=counts(franchise_food_ids | set(menu_by_name.values()))),
        recommendation=recommendations,
        runtimeAmountPolicy=json.loads((baseline_dir/'runtime-amount-audit.json').read_text(encoding='utf-8')) if (baseline_dir/'runtime-amount-audit.json').exists() else {},
        policy='Ingredients describe observed components or public reference recipes, separately from complete ingredient declarations. Positive allergens, complete labels and cross-contact remain separate.')
    OUT.mkdir(exist_ok=True)
    (OUT / 'linked-quality-summary.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(output,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
