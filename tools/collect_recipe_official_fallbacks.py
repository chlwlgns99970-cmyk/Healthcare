"""Bounded documented public sample queries; no credentials or limit bypass.

Only the documented 1/5 sample window is requested for each original dish name.
Alternative formulations remain separate from all 516 original recipe identities.
"""
import concurrent.futures,hashlib,json,re,urllib.parse,urllib.request,threading,argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-linkage-maximization';CACHE=OUT/'cookrcp-public-samples'
DOCUMENTATION='https://www.foodsafetykorea.go.kr/api/openApiInfo.do?menu_grp=MENU_GRP31&menu_no=661&show_cnt=10&start_idx=1&svc_no=COOKRCP01'
def collect(online=False):
    CACHE.mkdir(exist_ok=True)
    originals=json.loads((OUT/'recipe-final-audit.json').read_text(encoding='utf-8'))['audit']
    names=sorted({r['name'] for r in originals if not r['complete']})
    sample_limit=threading.Event()
    def fetch(name):
        url='https://openapi.foodsafetykorea.go.kr/api/sample/COOKRCP01/json/1/5/RCP_NM='+urllib.parse.quote(name)
        file=CACHE/(hashlib.sha256(url.encode()).hexdigest()[:24]+'.json')
        result=dict(query=name,url=url,checkedAt='2026-10-05',documentation=DOCUMENTATION)
        try:
            if not file.exists():
                if not online or sample_limit.is_set():
                    return result|dict(rows=[],status='NOT_REQUESTED_PUBLIC_SAMPLE_LIMIT_OR_OFFLINE')
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=20) as response:data=response.read()
                json.loads(data);file.write_bytes(data)
            data=file.read_bytes();payload=json.loads(data)
            body=payload.get('COOKRCP01',payload)
            code=body.get('RESULT',{}).get('CODE')
            if code=='INFO-310':sample_limit.set()
            result.update(sha256=hashlib.sha256(data).hexdigest(),rawFile=str(file.relative_to(ROOT)).replace('\\','/'),
                totalMatches=int(body.get('total_count') or 0),rows=body.get('row',[]),result=body.get('RESULT'),
                status='PUBLIC_SAMPLE_LIMIT' if code=='INFO-310' else 'CAPTURED')
        except Exception as e:result.update(error=str(e),rows=[])
        return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results=[]
        for i,result in enumerate(pool.map(fetch,names),1):
            results.append(result)
            if i%25==0:print(json.dumps(dict(queriesDone=i,totalQueries=len(names),rowsObserved=sum(len(x['rows']) for x in results))),flush=True)
    unique={r['RCP_SEQ']:r for response in results for r in response['rows']}
    # Preserve the earlier legitimate four-row kimbap response; later quota
    # responses never replace successfully captured facts.
    initial=OUT/'raw/d28d30732d142f3e.txt'
    if initial.exists():
        for r in json.loads(initial.read_text(encoding='utf-8'))['COOKRCP01']['row']:unique[r['RCP_SEQ']]=r
    report=dict(queryCount=len(names),sampleWindow='1/5',queries=results,uniqueRecipeCount=len(unique),recipes=list(unique.values()),originalRecipeCount=516)
    (OUT/'official-alternative-recipe-catalog.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    reasons={
        '227':['새싹채소 종류 미정','파프리카 색상 미정','저염간장 제품·제법 미정','참치 통조림 배합 및 기름 제거 후 중량 기준 미확정'],
        '229':['파프리카·피망 종류 미정','쇠고기 우둔의 등급별 nutrition 선택 근거 미정','매실청 배합·nutrition 미확정'],
        '3467':['메밀 생면·족발·달걀지단·리코타·레몬청의 배합 미정','김 2장의 가식부 중량 근거 부족','2인분의 별도 레시피로 원래 김밥 배합과 구분 필요'],
        '3197':['르뱅의 배합 미정','먹물가루의 원재료 종·nutrition 미정','닭가슴살 등 일부 연결 가능하지만 전체 배합 완전 연결 불가','김밥 모양의 빵이므로 밥 김밥 identity로 대체 불가'],
        '763':['원문 재료량이 50·15·20 등 단위 없는 숫자: 임의 g 해석 불가','모시조개 가식부 중량 및 양념 종류 근거 미확정'],
        '260':['카레·치킨스톡·밀가루 및 파프리카의 정확한 nutrition identity 미확정'],
        '606':['소갈비의 등급·뼈 포함 여부 미확정','인삼 뿌리·표고 개·대추 알 등의 가식부 중량 근거 부족','저염간장 제품·제법 미정'],
        '3065':['국간장의 정확한 제법·nutrition identity 미확정'],
    }
    reviews=[]
    for sequence,r in unique.items():
        captures=[{k:q[k] for k in ('url','sha256','rawFile') if k in q} for q in results if any(x['RCP_SEQ']==sequence for x in q['rows'])]
        if initial.exists() and any(x['RCP_SEQ']==sequence for x in json.loads(initial.read_text(encoding='utf-8'))['COOKRCP01']['row']):
            captures.append(dict(url='https://openapi.foodsafetykorea.go.kr/api/sample/COOKRCP01/json/1/5/RCP_NM=%EA%B9%80%EB%B0%A5',
                sha256=hashlib.sha256(initial.read_bytes()).hexdigest(),rawFile=str(initial.relative_to(ROOT)).replace('\\','/')))
        reviews.append(dict(recipeId='COOKRCP01-'+sequence,name=r['RCP_NM'],ingredientText=r['RCP_PARTS_DTLS'],
            instructions=[r[k] for k in sorted(r) if k.startswith('MANUAL') and not k.startswith('MANUAL_IMG') and r[k]],
            checkedAt='2026-10-05',sourceCaptures=captures,linkageStatus='INCOMPLETE_OFFICIAL_ALTERNATIVE',
            remainingReasons=reasons.get(sequence,['추가 개별 원재료·사용량 검증 필요']),published=False,countedInOriginal516=False))
    (OUT/'official-alternative-recipe-review.json').write_text(json.dumps(reviews,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(dict(queryCount=len(names),uniqueRecipeCount=len(unique),errors=sum('error' in x for x in results))),flush=True)
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--collect',action='store_true')
    collect(parser.parse_args().collect)
