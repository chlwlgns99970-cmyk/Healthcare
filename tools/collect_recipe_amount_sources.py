"""New amount-priority official sources, with previous captures reused."""
import sys,json,urllib.request,hashlib,http.cookiejar
from pathlib import Path
import collect_recipe_strategy_sources as prior

OUT=prior.ROOT/'data-source/recipe-amount-priority'

def kdca_download(record, sequence):
    """Normal public file download; generate the expiring token in one session."""
    manifest=OUT/'source-captures.json'
    records=json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else []
    for entry in records:
        if entry.get('recipeSourceRecord')==record and entry.get('fileSequence')==sequence and entry.get('status')=='CAPTURED':
            path=prior.ROOT/entry['rawFile']
            if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256']:
                return entry|{'cacheReused':True}
    base='https://knhanes.kdca.go.kr/knhanes'
    session=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    session.addheaders=[('User-Agent','Mozilla/5.0')]
    with session.open(base+'/main.do',timeout=45) as response:response.read()
    body=json.dumps({'type':'RS','dtlSn':record,'flSn':sequence}).encode()
    request=urllib.request.Request(base+'/api/file/download.json',data=body,headers={'Content-Type':'application/json'})
    with session.open(request,timeout=45) as response:metadata=json.loads(response.read())
    assert metadata['status']=='success'
    url=metadata['data']['downUrl']
    page=base+'/archive/wsiEtcPblcnDtl.do'
    with session.open(urllib.request.Request(url,headers={'Referer':page}),timeout=45) as response:raw=response.read()
    assert raw.startswith((b'%PDF',b'PK')), 'Reject HTML/error pages as files'
    (OUT/'raw').mkdir(parents=True,exist_ok=True)
    path=OUT/'raw'/f'kdca-{record}-{sequence}';path.write_bytes(raw)
    entry=dict(url=url,sourcePage=page,recipeSourceRecord=record,fileSequence=sequence,
        requestMode='SAME_SESSION_FRESH_DOWNLOAD_TOKEN_WITH_REFERER',rawFile=path.relative_to(prior.ROOT).as_posix(),
        bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),checkedAt='2026-10-05',status='CAPTURED')
    records.append(entry);manifest.write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return entry
def run(urls):
    old=prior.OUT
    OUT.mkdir(exist_ok=True);(OUT/'raw').mkdir(exist_ok=True)
    manifest=OUT/'source-captures.json'
    records=json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else []
    index={r['url']:r for r in records}
    for url in urls:
        if url in index:
            print(json.dumps(index[url]|{'cacheReused':True},ensure_ascii=False));continue
        # Shared earlier collector searches all previous caches first.
        prior.RAW=OUT/'raw'
        if '|JSON|' in url:
            request_url,body=url.split('|JSON|',1)
            r=dict(url=url,checkedAt='2026-10-05',cacheReused=False)
            try:
                with urllib.request.urlopen(urllib.request.Request(request_url,data=body.encode(),headers={'User-Agent':'Mozilla/5.0','Content-Type':'application/json'}),timeout=45) as response:
                    raw=response.read();r.update(finalUrl=response.url,httpStatus=response.status,contentType=response.headers.get('Content-Type',''))
                path=OUT/'raw'/hashlib.sha256(url.encode()).hexdigest()[:20];path.write_bytes(raw)
                r.update(rawFile=path.relative_to(prior.ROOT).as_posix(),sha256=hashlib.sha256(raw).hexdigest(),bytes=len(raw),status='CAPTURED')
            except Exception as e:r.update(status='FAILED',error=str(e))
        else:r=prior.capture(url)
        index[url]=r
        manifest.write_text(json.dumps(list(index.values()),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(r,ensure_ascii=False),flush=True)
if __name__=='__main__':run(sys.argv[1:])
