"""Reproduce reviewed ingredient facts from exact KTO page identities, with source digests."""
from collect_public_recipe_evidence import *

def generate():
    specs=json.loads((OUT/'reviewed-kto-ingredient-facts.json').read_text(encoding='utf-8'))
    parsed=json.loads((CACHE/'kto-food-parsed.json').read_text(encoding='utf-8'))
    templates={r['id']:r for r in read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')}
    rows=[]
    for page in parsed:
        spec=specs.get(page['sourceId'])
        if not spec:continue
        assert page['description'] and spec['ingredients'] and spec['groups']
        for tid in page['templateIds']:
            groups=spec['groups'].split('|')
            proteins=set(groups)&{'LEGUME_SOY','RED_MEAT','POULTRY','FISH','SEAFOOD','EGG','DAIRY','NUT_SEED','PROCESSED_MEAT','MEAT_UNSPECIFIED'}
            rows.append(dict(stableTemplateId=tid,menuName=templates[tid]['name'],ingredients=spec['ingredients'],
                foodGroups=spec['groups'],grainType='MIXED_GRAINS' if 'MIXED_GRAIN'in groups else 'RICE_OR_FLOUR' if 'GRAIN_UNSPECIFIED'in groups or 'REFINED_GRAIN'in groups else 'NONE',
                proteinSources='|'.join(sorted(proteins)) or 'UNKNOWN',cookingStyle=spec['cook'],
                sourceName='한국관광공사 공식 음식 소개',sourceUrl=page['sourceUrl'],evidenceScope='PUBLIC_DISH_DEFINITION',
                verifiedAt=page['checkedAt'],slowStyleEligible='false',notes=spec.get('notes','본문에 명시된 주요 구성만 연결; 실제 조리법별 전체 원재료·알레르기·영양·제공량은 확정하지 않음'),
                evidenceRecipeId='KTO-'+page['sourceId'],negativeSignals=spec.get('negative',''),sourceSha256=page['sourceSha256']))
    write_csv(OUT/'reviewed-public-definition-ingredients.csv',rows,list(rows[0]))
    print('Reviewed KTO definitions:',len(rows))

if __name__=='__main__':generate()
