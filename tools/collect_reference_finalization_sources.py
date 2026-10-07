"""Curated whole-recipe source expansion; captures never authorize publication."""
import json,shutil
from pathlib import Path
from collect_recipe_full_reference_sources import run as capture
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-reference-finalization'
CODES=['D102015','D102016','D102040','D102041','D102044','D102012','D016010','D092007','D092006',
 'D052158','D051179','D014050','D014065','D032017','D032011',
 'D123002','D103007','D102010','D051051','D052074','D095007','D121028',
 'D053027','D133024','D031095','D083004','D015074','D015013','D014081',
 'D051264','D053095','D053082','D071004','D083005','D053062','D051277',
 'D051283','D093014','D052049','D052157','D012100','D012101','D132116',
 'D082018','D014009','D051300','D061040','D102035']
def run():
    OUT.mkdir(exist_ok=True)
    for source,name in [(ROOT/'app/src/main/assets/fooddata/official_recipe_reference_estimates.csv','before-reference-asset.csv'),
        (ROOT/'data-source/recipe-final-residual/recipe-final-states.json','before-states.json'),
        (ROOT/'data-source/recipe-final-residual/progress.json','before-progress.json')]:
        target=OUT/name
        if not target.exists():shutil.copyfile(source,target)
    catalog=json.loads((ROOT/'data-source/recipe-full-reference/raw/d043efeb8cadc2124156').read_text(encoding='utf-8'))['eumsikList']
    # Extend to named generic/vegetable kimbap sources, preserving the recipe's
    # actual qualifier and never presenting ham as a vegetable composition.
    queue=json.loads((OUT/'reference-first-work-queue.json').read_text(encoding='utf-8'))
    # A broad retrieval pass is read-only. Even matching method/name candidates
    # still need manual primary-ingredient and food identity adjudication.
    proposed=[c['code'] for q in queue if not q['appComplete'] for c in q['uncapturedMenuzenCandidates'] if c['methodCompatible']]
    codes=list(dict.fromkeys(CODES+proposed+[r['eumsikCode'] for r in catalog if r['eumsikName'] in ('김밥','야채김밥','채소김밥','유부초밥')]))
    captures=json.loads((ROOT/'data-source/recipe-full-reference/source-captures.json').read_text(encoding='utf-8'))
    example=next(c['url'] for c in captures if 'selectFoodDetail.json?fdCode=' in c['url'])
    prefix=example.split('fdCode=')[0]+'fdCode='
    urls=[prefix+code for code in codes]
    urls.append('https://www.nongsaro.go.kr/portal/ps/psr/psrb/monthFdmtDtl.ps?cntntsNo=205161&menuId=')
    (OUT/'curated-source-request.json').write_text(json.dumps(dict(codes=codes,urls=urls,policy='manual content review required'),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    capture(urls)
if __name__=='__main__':run()
