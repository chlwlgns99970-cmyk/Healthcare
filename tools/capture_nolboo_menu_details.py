from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import json,hashlib
from capture_franchise_quality_sources import fetch
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-menu-completion'
listing=json.loads((OUT/'nolboo-source-audit.json').read_text(encoding='utf-8'))
urls=[r for row in listing if row.get('status')==200 and 'ydp.redtable' in row['url'] for r in row['links'] if '/product/' in r]
urls+=["https://www.ydp.go.kr/www/selectBbsNttList.do?bbsNo=45&key=268&searchCnd=all&searchKrwd=%EA%B4%80%EA%B4%91%EC%84%B8%EC%9D%BC"]
results=[]
with ThreadPoolExecutor(max_workers=4) as pool:
    for r in pool.map(fetch,urls):
        if 'text' in r:
            name=hashlib.sha256(r['url'].encode()).hexdigest()[:16]+'.html'
            (OUT/name).write_bytes(r['text'].encode('utf-8'));r['storedFile']=name
        results.append(r)
        print(json.dumps({k:v for k,v in r.items() if k in ('url','status','error')},ensure_ascii=False),flush=True)
(OUT/'nolboo-menu-details.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
