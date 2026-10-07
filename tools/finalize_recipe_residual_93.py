"""Record all 93 outcomes without treating missing quantities as zero."""
import collections,csv,hashlib,json,re
from pathlib import Path
from pypdf import PdfReader
from lxml import html
from finalize_recipe_residual_89 import review
from import_kfind_foods import normalize_name
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-residual-93';AS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(n,v):(OUT/n).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def run():
 states={r['recipeId']:r for r in load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json')}
 documents=load(OUT/'new-document-index.json');oldreviews=load(ROOT/'data-source/recipe-residual-89/new-primary-candidate-reviews.json')
 adjudications=[]
 for d in documents:
  if d['status']!='CAPTURED':continue
  raw=ROOT/d['rawFile'];assert hashlib.sha256(raw.read_bytes()).hexdigest()==d['sha256']
  if raw.read_bytes().startswith(b'%PDF'):
   indexed=(ROOT/d['textFile']).read_text(encoding='utf-8')
   pages=re.split(r'(?:^|\n\n)PAGE \d+\n',indexed)[1:]
   assert len(pages)==d['pageCount']
  else:
   doc=html.fromstring(raw.read_text(encoding='utf-8',errors='replace'))
   for node in doc.xpath('//script|//style'):node.drop_tree()
   pages=[doc.text_content()]
  for m in d['matches']:
   for page in m['pages']:
    prior=next((r for r in oldreviews if r['sourceUrl']==d['url'] and r['recipeId']==m['recipeId'] and r['page']==page),None)
    entry=dict(recipeId=m['recipeId'],name=m['name'],sourceUrl=d['url'],sourceSha256=d['sha256'],rawFile=d['rawFile'],page=page,
      excerpt=re.sub(r'\s+',' ',pages[page-1]),decision='NOT_PUBLISHED',checkedAt='2026-10-05',priorReviewReused=bool(prior))
    if prior:reason=prior['reason']
    elif 'kdca.go.kr' in d['url']:reason='외식 선택 예시에서 도토리묵밥 명칭만 제시. 레시피·재료별 중량·음식 kcal 원문 없음.'
    elif '20180629' in d['url']:reason='명절 음식 전체 kcal 표. 해당 음식 재료별 양 없음. 기존 Food 공식값을 교체하지 않음.'
    elif 'u1.ac.kr' in d['url']:reason='국/찌개 및 떡 조리법 분류 강의. 음식 예시 명칭만 있고 전체 정량 배합 없음.'
    elif 'ice.go.kr/upload/board' in d['url']:reason='학급 공동체 교육 활동의 음식 예시. 조리 레시피·재료 중량 없음.'
    elif 'korean.go.kr' in d['url']:reason='민족생활어 지역 음식 조사: 이름·어휘·조리 과정 설명. 해당 음식 전체 계량 배합 없음.'
    else:reason=review(entry)
    entry['reason']=reason;adjudications.append(entry)
 save('primary-candidate-adjudications.json',adjudications)
 receipts=[]
 for p in sorted(OUT.glob('school-search-*.json')):
  item=load(p);receipts.append((p.name,item['queries']))
 ledger=[]
 for previous in load(OUT/'scope-87.json'):
  s=states[previous['recipeId']];item=dict(previous)
  executed=[name for name,qs in receipts if previous['name'] in qs]
  assert executed
  item.update(state=s['state'],appComplete=s['appCompleteAvailable'],finalDisposition=s['state'] if s['appCompleteAvailable'] else s['state']+'_WITH_EXHAUSTED_EVIDENCE',
   currentExecutedSearchReceipts=executed,currentPrimaryReviews=[r for r in adjudications if r['recipeId']==s['recipeId']],
   newRoute='음식명 + 학교급식 표준레시피 재료량. 기존 PDF/제조사/의료공공 검색과 별도 실행.',
   exhaustionScope='이번 검색 경로 및 실제 획득·색인 문서에서 완전 정량 구성을 확정하지 못함. 인터넷 전체 또는 미공개 자료 부재를 뜻하지 않음.',
   wholeInternetExhausted=False,checkedAt='2026-10-05')
  ledger.append(item)
 assert len(ledger)==87
 save('all-87-final-evidence-ledger.json',ledger)
 food=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(OUT/('baseline-'+n))]
 approved={r['originalRecipeId']:r for r in load(OUT/'approved-new-food-evidence.json')};six=[]
 for prior in load(ROOT/'data-source/recipe-residual-89/question-required-six-final.json'):
  names={normalize_name(prior['name'])}
  if prior['recipeId'] in approved:names.add(normalize_name(approved[prior['recipeId']]['reportedNutritionHeader']['fdNm']))
  matches=[r['id'] for r in food if names & {normalize_name(r['name']),normalize_name(r['normalizedName']),*(normalize_name(a) for a in r['aliases'].split('|') if a)}]
  assert not matches
  item=dict(prior);evidence=approved.get(prior['recipeId'])
  item.update(duplicateRowsScanned=len(food),lastExactNormalizedAliasMatches=matches,newFoodCreated=bool(evidence),foodId=evidence['foodId'] if evidence else None,
   appComplete=states[prior['recipeId']]['appCompleteAvailable'],currentState=states[prior['recipeId']]['state'],currentFoodEvidence=evidence,
   finalDisposition='REFERENCE_COMPLETE' if evidence else 'COMPLETE_COMPOSITION_WITHOUT_VERIFIED_FOOD_NUTRITION',
   remainingReason=None if evidence else 'FoodItem.energyKcal는 non-null이며 seeder는 필수 Double로 읽음. 음식 자체 공식 kcal 미확보. 기존 reference-only unknown kcal Food 정책 없음. 원본 구성의 재료 kcal 합계나 0/NaN/임의 kcal로 Food 생성 금지.',checkedAt='2026-10-05')
  six.append(item)
 save('approved-six-final.json',six)
 save('remaining-91-foods.json',[r for r in ledger+six if not r['appComplete']])
 save('new-app-complete-foods.json',[r for r in six if r['appComplete']])
 conflicts=load(ROOT/'data-source/recipe-residual-89/current-212-conflict-review.json')
 for r in conflicts:r.update(residual93Resolution=False,residual93Reason='신규 두 전체 reference는 기관 원문 직접 g 사용. 원본 계량 충돌을 역해결하거나 평균하지 않음.')
 save('current-212-conflict-review.json',conflicts)
 protected=[]
 for name in ('food_items.csv','product_items.csv','franchise_official_items.csv','official_recipe_reference_estimates.csv','recipe_ingredient_estimates.csv'):
  old=rows(OUT/('baseline-'+name));now=rows(AS/name);key=(lambda r:r['id']) if 'items' in name else lambda r:(r['foodId'],r['recipeId'],r['ingredientName'])
  idx={key(r):r for r in now};assert all(idx.get(key(r))==r for r in old)
  protected.append(dict(asset=name,beforeRows=len(old),afterRows=len(now),everyBaselineRowUnchanged=True,sha256=hashlib.sha256((AS/name).read_bytes()).hexdigest()))
 save('protected-data-verification.json',protected)
 save('final-progress.json',dict(status='PARTIAL',total=516,beforeAppComplete=423,appComplete=sum(s['appCompleteAvailable'] for s in states.values()),beforeRemaining=93,remaining=91,
  states=dict(collections.Counter(s['state'] for s in states.values())),scope87=dict(collections.Counter(r['finalDisposition'] for r in ledger)),newFoodCount=2,foodCount=67356,
  referenceRows=len(rows(AS/'official_recipe_reference_estimates.csv')),newReferenceRows=20,originalIngredient=load(ROOT/'data-source/recipe-final-residual/original-ingredient-progress.json'),
  formalProduction=dict(versionCode=7,versionName='1.0.6',install=0,clear=0,overwrite=0,deploy=0),dbVersion=8,migrationsAdded=0))
 print(json.dumps(load(OUT/'final-progress.json'),ensure_ascii=False))
if __name__=='__main__':run()
