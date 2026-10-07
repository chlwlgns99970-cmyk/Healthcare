"""Restore two exact K-FIND food-weight references, independently of nutrition basis.

The source says 식품중량, not one-person serving or a household vessel. Preserve
that meaning; these fixed g candidates do not certify a bowl/serving conversion.
"""
from pathlib import Path
import csv
import hashlib
import re
from extract_food_identity_fields import ROOT, OUT, SOURCE_URL, extract, rows

VERSION='exact-kfind-food-weight-recommendation-v1'
SOURCE=ROOT/'data-source/kfind/kfind-food-db-2026-08-28.xlsx'
TARGET=OUT/'followup-recommendation-portion-evidence.csv'
EXPECTED={'D305-239000000-0001':('어탕','150g','2014-09-30'),
          'D110-472000000-0001':('멸치볶음','50g','2018-12-31')}

def build():
    official,audit=extract(SOURCE,EXPECTED)
    official={r['식품코드']:r for r in official}
    assert set(official)==set(EXPECTED)
    original={r['식품코드']:r for r in rows(OUT/'raw-identity-fields.csv')if r['식품코드']in EXPECTED}
    foods={r['sourceFoodCode']:r for r in rows(ROOT/'app/src/main/assets/fooddata/food_items.csv')if r['sourceFoodCode']in EXPECTED}
    output=[]
    for code in sorted(EXPECTED):
        name,weight,date=EXPECTED[code];r=official[code];food=foods[code]
        assert r=={k:original[code][k]for k in r}
        assert (r['식품명'],r['식품중량'],r['데이터생성일자'])==(name,weight,date)
        assert r['영양성분함량기준량']=='100g'
        assert not r['1인(회)분량 참고량']and not r['1회 섭취참고량']
        assert (food['id'],food['name'],food['referenceAmount'],food['unit'],food['brand'])==('kfind-'+code.lower(),name,'100','g','')
        amount=re.fullmatch(r'(\d+(?:\.\d+)?)g',weight).group(1)
        output.append(dict(foodItemId=food['id'],sourceType=food['sourceType'],sourceFoodCode=code,name=name,brand='',
            recommendationReferenceAmount=amount,recommendationReferenceUnit='g',
            recommendationSourceReference=SOURCE_URL,
            nutritionReferenceAmount='100',nutritionReferenceUnit='g',rawNutritionBasis=r['영양성분함량기준량'],
            rawFoodWeight=weight,rawPersonServingReference=r['1인(회)분량 참고량'],rawIntakeReference=r['1회 섭취참고량'],
            sourceName='식품의약품안전처 K-FIND 원본 식품중량',sourceUrl=SOURCE_URL,sourceDate=date,
            sourceVersion=SOURCE.name,checkedAt='2026-10-04',parserVersion=VERSION,
            identityEvidence=f'EXACT_KFIND_SOURCE_FOOD_CODE:{code}|ORIGINAL_SOURCE_DATE:{date}|COLUMN:식품중량',
            evidenceKind='EXACT_OFFICIAL_FOOD_WEIGHT_REFERENCE_NOT_HOUSEHOLD_SERVING',
            sourceSha256=audit['sha256'],notes='원본 분석 식품중량을 고정 g 추천 후보로 사용; 100g 영양 계산 기준과 별도; 원문 1인(회)분량·섭취참고량 공란이며 1인분·그릇·접시라고 주장하지 않음'))
    with TARGET.open('w',encoding='utf-8',newline='')as f:
        writer=csv.DictWriter(f,fieldnames=list(output[0]),lineterminator='\n');writer.writeheader();writer.writerows(output)
    print(f'{len(output)} exact source food-weight references; nutrition 100g basis unchanged; household units 0')

if __name__=='__main__':build()
