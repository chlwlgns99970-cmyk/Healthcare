"""Review every household unit by original bibliography and physical form."""
import collections
import csv
import json
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-full-reference'
def load(p): return json.loads(p.read_text(encoding='utf-8'))
def norm(s): return re.sub(r'\s+','',s)

def run():
    facts={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv')
        for r in csv.DictReader(p.open(encoding='utf-8-sig'))}
    reviews=[]
    for row in load(ROOT/'data-source/recipe-amount-priority/official-book-portion-candidates.json'):
        bib=facts[row['recipeId']]['sourceReference']
        same=[r for r in row['references'] if r['file']=='original-recipe-facts' and
            bib and norm(facts.get(r['page'],{}).get('sourceReference',''))==norm(bib)]
        values=sorted({round(r['gramsPerUnit'],8) for r in same})
        # Shared tablespoons of an identical named ingredient/form may be reviewed.
        # Variable pieces, cups of unspecified capacity, slices, and generic liquids remain unsafe.
        safe=row['unit'] in ('큰술','작은술') and len(values)==1 and len(same)>=2
        reviews.append(row|dict(sameBibliography=bib,sameBibliographyReferences=same,
            preferredGramsPerUnit=values[0] if safe else None,
            reviewResult='SAME_PUBLIC_BIBLIOGRAPHY_FORM_UNIT_VERIFIED' if safe else
                'SAME_BIBLIOGRAPHY_CONFLICT' if len(values)>1 else 'NO_DEFINED_SAME_SOURCE_PORTION',
            reviewReason='같은 원문 참고문헌·동일 재료명/형태·동일 술 단위의 복수 공식 명시값이 일치' if safe else
                '원문과 동일한 계량 정의를 확정할 근거 부족 또는 동일 출처 내 중량 충돌; 다수결·평균 적용하지 않음'))
    (OUT/'portion-reviews.json').write_text(json.dumps(reviews,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(collections.Counter(r['reviewResult'] for r in reviews))
    print([(r['recipeId'],r['ingredient'],r['unit'],r['preferredGramsPerUnit']) for r in reviews if r['preferredGramsPerUnit']])

if __name__=='__main__':run()
