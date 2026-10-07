"""Capture only public official evidence into the request audit directory."""
from pathlib import Path
import json,hashlib,sys
from concurrent.futures import ThreadPoolExecutor
from urllib.request import Request,urlopen
from urllib.parse import urlencode
from lxml import html
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication';RAW=OUT/'raw';RAW.mkdir(exist_ok=True)
def fetch(url):
    row={'url':url,'checkedAt':'2026-10-05'}
    try:
        request_url=url
        body=None
        if '|POST|' in url:
            request_url,parameters=url.split('|POST|',1)
            body=parameters.encode('utf-8')
        with urlopen(Request(request_url,data=body,headers={'User-Agent':'Mozilla/5.0'}),timeout=30) as response:
            data=response.read();row.update(status=response.status,finalUrl=response.url,contentType=response.headers.get('Content-Type',''))
        name=hashlib.sha256(url.encode()).hexdigest()[:16];(RAW/name).write_bytes(data)
        row.update(rawFile=str((RAW/name).relative_to(ROOT)),sha256=hashlib.sha256(data).hexdigest(),bytes=len(data))
        if 'html' in row['contentType']:
            try:text=data.decode('utf-8')
            except UnicodeDecodeError:text=data.decode('cp949',errors='replace')
            doc=html.fromstring(text);row['links']=doc.xpath('//a/@href');row['scripts']=doc.xpath('//script/@src');row['text']=text
    except Exception as e:row['error']=str(e)
    return row
def run(urls):
    manifest=OUT/'source-captures.json';old=json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else []
    with ThreadPoolExecutor(max_workers=4) as pool:
        results=list(pool.map(fetch,urls))
    byurl={r['url']:r for r in old};byurl.update({r['url']:r for r in results})
    manifest.write_text(json.dumps(list(byurl.values()),ensure_ascii=False,indent=2),encoding='utf-8')
    for r in results:print(json.dumps({k:v for k,v in r.items() if k not in ('text','links','scripts')},ensure_ascii=False))
if __name__=='__main__':run(sys.argv[1:])
