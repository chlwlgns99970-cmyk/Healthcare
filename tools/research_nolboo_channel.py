"""Public official channel pagination, dates and historical-menu separation."""
from pathlib import Path
from datetime import datetime,timezone
import json
from capture_full_adjudication_sources import fetch
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication'
initial=json.loads((OUT/'raw/e575d2615e80367b').read_text(encoding='utf-8'))
pages=[initial];evidence=[];seen=set();posts=[]
for page_no in range(30):
    page=pages[-1];items=page.get('items',[])
    fresh=[p for p in items if p['id'] not in seen]
    if not fresh:break
    for p in fresh:
        seen.add(p['id']);posts.append(dict(id=p['id'],publishedAt=datetime.fromtimestamp(p.get('published_at',p['created_at'])/1000,tz=timezone.utc).isoformat(),
          text='\n'.join(str(c.get('v','')) for c in p.get('contents',[]) if c.get('t') in ('text','link')),url=p.get('permalink',''),media=p.get('media',[])))
    if not page.get('has_next'):break
    url='https://pf.kakao.com/rocket-web/web/profiles/_Nxaxmxad/posts?since='+str(items[-1]['sort'])+'&includePinnedPost=false'
    capture=fetch(url);evidence.append(capture)
    if 'rawFile' not in capture:break
    pages.append(json.loads((ROOT/capture['rawFile']).read_text(encoding='utf-8')))
else:raise RuntimeError('Pagination safety limit reached; cannot claim complete')
result=dict(checkedAt='2026-10-05',pageCount=len(pages),posts=posts,requests=evidence,hasNext=pages[-1].get('has_next'),
 currentBrandWideMenusVerified=0,reason='최신 공지 날짜와 현재 판매 여부가 다름; 과거 출시/행사 공지를 현재 전체 메뉴로 승격하지 않음')
(OUT/'nolboo-official-channel-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print('pages',len(pages),'posts',len(posts),'hasNext',result['hasNext'])
for p in posts:
    if '부대' in p['text']:print(p['id'],p['publishedAt'],p['text'][:250].replace('\n',' '))
