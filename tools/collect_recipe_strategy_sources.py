"""Read-only official downloads with a shared URL cache and no API samples."""
import hashlib,json,sys,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-linkage-strategy';RAW=OUT/'raw'

def capture(url):
    assert '/api/sample/' not in url
    manifests=[OUT/'source-captures.json',ROOT/'data-source/recipe-linkage-residual/source-captures.json',
        ROOT/'data-source/recipe-linkage-maximization/additional-source-captures.json',ROOT/'data-source/full-adjudication/source-captures.json']
    for manifest in manifests:
        if not manifest.exists():continue
        for r in json.loads(manifest.read_text(encoding='utf-8')):
            if r.get('url')!=url:continue
            if r.get('rawFile'):
                p=ROOT/r['rawFile']
                if p.exists():return {k:v for k,v in r.items() if k not in ('text','links','scripts')}|dict(cacheReused=True)
            if manifest==OUT/'source-captures.json':return r|dict(cacheReused=True)
    result=dict(url=url,checkedAt='2026-10-05',cacheReused=False)
    try:
        request_url=url;body=None
        if '|POST|' in url:request_url,params=url.split('|POST|',1);body=params.encode('utf-8')
        with urllib.request.urlopen(urllib.request.Request(request_url,data=body,headers={'User-Agent':'Mozilla/5.0'}),timeout=45) as r:
            data=r.read();result.update(finalUrl=r.url,contentType=r.headers.get('Content-Type',''),httpStatus=r.status)
        p=RAW/hashlib.sha256(url.encode()).hexdigest()[:20];p.write_bytes(data)
        result.update(rawFile=p.relative_to(ROOT).as_posix(),sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),status='CAPTURED')
    except Exception as e:result.update(status='FAILED',error=str(e))
    return result

def run(urls):
    RAW.mkdir(parents=True,exist_ok=True);manifest=OUT/'source-captures.json'
    prior=json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else []
    index={r['url']:r for r in prior}
    for url in urls:
        r=capture(url);index[url]=r
        print(json.dumps(r,ensure_ascii=False),flush=True)
        manifest.write_text(json.dumps(list(index.values()),ensure_ascii=False,indent=2),encoding='utf-8')
if __name__=='__main__':run(sys.argv[1:])
