from pathlib import Path
import re,json
from import_franchise_quality import captured_pages
ROOT=Path(__file__).resolve().parents[1]
for c in captured_pages():
    if c['brand'] in ('스쿨푸드','신전떡볶이') and ('menu.html' in c['url'] or 'menu03' in c['url'] or 'menu06' in c['url']):
        print(c['brand'],c['url'],list(c.keys()))
        for k,v in c.items():
            if isinstance(v,str) and any(t in v for t in ('맵닭','마리','토핑')):
                for term in ('맵닭','마리','토핑'):
                    match=re.search(term,v)
                    if match:print(k,v[max(0,match.start()-100):match.end()+200])
text=(ROOT/'data-source/full-adjudication/raw/e311d27953dd736a').read_text(encoding='utf-8')
print('KAKAO',sorted(set(re.findall(r'["\x27]([^"\x27]*(?:api|profiles|posts|channels)[^"\x27]*)["\x27]',text)))[:30])
