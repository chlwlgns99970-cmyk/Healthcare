"""Reviewed whole compositions from newly captured primary documents."""
import hashlib
import json
from pathlib import Path
from relink_recipe_strategy import legacy_index
from maximize_recipe_evidence import additional_nutrients
from recipe_composition_validation import composition_problem

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recipe-residual-89'


def run():
    documents = json.loads((OUT/'new-document-index.json').read_text(encoding='utf-8'))
    source = next(r for r in documents if r['url']=='https://www.daegufood.go.kr/kor/data/down01.pdf')
    assert hashlib.sha256((ROOT/source['rawFile']).read_bytes()).hexdigest() == source['sha256']
    nutrients = legacy_index() | additional_nutrients()
    # Printed page 40 (PDF page 41), visually checked. All masses belong to
    # this single ten-serving recipe; no spoon, piece or density conversion.
    composition = [
        ('양상추',600,'MFDS-LEGACY-1472','양상추, 생것'),
        ('양배추',200,'MFDS-LEGACY-1466','양배추, 생것'),
        ('오이',100,'168409','Cucumber, with peel, raw'),
        ('마요네즈',100,'171009','Salad dressing, mayonnaise, regular'),
        ('꿀',60,'169640','Honey'),
        ('식초',30,'MFDS-LEGACY-4555','식초,식초'),
        ('레몬즙',30,'167747','Lemon juice, raw'),
    ]
    inputs=[]
    for ingredient,grams,code,expected in composition:
        nutrient=nutrients[code]
        assert nutrient['name']==expected
        inputs.append(dict(ingredient=ingredient,amountGrams=grams,nutrient=nutrient,
            identityStatus='REVIEWED_GENERIC_SAME_FOOD_AND_STATE',
            identityReason='식약처/USDA 일반 식품 항목. 브랜드·품종·저지방 제품을 임의 선택하지 않음. 씻고 채썬 생채소 및 소스 원료.',
            amountEvidenceStatus='REFERENCE_RECIPE_EXACT',originalSpan=f'{ingredient} {grams}g',
            originalRecord=dict(documentPage=40,pdfPage=41,servings=10,amount=grams,unit='g'),
            estimatedKcal=grams*nutrient['energyKcal']/100))
    ref=dict(recipeId='DAEGU-LOW-SODIUM-P40',name='양상추샐러드(마요네즈 소스)',
        compositionKind='REFERENCE_RECIPE',sourceInstitution='대구광역시',
        sourceUrl=source['url'],sourceSha256=source['sha256'],rawFile=source['rawFile'],
        document='대구광역시 저염 조리책',documentPage=40,pdfPage=41,
        checkedAt='2026-10-05',basis='대구시 조리책 10인분 전체 재료량 1120g · 마요네즈 소스 참고 구성',
        ingredientText=', '.join(f'{a} {g}g' for a,g,_,_ in composition),inputs=inputs,
        complete=True,originalAmountOverridden=False,
        matchingReason='양상추 중심 생채소에 소스를 버무리는 동일 샐러드. 마요네즈 소스 변형명을 표시하며 원본 MFDS 양을 대체하지 않음.',
        visualEvidence=['data-source/recipe-residual-89/daegu-41.png'])
    assert sum(x['amountGrams'] for x in inputs)==1120
    assert not composition_problem(ref)
    references=[ref]
    rda={n['code']:n for n in json.loads((ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json').read_text(encoding='utf-8'))}
    for capture in json.loads((OUT/'new-menuzen-source-captures.json').read_text(encoding='utf-8')):
        if capture['status']!='CAPTURED':continue
        raw=ROOT/capture['rawFile']
        assert hashlib.sha256(raw.read_bytes()).hexdigest()==capture['sha256']
        parsed=json.loads(raw.read_text(encoding='utf-8-sig'));header=parsed['foodDetailHeader']
        parsed_inputs=[]
        for row in parsed['foodDetailList']:
            nutrient=rda.get(row['nationStdFoodCode'])
            if nutrient and nutrient['name'].replace(' ','')!=row['foodNm'].replace(' ',''):nutrient=None
            parsed_inputs.append(dict(ingredient=row['foodNm'],amountGrams=row['foodWgh'],nutrient=nutrient,
                identityStatus='EXACT_OFFICIAL_CODE_AND_QUALIFIED_NAME' if nutrient else 'UNRESOLVED',
                amountEvidenceStatus='REFERENCE_RECIPE_EXACT',originalRecord=row,
                estimatedKcal=row['foodWgh']*nutrient['energyKcal']/100 if nutrient else None))
        references.append(dict(recipeId='MENUZEN-'+parsed['foodDetailList'][0]['fdCode'],name=header['fdNm'],
            compositionKind='REFERENCE_RECIPE',sourceInstitution='농촌진흥청 국립식량과학원 메뉴젠',
            sourceUrl=capture['url'],sourceSha256=capture['sha256'],checkedAt='2026-10-05',rawFile=capture['rawFile'],
            basis=f"메뉴젠에 제시된 전체 재료량 {header['totalFoodWgh']}g · 별도 공식 참고 구성",
            inputs=parsed_inputs,complete=all(i['nutrient'] and i['amountGrams']>0 for i in parsed_inputs),
            originalAmountOverridden=False,header=header))
    (OUT/'new-validated-reference-compositions.json').write_text(json.dumps(references,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(recipeId=ref['recipeId'],inputs=len(inputs),mass=1120,
                         estimatedKcal=sum(x['estimatedKcal'] for x in inputs)),ensure_ascii=False))


if __name__=='__main__':
    run()
