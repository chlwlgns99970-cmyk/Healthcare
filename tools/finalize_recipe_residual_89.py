"""Persist bounded source adjudications; never turn retrieval scores into publication."""
import collections,csv,hashlib,json,re
from pathlib import Path
from lxml import html
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-residual-89'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(n,v):(OUT/n).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def review(r):
    u=r['sourceUrl'];p=r['page'];n=r['name']
    if 'lotteshopping' in u:return '강좌 일정·수강료·메뉴명만 제시. 해당 음식 전체 재료별 중량 없음.'
    if '2014.pdf' in u or 'ksn.or.kr' in u or 'text.cnu' in u:return '완성 음식의 제공량·영양 표 또는 목차이며 전체 재료별 수량을 제시하지 않음.'
    if 'bookcafe065' in u:return '향토음식 명칭·지역·조리법 설명과 색인. 전체 재료별 수량 없음. 어종·조리법을 넘어 동의어 강제 연결하지 않음.'
    if 'korea.kr' in u or 'chuncheon' in u or 'hansung' in u or 'mafra' in u or 'atfis' in u:return '여행·문화·학술 분류·시장 자료의 음식 언급. 해당 음식 전체 재료별 계량 레시피 없음.'
    if 'cuckoo' in u or ('cuisinart' in u and n=='딸기잼'):return '딸기잼을 다른 음식에 사용하는 재료로 언급. 딸기잼 제조 전체 레시피가 아님.'
    if 'bosch' in u:return '바나나·우유·꿀/설탕 블렌딩 조리법은 확보했으나 현재 Food에서 같은 무브랜드 바나나스무디를 확인하지 못함. 유사 주스·브랜드 제품 연결 금지.'
    if 'gyeongnam' in u and p==31:return '고추잎 200g·고춧가루 50g·멸치액젓 100ml 확보. 7일 절임 후 씻는 소금 잔존량, 큰술/작은술 재료별 중량·밀도 미확정.'
    if 'images.samsung' in u:
        if n=='찐만두':return '시판 냉동 만두 6–12개 300–600g 가열 안내. 만두 원재료 구성과 제품별 영양 없음.'
        if p in (43,40,122,123):return '닭봉 1팩 500g은 뼈 포함이며 가식부 수율 없음. 스위트칠리소스 제품과 큰술 중량 미확정.'
        return '목차·색인 또는 인접 메뉴의 재료. 해당 음식 전체 계량 구성 증거가 아님.'
    if 'cuisinart' in u:return '닭봉 개수/냉동감자·후추·소금 약간 또는 떡 개수·소스 약간. 전체 가식부 중량 미확정.'
    if 'noodleplanet' in u:return '다른 면 요리의 추천 식단 사이드 품목으로만 언급. 해당 음식의 전체 재료량 없음.'
    if 'pn.co.kr' in u:
        if 'idx=343' in u:return '취나물 250g 확보. 계량은 큰술/작은술이며 조리법의 참기름이 재료 표에 빠져 전체 수량 불완전.'
        if 'idx=989' in u:return '멍게 120g 확보. 밥 1공기·고추 1개·김가루 미량/수량 미기재. 전체 중량 불완전.'
        return '이미 조리된 취나물 들깨무침 반 컵을 다른 음식에 사용. 원재료별 배합 없음.'
    if 'seoulmc' in u:return '손질 멍게 500g 확보. 줄기/개수·큰술, 갈치속젓/생강청 제품 및 밥·참기름·통깨 수량 미확정.'
    if 'ourhome' in u:
        if '/308' in u:return '1인분 g 표 확보. 조리법의 느타리버섯이 표에 누락. 브랜드 소고기/소스/지단 구성·영양 미확정. 표의 0g을 임의 보정하지 않음.'
        return '꽃맛살샐러드: 제조사 꽃맛살·레몬소스·양상추믹스 제품 구성. 단순 양상추샐러드 동일성 및 제품 원료별 영양 미확정.'
    if 'foodnuri' in u:
        if '215110' in u:return '첨부 원문 이미지 직접 확인: 찹쌀가루 300g, 단호박 1/2개, 소금·설탕 약간, 검은깨 1컵·통깨 3컵. 단호박 가식부·약간·컵 중량 미확정.'
        if '234725' in u:return '멍게 8개·양상추 3장·두반장 3큰술. 가식부 수율·잎 중량·브랜드 제품 영양 불확정.'
        return '다른 게시물 링크/추천명만 언급. 해당 음식 전체 재료별 계량 없음.'
    if 'tongblog' in u:return '주꾸미 1kg·콩나물 350g 확보. 모시조개 1팩·미나리 한줌·참기름 조금·통깨 수량 미기재. 딸기잼은 이전 글 링크.'
    if 'ABDAAF1F' in u:
        if p==14:return '딸기 500g·설탕 300g. 재료 표 레몬즙 1T와 조리법 2스푼이 충돌. Pinterest 이미지 출처로 수치 임의 선택 금지.'
        return '목차·수업 활동·식품 설명 또는 다른 음식 조리법의 딸기잼 언급. 전체 해당 구성 없음.'
    if 'goe.go.kr' in u:
        if p==217:return '단호박카레 수량 표는 1인량(초등)으로 단위 g 명시 없음. 돼지고기 포함 원본과 주재료도 다름. 숫자만 g로 추정 금지.'
        if p==75:return '감자채/베이컨 두 메뉴 공유 표. 후추 약간·조리법 소금 누락. 별도 완전 KDCA 전체 조사 평균을 채택.'
        if p==72:return '미역오이초무침/미역초고추장 공유 표. 두 메뉴별 양 배분·절임 잔존량 불명. 다른 메뉴 수량 합성 금지.'
        return '계기교육·계절 식단·메뉴명 설명. 해당 음식 전체 재료별 계량 구성 없음.'
    if 'cbe.go.kr' in u:
        if n=='묵밥' and p in (8,23):return '도토리묵밥 1인당 g 확보. 조리법의 다진 김치 양이 표에서 빠짐. 원본 묵밥과 새 수량을 혼합하지 않음.'
        if n=='묵밥' and p==196:return '도토리묵·김치·육수 계량은 있으나 조리법의 밥·파뿌리 수량 없음.'
        if n=='완두콩스프' and p==100:return '완두콩 7.02g·감자 3.51g·크림스프분말 17.54g 확보. 분말 제품 identity/원재료 영양 미확정. 완성 스프 영양으로 대입 금지.'
        if n=='닭봉구이' and p==145:return '단호박닭봉구이 계량 표 확보. 닭봉 뼈 포함 가식부 수율 및 바비큐소스 제품 영양 미확정.'
        return '월 식단·목차 또는 다른 음식 레시피의 적용 식단/재료 언급. 해당 음식 전체 계량 레시피 아님.'
    if 'ydp.go.kr' in u:return '이미 완성된 느타리버섯전을 다른 피자 재료로 사용. 느타리버섯전 전체 배합 없음.'
    return '현재 원문에서 해당 음식 전체 원재료별 수량·상태·단위·영양이 함께 확보되지 않음. 검색 언급으로 publish하지 않음.'
def run():
    q=load(OUT/'new-source-review-queue.json')
    for r in q:
        raw=ROOT/r['rawFile'];assert hashlib.sha256(raw.read_bytes()).hexdigest()==r['sourceSha256']
        if not raw.read_bytes().startswith(b'%PDF'):
            d=html.fromstring(raw.read_text(encoding='utf-8',errors='replace'))
            for e in d.xpath('//script|//style'):e.drop_tree()
            t=re.sub(r'\s+',' ',d.text_content());j=t.find(r['name']);r['cleanExcerpt']=t[max(0,j):max(0,j)+2200]
        r.update(decision='NOT_PUBLISHED',reason=review(r),checkedAt='2026-10-05')
    save('new-primary-candidate-reviews.json',q)
    states={r['recipeId']:r for r in load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json')}
    bulk={r['recipeId']:r for r in load(OUT/'bulk-reindexed-candidates.json')}
    foods={r['recipeId']:r for r in load(OUT/'all-food-metadata-reindex.json')}
    receipts=collections.defaultdict(list)
    for p in OUT.glob('*search-*.json'):
        for f in load(p).get('foods',[]):receipts[f['recipeId']].append(p.name)
    ledger=[]
    for f in load(OUT/'scope-89.json'):
        s=states[f['recipeId']];done=s['appCompleteAvailable']
        prior={r['recipeId']:r for r in f['rankedReferenceAdjudications']}
        for candidate in bulk[f['recipeId']]['proposals']:
            selected=done and candidate['recipeId']==s.get('referenceId')
            candidate['identityDecision']='PUBLISHED_REVIEWED_SAME_DISH' if selected else 'NOT_PUBLISHED'
            candidate['finalReason']=('선택된 전체 구성과 동일 음식 기존 Food 연결 검증 완료.' if selected else
              prior.get(candidate['recipeId'],{}).get('reason') or
              ('전체 수량·영양 미연결: '+', '.join(candidate['unresolvedInputs']) if not candidate['complete'] else
               '검색 문자열/재료 일부 유사성은 동일 음식 근거가 아님. 명시 주재료·조리법·소스/종/상태 qualifier가 전체 일치한 구성을 확인하지 못함.'))
        f.update(appComplete=done,state=s['state'],finalDisposition=s['state'] if done else f['baselineState']['state']+'_WITH_EXHAUSTED_EVIDENCE',
          newExecutedSearchReceipts=receipts[f['recipeId']],newPrimaryCandidateReviews=[r for r in q if r['recipeId']==f['recipeId']],
          bulkReindexedCandidates=bulk[f['recipeId']],foodMetadataReindex=foods[f['recipeId']],
          selectedMapping=s if done else None,wholeInternetExhausted=False,
          exhaustedMeaning='이번 실행에서 확보·재색인한 공식/공공 자료, 세 계열 새 검색 및 기존 89개별 판정을 검토함. 미공개 자료·인터넷 전체에 근거가 없다는 의미가 아님.',
          evidenceScope='전체 89개: PDF/제조사/의료학술공공 검색, 구성 1388개, 메뉴 3250개, Food 67354개. 대구 책자 전체 100개 레시피 시각 확인.',
          newReviewStatus='ADJUDICATED',checkedAt='2026-10-05')
        if not done:
            missing=[r['ingredient']+': '+r['unitReason']+' / '+r['identityReason'] for r in f['originalUnresolved']]
            f['remainingReason']=missing
            f['nextAction']='같은 음식 전체 레시피에서 '+', '.join(r['ingredient'] for r in f['originalUnresolved'])+'의 정확한 가식부 수량·단위·생/익힘/제품 identity 확보. 다른 요리 계량과 합성하지 않음.'
        ledger.append(f)
    assert len(ledger)==89 and sum(r['appComplete'] for r in ledger)==2
    save('all-89-final-evidence-ledger.json',ledger)
    save('remaining-87-foods.json',[r for r in ledger if not r['appComplete']])
    save('new-app-complete-foods.json',[r for r in ledger if r['appComplete']])
    six=[]
    for f in load(OUT/'question-required-six-baseline.json'):
        candidates=foods[f['recipeId']]['candidates']
        f.update(finalDisposition='QUESTION_REQUIRED_NEW_FOOD',newFoodCreated=False,reindex=foods[f['recipeId']],sameExistingFoodVerified=False)
        six.append(f)
    save('question-required-six-final.json',six)
    conflicts=load(ROOT/'data-source/recipe-residual-97/current-212-conflict-review.json')
    for r in conflicts:r.update(residual89Review='새 reference는 공식 직접 g 또는 원문 조사 평균 g를 사용. 이 원본 큰술/작은술 충돌을 역해결한 것으로 세지 않음. 상태·밀도·계량 문맥 불확정.',newResolution=False)
    save('current-212-conflict-review.json',conflicts)
    save('separate-nutrition-and-source-review.json',dict(nutrition=dict(ingredient='주꾸미 먹물',status='UNRESOLVED',reason='종별 먹물 열량/근접성분 자료 미확보. 간췌장 조직 연구와 다른 두족류 먹물은 동일 영양 근거 아님.',receipts=['focused-unresolved-search-002.json','focused-unresolved-search-003.json']),source=dict(ingredient='딸기잼 원본 source',recipeId='MFDS-223',sourceUrl='https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=223',status='UNRESOLVED',reason='기관·recipe ID·원문은 확인됐으나 완성 딸기잼 81g만 제시하고 원재료 배합이 없음. 새 충북 PDF 레몬즙 1T/2스푼 충돌과 개인 출처 이미지로 원본 해결 처리하지 않음.')))
    save('final-progress.json',dict(status='PARTIAL',total=516,beforeAppComplete=421,appComplete=423,beforeRemaining=95,remaining=93,states=dict(collections.Counter(s['state'] for s in states.values())),scope89=dict(collections.Counter(r['finalDisposition'] for r in ledger)),questionRequired=6,originalIngredient=load(OUT/'baseline-original-progress.json'),assetRows=6766))
    print(json.dumps(load(OUT/'final-progress.json'),ensure_ascii=False))
if __name__=='__main__':run()
