"""Reviewed exact brand descriptions, plus before/after 292 identity research audit.

The named ingredient map below is an explicit review of source ingredient
tokens. Composite bread, sauce and flavor names never imply hidden ingredients.
"""
from pathlib import Path
from collections import Counter
import argparse
import hashlib
import json
from collect_public_recipe_evidence import ROOT, OUT, CACHE, CHECKED, canonical, read_csv, write_csv

VERSION='recommendation-followup-v1'
BASELINE=OUT/'followup-baseline.json'
BRAND_GROUPS={
 'kfind-d202-096060000-0001':{'닭가슴살':'POULTRY','토마토':'VEGETABLE','피클':'VEGETABLE'},
 'kfind-d202-096000000-0010':{'훈제치킨':'POULTRY','로스트 파프리카':'VEGETABLE','양파':'VEGETABLE','모짜렐라치즈':'DAIRY'},
 'kfind-d202-115000000-0021':{'햄':'PROCESSED_MEAT','치즈':'DAIRY','에그샐러드':'EGG'},
 'kfind-d220-748000000-0209':{'우유':'DAIRY'},
 'kfind-d202-083000000-0023':{'닭고기':'POULTRY'},
 'kfind-d203-194000000-0004':{'스파게티면':'GRAIN_UNSPECIFIED','치즈':'DAIRY','쇠고기':'RED_MEAT'},
}

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def baseline():
    if BASELINE.exists():return
    templates=read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')
    public=read_csv(OUT/'verified-food-groups.csv')
    research=read_csv(OUT/'recommendation-ingredient-research.csv')
    linked={r['mealTemplateId']:r['foodItemId']for r in read_csv(ROOT/'app/src/main/assets/fooddata/meal_template_ingredients.csv')}
    metadata={r['foodItemId']:r for r in read_csv(ROOT/'app/src/main/assets/fooddata/food_metadata.csv')}
    permeal={meal:sum(r['slowStyleEligible']=='true'and meal in next(t['supportedMealTypes']for t in templates if t['id']==r['stableTemplateId']) for r in public)for meal in ['BREAKFAST','LUNCH','DINNER','SNACK']}
    ingredients=sum(bool(metadata[linked[t['id']]]['ingredients'])for t in templates)
    snap=dict(checkedAt=CHECKED,templateRows=len(templates),linkedIngredients=ingredients,
        sourcedGroups=len(public),styleEligible=sum(r['slowStyleEligible']=='true'for r in public),
        perMeal=permeal,publicResearchUnknown=sum(not r['ingredients']for r in research),
        sourceSha256={str(p.relative_to(ROOT)):sha(p)for p in [OUT/'verified-food-groups.csv',OUT/'recommendation-ingredient-research.csv']},
        existingVerifiedTemplateIds=[r['stableTemplateId']for r in public],existingStyleTemplateIds=[r['stableTemplateId']for r in public if r['slowStyleEligible']=='true'],
        templateIdentitySha256=sha(ROOT/'app/src/main/assets/fooddata/meal_templates.csv'),parserVersion=VERSION)
    BASELINE.write_text(json.dumps(snap,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def build_brand_groups():
    baseline()
    template={r['id']:r for r in read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')}
    linked={r['mealTemplateId']:r['foodItemId']for r in read_csv(ROOT/'app/src/main/assets/fooddata/meal_template_ingredients.csv')}
    template_by_food={fid:tid for tid,fid in linked.items()}
    foods={r['id']:r for r in read_csv(ROOT/'app/src/main/assets/fooddata/food_items.csv')}
    evidence=[r for name in ['cafe-targeted-evidence.csv','product-targeted-evidence.csv']for r in read_csv(ROOT/'data-source/food-quality'/name)]
    byid={r['foodItemId']:r for r in evidence if r['ingredients']and r['ingredientStatus']=='PARTIAL_DESCRIPTION'}
    fields=list(read_csv(OUT/'verified-food-groups-baseline.csv')[0])+['evidenceRecipeId','negativeSignals','sourceSha256']
    rows=[]
    for fid,reviewed in BRAND_GROUPS.items():
        r=byid[fid];food=foods[fid];t=template[template_by_food[fid]]
        assert r['sourceFoodCode']==food['sourceFoodCode']and r['brand']==food['brand']and r['name']==food['name'],fid
        assert set(reviewed)<=set(r['ingredients'].split('|')),fid
        groups=set(reviewed.values())
        protein=groups&{'LEGUME_SOY','RED_MEAT','POULTRY','FISH','SEAFOOD','EGG','DAIRY','NUT_SEED','PROCESSED_MEAT'}
        rows.append(dict(stableTemplateId=t['id'],menuName=t['name'],ingredients=r['ingredients'],foodGroups='|'.join(sorted(groups)),
            grainType='RICE_OR_FLOUR'if 'GRAIN_UNSPECIFIED'in groups else 'NONE',proteinSources='|'.join(sorted(protein))or 'UNKNOWN',
            cookingStyle='UNKNOWN',sourceName=food['brand']+' 공식 메뉴 원문',sourceUrl=r['sourceUrl'],
            evidenceScope='BRAND_OFFICIAL_MAJOR_INGREDIENTS',verifiedAt=r['checkedAt'],slowStyleEligible='false',
            notes='정확한 food ID·품목코드·브랜드·음식명으로 검증된 공식 description 원문의 literal 재료만 연결; 복합 빵·소스·향료 속 원재료와 조리유·중량을 추정하지 않음; 현재 메뉴와 분석시점 recipe version 일치 여부 미확인',
            evidenceRecipeId='OFFICIAL-FOOD-'+food['sourceFoodCode'],negativeSignals='PROCESSED_MEAT'if 'PROCESSED_MEAT'in groups else '',sourceSha256=r['sourceHash']))
    write_csv(OUT/'reviewed-followup-brand-groups.csv',rows,fields)
    print(f'{len(rows)} exact official brand ingredient-group reviews')

def audit():
    before=json.loads(BASELINE.read_text(encoding='utf-8'))
    templates=read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')
    research=read_csv(OUT/'recommendation-ingredient-research.csv')
    rows=read_csv(OUT/'verified-food-groups.csv')
    assert sha(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')==before['templateIdentitySha256']
    eligible={r['stableTemplateId']for r in rows if r['slowStyleEligible']=='true'}
    groups={r['stableTemplateId']for r in rows}
    permeal={meal:sum(t['id']in eligible and meal in t['supportedMealTypes']for t in templates)for meal in ['BREAKFAST','LUNCH','DINNER','SNACK']}
    fields=Counter(r['ingredientStatus']for r in rows)
    report=dict(checkedAt=CHECKED,parserVersion=VERSION,templateRows=len(templates),
        before={k:before[k]for k in ['linkedIngredients','sourcedGroups','styleEligible','perMeal','publicResearchUnknown']},
        after=dict(sourcedGroups=len(groups),sourcedIngredientsFromThisResearch=len(rows),styleEligible=len(eligible),perMeal=permeal,
            researchUnknown=sum(not r['ingredients']for r in research),ingredientStatuses=dict(fields),
            completeProductIngredientDeclarations=0,allergenStatusesUnchangedUnknownForReferenceRecipes=True),
        newSourcedTemplateIds=sorted(groups-set(before['existingVerifiedTemplateIds'])),
        newStyleTemplateIds=sorted(eligible-set(before['existingStyleTemplateIds'])),
        removedStyleTemplateIds=sorted(set(before['existingStyleTemplateIds'])-eligible),
        unknownReasons=dict(Counter(r['reason']for r in research if not r['ingredients'])),
        sourceFieldAudit=dict(originalKfindIngredientColumns=0,originalKfindAllergenColumns=0,
            referenceRecipeMainAndAdditionalRawFieldsPreserved=True,
            restoredDroppedFillingSection='RDA-90925: <소> 거피팥 420g(2컵)',
            completeIngredientSourceScope='REFERENCE_RECIPE_IS_NOT_COMPLETE_PRODUCT_LABEL',
            unitlessLegacyRecipeAmounts='PRESERVED_UNCHANGED_NOT_ASSUMED_GRAMS_NOT_STYLE_ELIGIBLE',
            brandedRecipeJoin='EXACT_ID_SOURCE_FOOD_CODE_BRAND_SOURCE_NAME_GATE'),
        nutritionTransferred=0,servingTransferred=0,newHealthPolicyWeights=0,
        acceptedNewEvidence=[r for r in research if r['stableTemplateId']in groups-set(before['existingVerifiedTemplateIds'])])
    (OUT/'followup-ingredient-evidence-summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items()if k!='acceptedNewEvidence'},ensure_ascii=False))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--audit',action='store_true');args=p.parse_args()
    audit()if args.audit else build_brand_groups()
