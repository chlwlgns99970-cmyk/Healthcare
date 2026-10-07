"""This request's captured baseline, distinct from the preceding completed audit."""
from pathlib import Path
import csv, hashlib, json, shutil
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/food-quality'
BUILD=ROOT/'app/build/food-quality-followup'
def read(path):
    with path.open(encoding='utf-8-sig',newline='') as stream:return list(csv.DictReader(stream))
def load(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def main():
    before=load(OUT/'followup-baseline.json')
    linked=load(OUT/'linked-quality-summary.json')
    foods=load(OUT/'food-quality-audit.json')['after']
    runtime=load(BUILD/'final-serving-summary.json')
    meals=load(ROOT/'app/build/style-qa/audit-counts.json')
    after={'foods':foods,'foodAndMenuEvidence':load(OUT/'food-quality-audit.json')['allFoodAndMenuEvidence'],
        'franchise':linked['franchise']['combinedSourceEntries'],
        'recommendation':{k:linked['recommendation'][k] for k in ('templates','researched','someVerifiedIngredients','foodGroups','allergenEvidence','allergenCompleteLabel')},
        'runtimeServing':runtime,
        'franchiseMenu':load(ROOT/'data-source/franchise/expansion-audit-summary.json')['final'],
        'style':{'total':meals['eligible']['slowAgingStyle'],'meals':list(meals['slowStyleMeals'].values())}}
    # Regex legacy metrics describe source strings only. Executable policy counts are authoritative.
    foods['officialServing']=runtime['qualities']['OFFICIAL_SERVING']
    foods['householdServing']=runtime['household']
    foods['verifiedConversion']=runtime['qualities']['VERIFIED_CONVERSION']
    foods['ingredientPartial']=foods['ingredientsKnownPartialOrComplete']-foods['ingredientsCompleteDeclaration']
    foods['ingredientUnknown']=foods['totalFoods']-foods['ingredientsKnownPartialOrComplete']
    foods['allergenUnknown']=foods['totalFoods']-foods['allergenEvidenceKnown']
    foods['basisUnresolved']=runtime['qualities']['UNRESOLVED']
    metadata=read(ROOT/'app/src/main/assets/fooddata/food_metadata.csv')
    declarations=[r for r in metadata if r['ingredientStatus']=='COMPLETE_DECLARATION']
    assert len(declarations)==2 and all(r['completeIngredientText'] and r['productReportNumber'] for r in declarations)
    assert len({r['foodItemId'] for r in metadata})==33121
    assert after['franchiseMenu']['nutritionMissingMenuCount']==after['franchiseMenu']['totalMenuCount']-after['franchiseMenu']['kcalMenuCount']
    hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest().upper() for p in (ROOT/'app/src/main/assets/fooddata').glob('*.csv')}
    report={'checkedAt':'2026-10-04','baselineFile':'followup-baseline.json','before':before,'after':after,
        'sourceHashes':hashes,'completeIngredientFoodIds':[r['foodItemId'] for r in declarations],
        'policy':'Exact identities only. Full original labels, partial descriptions, reference recipes and cross-contact are separate. Unknown is never safe.',
        'auditOutputs':['final-food-identity-audit.csv','final-serving-audit.csv','final-recommendation-serving-audit.csv','final-actual-plans.csv'],
        'scopeNote':'Earlier generators retain the preceding task baseline. This report uses the 31,844-food start of this follow-up. Legacy template COMPLETE tags and category/name hints do not count as source-verified coverage.'}
    OUT.joinpath('followup-quality-summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    shutil.copyfile(ROOT/'app/build/data-quality-qa/food-identity-audit.csv',BUILD/'final-food-identity-audit.csv')
    print(json.dumps(after,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
