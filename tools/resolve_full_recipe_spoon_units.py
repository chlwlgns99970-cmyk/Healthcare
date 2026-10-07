"""Residual-only standard-spoon conversion for the already verified sugar identity."""
import copy
import hashlib
import json
from pathlib import Path
from build_full_recipe_references import ROOT,OUT,load,save
from maximize_recipe_evidence import archive_tables,archive_digest,KOREAN_MEASURE_URL,US_MEASURE_URL
from relink_recipe_strategy import bulk_index,legacy_index
from maximize_recipe_evidence import additional_nutrients
from publish_adjudicated_recipe_references import publish

def run():
    decisions=copy.deepcopy(load(OUT/'baseline-ingredient-decisions.json'))
    _,_,portions=archive_tables()
    sugar=[p for p in portions if p['fdc_id']=='169655' and p['modifier']=='tsp']
    assert len(sugar)==1 and float(sugar[0]['gram_weight'])==4.2 and float(sugar[0]['amount'])==1
    kr=ROOT/'data-source/recipe-linkage-maximization/raw/3bfa0f23eca44949.html'
    us=ROOT/'data-source/recipe-linkage-maximization/raw/e05608a3ed0d24fd.pdf'
    assert hashlib.sha256(kr.read_bytes()).hexdigest()=='4f566a776dfa94944ea0d59246009fce30f56c21aa6afac80e7a93bca7d7e907'
    assert hashlib.sha256(us.read_bytes()).hexdigest()=='804c084c2916b349dadbbbda3cd6788c69ff92a5bea8e0152f2192cb790bbec2'
    assert 'T = 큰술 = 스푼 = 15mL' in kr.read_text(encoding='utf-8') and 't = 작은술 = 5mL' in kr.read_text(encoding='utf-8')
    conversions=[]
    for d in decisions:
        if not (d['status']=='UNIT_CONVERSION_UNVERIFIED' and d['identityStatus']=='LINKED' and
            d['ingredient']=='설탕' and d['nutrientId']=='169655' and d['unit']=='큰술' and
            d['quantity'] and d['quantity']>0 and not d['quantityRange']): continue
        assert d['amountGrams'] is None
        provenance=dict(sourceUrl='https://fdc.nal.usda.gov/food-details/169655/nutrients',
            archiveSha256=archive_digest(),portionRows=sugar,gramsPerUnit=12.6,
            formula='4.2g / 5mL * 15mL * original tablespoon quantity',
            measureDefinitionSources=[KOREAN_MEASURE_URL,US_MEASURE_URL],
            measureDefinitionHashes=[hashlib.sha256(kr.read_bytes()).hexdigest(),hashlib.sha256(us.read_bytes()).hexdigest()],
            checkedAt='2026-10-05',reason='검증된 granulated sugar identity와 표준 계량스푼 5/15mL 비율의 공식 참고 환산. 책자의 서로 다른 반올림/채움 조건을 평균내지 않음')
        d.update(amountGrams=d['quantity']*12.6,status='LINKED',unitStatus='OFFICIAL_STANDARD_SPOON_RATIO',
            unitReason=provenance['reason'],conversionProvenance=provenance,amountEvidenceStatus='ORIGINAL_OFFICIAL_CONVERSION')
        conversions.append(dict(recipeId=d['recipeId'],ingredientIndex=d['ingredientIndex'],
            originalSpan=d['originalSpan'],amountGrams=d['amountGrams'],provenance=provenance))
    assert len(conversions)==25
    nutrients={r['code']:r for r in load(ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json')}
    nutrients.update(additional_nutrients());nutrients.update(bulk_index());nutrients.update(legacy_index())
    selected={r['recipeId'] for r in load(OUT/'baseline-selected-complete-references.json')}
    publish(decisions,nutrients,OUT,selected)
    save('ingredient-decisions.json',decisions)
    save('original-spoon-conversions.json',conversions)
    reviews=load(OUT/'portion-reviews.json')
    bykey={(r['recipeId'],r['ingredientIndex']):r for r in conversions}
    for r in reviews:
        conversion=bykey.get((r['recipeId'],r['ingredientIndex']))
        if conversion:
            r.update(preferredGramsPerUnit=12.6,reviewResult='OFFICIAL_STANDARD_SPOON_RATIO_VERIFIED',
                reviewReason=conversion['provenance']['reason'],preferredSource=conversion['provenance'])
    save('portion-reviews.json',reviews)
    print('Residual original sugar rows resolved:',len(conversions))

if __name__=='__main__':run()
