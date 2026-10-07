"""155-food receipts with bounded research claims and explicit rejected candidates."""
import collections,csv,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-reference-finalization';FINAL=ROOT/'data-source/recipe-final-residual'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(n,x):(OUT/n).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def run():
    queue=load(OUT/'reference-first-work-queue.json');states={s['recipeId']:s for s in load(FINAL/'recipe-final-states.json')}
    maps={m['originalRecipeId']:m for m in load(FINAL/'food-mapping-decisions.json')}
    refs={r['recipeId']:r for r in load(FINAL/'validated-reference-compositions.json')+load(FINAL/'additional-original-compositions.json')}
    before={s['recipeId']:s for s in load(OUT/'before-states.json')}
    previous={r['originalRecipeId'] for r in load(ROOT/'data-source/recipe-final-residual-followup/newly-reviewed-mappings.json')}
    foods={f['id']:f for f in rows(ROOT/'app/src/main/assets/fooddata/food_items.csv')}
    originals=collections.defaultdict(list)
    for d in load(FINAL/'ingredient-decisions.json'):originals[d['recipeId']].append(d)
    search=collections.defaultdict(list)
    for pattern in ['web-search-batch-*.json','expanded-search-*.json']:
        for p in sorted(OUT.glob(pattern)):
            receipt=load(p)
            for food in receipt['foods']:search[food['id']].append(p.name)
    docs=load(OUT/'primary-document-index.json')
    complete=[];remaining=[];ledger=[];aliasAudit=[]
    normalize=lambda s:re.sub(r'[\s_(),<>/·]','',s)
    specialReasons={
      'MFDS-239':'딸기·잼을 식빵에 말아 만든 원본 완전 샌드위치. 앱의 채소·햄·육류·모듬 샌드위치는 딸기 롤 정체성을 보증하지 않으며 정확한 무브랜드 과일 샌드위치 foodId 없음.',
      'MFDS-255':'연근·모차렐라치즈·튀김옷을 굽는 완전 원본. 콘치즈·일반 치즈구이는 연근 주재료가 다르고 해당 음식 foodId 없음.',
      'MFDS-256':'원본 재료는 도토리묵200g·밥280g이며 조리문은 함께 담는 것만 명시. 앱 묵밥·도토리묵말이는 육수 있는 음식이어서 원본의 조리 문맥 불일치; 국물이나 양념을 합성하지 않음.',
      'MFDS-306':'원본은 우유500g·고구마200g·잣30g·소금3g을 끓이는 완전 스프. 잣죽의 쌀과 크림스프의 밀/감자는 다른 주재료. 같은 고구마·잣 스프 foodId 없음.',
      'MFDS-223':'원문 MFDS223는 딸기잼(4인분)-81g만 있고 재료 분해·조리문 없음. 메뉴젠 D212044도 완제품 자기 참조이므로 완전 구성 제외. 학회 KCI ART001678995는 확보한 초록에 배합량이 없으며 제조사 제품으로 대체하지 않음.',
      'MFDS-295':'원본 주꾸미 먹물10g의 종·부위에 대응하는 검증된 100g 영양값 미확보. 태국 TSTJ186944는 A.aegina 등 다른 종과 먹물 젤리 연구; ICAR143304는 먹물샘 제거한 식용부. 몸통·오징어/갑오징어 먹물·추출물 값 대입 금지.',
      'MFDS-108':'공공 급식 양상추샐러드/키위소스는 명시된 재료량과 조리문 물엿이 불일치. 대한당뇨병학회는 드레싱25g이나 해당 소스 영양 identity 미확정. 임의 소스·중량 추가 없이 부분 유지.',
      'MFDS-93':'서울의료원 원문은 멍게500g 외 T·개·줄과 참기름/통깨/밥의 누락 중량. 신장학회 책자97쪽은 완성 음식의 총 영양표이며 재료별 레시피가 아님. 완전 구성으로 합성 불가.',
      'MFDS-241':'대한영양사협회 청소년 특선식단 PDF2쪽에 김치콩나물밥100인 배합을 확보했으나 소금 정량이 없고 정확한 김치·콩나물 밥 foodId 미확인. 김치볶음밥과 조리방식 구분.',
      'MFDS-279':'오뚜기 공식 고등어김치찜은 통조림 고등어1개·묵은지1/4포기·T단위 배합. 원본 생 고등어와 상태·제품·정량 다름. 메뉴젠 일반 고등어찜에 김치를 덧붙이지 않음.',
      'MFDS-340':'Samsung 공식 책자122쪽 매콤한 닭봉구이는 닭봉500g 외 양념 큰술·작은술. 재료별 동일 상태의 g 환산과 같은 음식 foodId가 없음. 닭다리나 닭날개를 닭봉으로 대체하지 않음.',
      'RDA-91764':'원본 제목의 곤달비와 괄호 곤드레는 식물 종이 달라 괄호만으로 동의어 처리 금지. 메뉴젠 곤드레밥의 배합은 곤달비 배합과 같다고 확정할 수 없음.',
      'RDA-91542':'원본 제목은 낙지볶음/전골을 함께 쓰며 실제 문맥을 확인했으나 앱 낙지전골에 일반 팬 볶음을 붙일 수 없음. 명칭 앞부분만으로 볶음 foodId 강제 연결 금지.'}
    for q in queue:
        s=states[q['recipeId']];m=maps.get(q['recipeId']);ds=originals[q['recipeId']]
        # The source aliases contain ingredient tokens too. Exact alias hits are
        # candidate evidence only and never authorization to ignore qualifiers.
        aliasHits=[dict(foodId=f['id'],name=f['name'],category=f['category'],normalizedName=f['normalizedName'],aliases=f['aliases'],source=f['sourceType'],brand=f['brand'],referenceAmount=f['referenceAmount'],unit=f['unit']) for f in foods.values() if f['sourceType']=='K-FIND' and not f['brand'] and (normalize(q['name'])==normalize(f['normalizedName']) or any(normalize(q['name'])==normalize(a) for a in f['aliases'].split('|') if a))]
        aliasAudit.append(dict(recipeId=q['recipeId'],name=q['name'],appComplete=s['appCompleteAvailable'],exactNormalizedOrAliasCandidates=aliasHits,automaticPublication=False))
        sourceDocs=[dict(url=d['url'],sha256=d['sha256'],rawFile=d['rawFile'],pageCount=d['pageCount'],matchedPages=next(x['pages'] for x in d['matches'] if x['recipeId']==q['recipeId']),review='전체 텍스트 색인에서 해당 이름을 찾은 페이지. 정량 채택은 별도 원문 검토 필요') for d in docs if any(x['recipeId']==q['recipeId'] for x in d['matches'])]
        candidates=[]
        for c in q['rankedExistingReferences']:
            r=refs[c['recipeId']]
            reason='조리 형태 불일치' if not c['methodCompatible'] else '레시피 자체 미완전: '+str(r.get('compositionValidationProblem') or c['unresolvedIngredients']) if not r['complete'] else '명시 주재료 전체 일치 불확정' if c['primaryIngredientCoverage']<1 else '완전 후보라도 원본의 음식 identity·qualifier와 안전한 foodId 일치가 확정되지 않음'
            candidates.append(c|dict(validationComplete=r['complete'],adjudication='SELECTED_WHOLE_REFERENCE' if m and m['selectedId']==c['recipeId'] else 'NOT_PUBLISHED',reason=m['reason'] if m and m['selectedId']==c['recipeId'] else reason))
        record=dict(recipeId=q['recipeId'],name=q['name'],state=s['state'],appComplete=s['appCompleteAvailable'],originalComplete=s['originalComplete'],
            originalSource=dict(url=q['originalSourceUrl'],sha256=q['originalSourceSha256'],ingredientText=q['originalIngredientText'],cookingContext=q['originalContext']),
            originalUnresolved=[dict(ingredient=d['ingredient'],span=d['originalSpan'],status=d['status'],identityReason=d['identityReason'],unitReason=d['unitReason'],amountGrams=d['amountGrams'],nutrition=d.get('nutritionProvenance')) for d in ds if d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM')],
            searchVariants=q['searchVariants'],executedSearchReceipts=search[q['recipeId']],
            bulkSourcesReviewed=dict(menuzenCatalog=3250,acquiredWholeReferences=len(refs)-len(load(FINAL/'additional-original-compositions.json')),kdcaGroups=613,officialFoods=67354,documentIndex='primary-document-index.json'),
            matchingSourceDocuments=sourceDocs,rankedReferenceAdjudications=candidates,foodCandidates=q['foodCandidates'],
            verifiedFoodIds=s['targetFoodIds'],selectedMapping=m,wholeInternetExhausted=False,
            evidenceScope='현재 확보한 공공 원문·메뉴젠 전체 목록·KDCA 전체 그룹·기존 영양표·책자 전체 텍스트 색인과 기록된 두 기관 검색 전략. 검색 결과 수치는 채택하지 않음.')
        if s['appCompleteAvailable']:
            record['finalDisposition']=s['state']
            assert m and m['foodIds']
            if not before[s['recipeId']]['appCompleteAvailable'] or s['recipeId'] in previous:complete.append(record)
        else:
            assert len(search[q['recipeId']])>=2,(q['recipeId'],search[q['recipeId']])
            record['finalDisposition']='PARTIAL_WITH_EXHAUSTED_EVIDENCE' if s['state']!='UNRESOLVED' else 'UNRESOLVED_WITH_EXHAUSTED_EVIDENCE'
            record['exhaustedMeaning']='확인한 자료·실행한 검색 범위 내 안전하게 채택 가능한 완전 배합/foodId 근거 소진. 모든 인터넷 자료가 없다는 뜻은 아님. 새로운 정량 원문·동일 음식 foodId가 확보되면 재검토.'
            record['remainingReason']=specialReasons.get(q['recipeId']) or ('기존 67,354개 식품에서 해당 이름·주재료·조리형태의 안전한 foodId 미확정. 근접 음식의 단백질 종·부위·조리 형태를 바꾸거나 제조사 제품을 일반 음식으로 연결하지 않음. '+str([c['name'] for c in q['foodCandidates'][:3]]) if not s['targetFoodIds'] else '음식 foodId는 확인했으나 동일 음식의 모든 재료 identity·정량·영양이 완전한 하나의 원문 배합을 확보하지 못함. 부족한 항목은 originalUnresolved 및 후보별 미채택 이유 참조.')
            remaining.append(record)
        ledger.append(record)
    assert len(ledger)==155 and len(complete)==58 and len(remaining)==97
    save('all-155-food-evidence-ledger.json',ledger);save('new-complete-foods-from-361.json',complete);save('remaining-97-foods.json',remaining)
    save('exact-existing-alias-audit.json',aliasAudit)
    priority=[]
    for q in load(OUT/'unlinked-complete-nine.json'):
        s=states[q['recipeId']];r=next(x for x in ledger if x['recipeId']==q['recipeId'])
        priority.append(r|dict(priorityFoodReview=[{k:foods[fid][k] for k in ['id','name','normalizedName','aliases','category','brand','sourceType','sourceFoodCode','referenceAmount','unit','servingDescription','energyKcal']} for fid in s['targetFoodIds']],remainingNoFoodId=not s['appCompleteAvailable']))
    assert len(priority)==9 and sum(p['remainingNoFoodId'] for p in priority)==4
    save('priority-nine-final-review.json',priority)
    conflicts=load(FINAL/'portion-conflict-reassessments.json')
    # Existing comparisons retain raw/cooked, cutting, measurement, source page,
    # and serving definitions. New PDFs add no row-specific mass conversion.
    save('current-212-conflict-review.json',[r|dict(currentAdditionalDocumentIndex='primary-document-index.json',newRowSpecificConversionAvailable=False,selection='NO_AVERAGE_OR_GLOBAL_SPOON_WEIGHT') for r in conflicts if r['finalStatus']=='UNRESOLVED'])
    grouped=collections.defaultdict(list)
    for r in complete:grouped[refs[r['selectedMapping']['selectedId']]['sourceInstitution']].append(r)
    institutions=[dict(institution=i,foodsSearched=155,acquiredReferences=sum(x['sourceInstitution']==i and x['compositionKind']!='ORIGINAL' for x in refs.values()),originalRecipesAvailable=211 if i=='식품의약품안전처' else 0,newAppCompleteFoods=len(v)) for i,v in grouped.items()]
    additional=[dict(institution=i,foodsSearched=155,acquiredDocuments=sum(any(h in d['url'] for h in hosts) for d in docs),verifiedCompleteReferencesForRemainingFoods=0,newAppCompleteFoods=0,method='기록된 검색 및 책자 전체 텍스트 색인; 음식의 전체 정량 배합 검증 수와 별개') for i,hosts in [
      ('대학 공개 자료',('text.cnu.ac.kr','dspace.hansung.ac.kr')),
      ('영양사·신장·식품위생 학회',('dietitian.or.kr','ksn.or.kr','foodhygiene.or.kr')),
      ('제조사 공식 조리책·레시피',('images.samsung.com','okitchen.co.kr')),
      ('서울의료원',('webzine.seoulmc.or.kr',)),
      ('지자체·공공 자료',('ebook.gg.go.kr','50plus.or.kr','kamis.or.kr','hansik.or.kr')),
      ('해외 학술·정부 연구기관 영양 자료',('li01.tci-thaijo.org','epubs.icar.org.in'))]]
    save('source-institution-finalization-summary.json',dict(publishedInstitutions=institutions,additionalInstitutions=additional,wholeReferenceBankCount=1385,menuzenReferences=769,kdcaReferences=613,additionalDocumentsIndexed=len(docs),externalExpandedSearchFoods=97,note='새 앱 연결 수는 사용자 기준361 이후58개. 원본 ingredient 해결 수와 구분. 검색 및 원문색인 수는 조리레시피 확보 수와 구분. source1 딸기잼·nutrition1 주꾸미 먹물의 기관·원문별 미채택 근거는 음식별 ledger에 저장.'))
    print(json.dumps(dict(scope=len(ledger),newComplete=len(complete),remaining=len(remaining),priorityUnlinked=4,conflicts=212),ensure_ascii=False))
if __name__=='__main__':run()
