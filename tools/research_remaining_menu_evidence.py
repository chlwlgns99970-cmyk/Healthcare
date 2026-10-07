"""Replay public menu requests observed in official pages, without login."""
import json,hashlib
from urllib.parse import quote
from urllib.request import build_opener,HTTPCookieProcessor,Request
from http.cookiejar import CookieJar
from lxml import html
from capture_full_adjudication_sources import run,OUT,ROOT,RAW

def main():
    manifest=json.loads((OUT/'source-captures.json').read_text(encoding='utf-8'))
    urls=[]
    for c in manifest:
        if '/j_menureg|' in c['url'] and ('Mseq=51' in c['url'] or 'Mseq=52' in c['url']):
            payload=json.loads((ROOT/c['rawFile']).read_text(encoding='utf-8'))
            urls.append('https://www.yupdduk.com'+quote(payload['rows']['W_furl']))
    run(urls)
    session=build_opener(HTTPCookieProcessor(CookieJar()))
    page=session.open(Request('https://www.hsd.co.kr/menu/menu_list',headers={'User-Agent':'Mozilla/5.0'}),timeout=30)
    token=html.fromstring(page.read()).xpath('//meta[@name="_csrf"]/@content')[0]
    url='https://www.hsd.co.kr/api/menu/menu_list/3/10'
    response=session.open(Request(url,data=b'',headers={'User-Agent':'Mozilla/5.0','X-CSRF-TOKEN':token,'Content-Type':'application/json; charset=utf-8','Accept':'application/json; charset=utf-8'}),timeout=30)
    data=response.read();payload=json.loads(data)
    raw=RAW/hashlib.sha256((url+'|POST').encode()).hexdigest()[:16];raw.write_bytes(data)
    row=dict(url=url+'|POST',checkedAt='2026-10-05',status=response.status,rawFile=str(raw.relative_to(ROOT)),sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),contentType=response.headers.get('Content-Type',''))
    manifest=json.loads((OUT/'source-captures.json').read_text(encoding='utf-8'));manifest=[c for c in manifest if c['url']!=row['url']]+[row]
    (OUT/'source-captures.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Hansot public rectangle-lunchbox menu response',raw, 'bytes',row['bytes'])
    print(json.dumps(payload,ensure_ascii=False)[:4000])
if __name__=='__main__':main()
