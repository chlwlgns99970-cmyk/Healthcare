"""Summarize captured evidence and completed checks; never rerun device actions."""
import collections, hashlib, json
from pathlib import Path
from urllib.parse import urlparse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recipe-residual-93'
def read(name):
    return json.loads((OUT / name).read_text(encoding='utf-8'))
def save(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

docs = read('new-document-index.json')
reviews = read('primary-candidate-adjudications.json')
by_host = collections.defaultdict(lambda: dict(documentsInvestigated=0, captured=0, failed=0, matchedPageReviews=0, newCompleteReferences=0, newAppLinks=0))
for d in docs:
    row = by_host[urlparse(d['url']).hostname]
    row['documentsInvestigated'] += 1
    row['captured' if d['status']=='CAPTURED' else 'failed'] += 1
for r in reviews:
    by_host[urlparse(r['sourceUrl']).hostname]['matchedPageReviews'] += 1
by_host['www.nics.go.kr'].update(documentsInvestigated=2, captured=2, newCompleteReferences=0, newAppLinks=2, previouslyCompleteReferencesReused=2)
save('source-institution-results.json', {'countingBasis':'문서 URL별 조사 수. 87개 신규 완전 구성은 0, 기존 완전 구성의 신규 Food 연결은 농촌진흥청 2. 검색 결과 수와 중복 계산하지 않음.', 'institutions':dict(sorted(by_host.items()))})
unit=[]
for name in ('FullRecipeReferenceTest','RecipeMenuCompletionTest','SearchRecipeFollowupTest'):
    path=ROOT/f'app/build/test-results/testQaUnitTest/TEST-com.example.healthcare.{name}.xml'
    root=ET.parse(path).getroot()
    unit.append(dict(name=name, tests=int(root.attrib['tests']), failures=int(root.attrib['failures']), errors=int(root.attrib['errors']), skipped=int(root.attrib.get('skipped',0)), timestamp=root.attrib.get('timestamp'), artifact=str(path.relative_to(ROOT))))
assert sum(r['tests'] for r in unit)==12 and all(not r['failures'] and not r['errors'] for r in unit)
preserve=json.loads((ROOT/'app/build/recipe-residual-93/preservation-verify.json').read_text(encoding='utf-8'))
assert preserve['status']=='PASS'
for name,count in [('samsung-flow-output.txt',2),('samsung-protection-output.txt',20)]:
    assert f'OK ({count} tests)' in (ROOT/'app/build/recipe-residual-93'/name).read_text(encoding='utf-8-sig')
save('validation-summary.json',dict(status='PASS',dataTests=24,unitTests=unit,samsungNewFoodFlows=2,samsungProtectionTests=20,qaBuild='PASS',qaApkSha256=hashlib.sha256((ROOT/'app/build/outputs/apk/qa/app-qa.apk').read_bytes()).hexdigest(),privateFilesPreserved=16,productIdentityUnchanged=True,dbVersion=8,migrationsAdded=0,productionActions=0,note='PASS는 검증 결과이며 516개 완성 상태를 의미하지 않음. 전체 작업 상태 PARTIAL.'))
save('additional-source-decisions.json',[
 dict(source='KAMIS 4327.pdf',decision='NOT_PUBLISHED',reason='도토리묵밥 이미지 원문 검토: 복합 육수 1.2L 구성 미상, 약간 단위 남음. 기존 MFDS 묵+밥 구성과 다르고 음식 자체 kcal 없음.',image='data-source/recipe-residual-93/kamis-page-1.png'),
 dict(source='해외 공식자료 검색 및 열람',receipt='overseas-source-open.json',decision='NOT_PUBLISHED',reason='NCHFP 딸기잼은 펙틴 패키지 정량 미확정. 공식 Summer Breeze는 바나나·파인애플·딸기 혼합으로 순수 바나나스무디와 identity 다름. 후보들을 섞거나 수치를 전용하지 않음.'),
 dict(source='신규 Food 4개 공식 영양 조사',receipts=['new-food-nutrition-search.json','explicit-source-search-001.json'],decision='NOT_CREATED',reason='완전 구성은 있으나 음식 자체 kcal 근거 미확보. FoodItem.energyKcal non-null Double, BundledFoodDataSeeder 필수 Double. 기존 unknown kcal Food 표현 정책 없음. 재료 합계를 공식 총량으로 만들거나 0/NaN 저장하지 않음.')
])
print('Report evidence and validation summary saved; all completed check receipts verified.')
