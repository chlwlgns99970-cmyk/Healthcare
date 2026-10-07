"""Audit all existing Food names and metadata without authorizing alias matches."""
import csv,json,re
from pathlib import Path
from finish_recipe_reference_mapping import keys,original_keys
from recipe_composition_validation import identity_key

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-residual-89'

def run():
    foods=[]
    for filename in ('food_items.csv','product_items.csv','franchise_official_items.csv'):
        with (ROOT/'app/src/main/assets/fooddata'/filename).open(encoding='utf-8-sig',newline='') as stream:
            foods.extend(csv.DictReader(stream))
    assert len(foods)==67354
    queue=json.loads((OUT/'scope-89.json').read_text(encoding='utf-8'))
    six=json.loads((OUT/'question-required-six-baseline.json').read_text(encoding='utf-8'))
    output=[]
    for item in queue+six:
        search=set(original_keys(item['name']))
        search.update(item.get('retrievalKeys',[]))
        search={identity_key(k).replace('수프','스프') for k in search}
        candidates=[]
        for f in foods:
            names={identity_key(k).replace('수프','스프') for k in keys(f['name'])}
            normalized=identity_key(f['normalizedName']).replace('수프','스프')
            aliases={identity_key(a).replace('수프','스프') for a in f['aliases'].split('|') if a}
            match=search & names
            alias_match=search & (aliases | {normalized})
            if match or alias_match:
                candidates.append(dict(foodId=f['id'],name=f['name'],category=f['category'],
                    source=f['sourceType'],brand=f['brand'],referenceAmount=f['referenceAmount'],
                    unit=f['unit'],officialEnergyKcal=f['energyKcal'],aliases=f['aliases'],
                    matchedKeys=sorted(match|alias_match),
                    matchType='QUALIFIED_NAME' if match else 'METADATA_ALIAS_REQUIRES_WHOLE_IDENTITY_REVIEW',
                    publicationAuthorized=False))
        output.append(dict(recipeId=item['recipeId'],name=item['name'],foodsScanned=len(foods),
            candidates=candidates,newFoodCreated=False,
            status='REVIEW_REQUIRED' if candidates else 'NO_EXACT_EXISTING_FOOD',
            questionRequired=item in six))
    (OUT/'all-food-metadata-reindex.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(foodsScanned=len(foods),dishes=len(output),automaticPublications=0)))

if __name__=='__main__':run()
