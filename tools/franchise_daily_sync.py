"""Official-source daily menu overlay. Never writes Food assets or app releases."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlparse, urljoin, quote, urlunparse
import argparse, concurrent.futures, hashlib, json, math, os, re, time, unicodedata
import urllib.request
import urllib.error
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / 'data-source/franchise-sync/brands.json'
SEED = ROOT / 'data-source/franchise-sync/static-identities.json'
MAX_BYTES = 2_000_000

def normalize(name):
    name=unicodedata.normalize('NFKC',name)
    name=re.sub(r'\(\s*(HOT|ICE|ICED)\s*\)',r'\1',name,flags=re.I)
    return re.sub(r'[\s,·]+', '', name).casefold()

def stable_id(brand_id, external_id, name, category):
    identity = external_id or normalize(name)+'|'+category
    return 'remote-franchise-'+hashlib.sha256((brand_id+'|'+identity).encode()).hexdigest()[:24]

def official_url(url, brand):
    parsed = urlparse(url)
    hosts = {host.encode('idna').decode().lower() for host in brand['allowedHosts'] if host}
    return parsed.scheme in ('https', 'http') and not parsed.username and not parsed.password and parsed.hostname and parsed.hostname.encode('idna').decode().lower() in hosts

def validate(menu, brand):
    assert menu['brandId'] == brand['brandId'] and menu['brand'] == brand['name']
    assert isinstance(menu['name'], str) and 0 < len(menu['name']) <= 160 and not any(ord(c)<32 for c in menu['name'])
    assert official_url(menu['sourceUrl'], brand)
    assert menu['checkedAt'] and menu['externalId'] is not None
    nutrition = [menu.get(k) for k in ('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams')]
    for value in nutrition:
        assert value is None or (isinstance(value,(int,float)) and not isinstance(value,bool) and math.isfinite(value) and 0 <= value <= 100_000)
    if any(v is not None for v in nutrition):
        assert isinstance(menu.get('servingAmount'), (int,float)) and 0 < menu['servingAmount'] <= 10_000
        assert menu.get('servingUnit') in ('g','ml','개','인분')
        assert menu.get('nutritionSourceUrl') == menu['sourceUrl']
    expected = stable_id(menu['brandId'], menu['externalId'], menu['name'], menu.get('category',''))
    assert menu['id'] == expected

class SourcePage(HTMLParser):
    """Only explicit brand selectors and schema.org MenuItem; never arbitrary page text."""
    def __init__(self, brand):
        super().__init__(convert_charrefs=True)
        self.brand=brand; self.anchor=None; self.items=[]; self.ld=False; self.ld_text=''; self.documents=[]
    def handle_starttag(self, tag, attrs):
        attrs=dict(attrs)
        if tag=='script' and attrs.get('type')=='application/ld+json': self.ld=True; self.ld_text=''
        if tag=='a':
            href=attrs.get('href','')
            adapter=self.brand['adapter']
            if adapter=='sulbing' and re.search(r'menu_view\.php\?menu=\d+',href): self.anchor=[href,'']
            elif adapter=='isungdang' and attrs.get('name','').startswith('anchorBoxName_') and href.startswith('/product/'):
                self.anchor=[href,'']
        if tag=='img' and self.anchor and self.brand['adapter']=='isungdang' and not self.anchor[1]:
            self.anchor[1]=attrs.get('alt','')
    def handle_data(self,data):
        if self.ld: self.ld_text+=data
        if self.anchor and self.brand['adapter']!='isungdang': self.anchor[1]+=data
    def handle_endtag(self,tag):
        if tag=='script' and self.ld:
            self.ld=False
            try: self.documents.append(json.loads(self.ld_text))
            except ValueError: pass
        if tag=='a' and self.anchor:
            href,name=self.anchor; self.anchor=None
            name=re.sub(r'\s+',' ',name).strip()
            if name: self.items.append((href,name))

def candidates(brand, html, checked):
    page=SourcePage(brand);page.feed(html)
    results=[]
    def item(name, external_id, url, category=''):
        if any(word in name for word in ('텀블러','머그컵','에코백','키링','굿즈','기프티콘','상품권')): return
        menu=dict(brandId=brand['brandId'],brand=brand['name'],name=name,normalizedName=normalize(name),
            externalId=external_id,category=category,sourceUrl=url,checkedAt=checked,
            energyKcal=None,carbohydrateGrams=None,proteinGrams=None,fatGrams=None,servingAmount=None,servingUnit=None)
        menu['id']=stable_id(brand['brandId'],external_id,name,category)
        validate(menu,brand);results.append(menu)
    for href,name in page.items:
        if brand['adapter']=='isungdang' and not any(word in name for word in ('빵','전병','고로케','크로와상','찹쌀떡','카스텔라','카스테라','마들렌','케이크','스콘','샌드','쿠키')):
            continue # Official shop also sells non-food goods; never register those as menus.
        url=urljoin(brand['sourceUrl'],href)
        external_id=(re.search(r'menu=(\d+)',href).group(1) if brand['adapter']=='sulbing' else re.search(r'/([0-9]+)/category/',href).group(1))
        item(name,external_id,url,'빙수' if brand['adapter']=='sulbing' else '')
    def visit(value):
        if isinstance(value,list):
            for child in value: visit(child)
        elif isinstance(value,dict):
            kind=value.get('@type')
            if kind=='MenuItem' or isinstance(kind,list) and 'MenuItem' in kind:
                name=value.get('name');url=urljoin(brand['sourceUrl'],value.get('url') or brand['sourceUrl'])
                external=str(value.get('identifier') or value.get('@id') or '')
                if isinstance(name,str) and official_url(url,brand): item(name,external,url)
            for child in value.values():
                if isinstance(child,(dict,list)): visit(child)
    for document in page.documents: visit(document)
    if brand['adapter'] not in ('jsonld','sulbing','isungdang'):
        from franchise_brand_adapters import reviewed_rows
        for name,category,url,external_id in reviewed_rows(brand,html):
            item(name,external_id,url,category)
    # Nutrition is intentionally not inferred from text or schema fragments without a reviewed basis adapter.
    return list({r['id']:r for r in results}.values())

def fetch_source(brand, source, capture_dir=None):
    brand=dict(brand,sourceUrl=source['url'],adapter=source['adapter'],sourceKey=source.get('key',''))
    if not brand.get('sourceUrl'): return dict(brandId=brand['brandId'],status='SOURCE_MISSING',menus=[])
    checked=datetime.now(timezone.utc).isoformat()
    try:
        assert official_url(brand['sourceUrl'],brand)
        parsed=urlparse(brand['sourceUrl'])
        encoded=urlunparse(parsed._replace(netloc=parsed.netloc.encode('idna').decode(),path=quote(parsed.path,safe='/%'),query=quote(parsed.query,safe='=&%')))
        request=urllib.request.Request(encoded,headers={'User-Agent':'HealthcareMenuAudit/1.0 (once-daily official-menu check)', 'Accept':'application/json,text/html;q=0.9,*/*;q=0.5'})
        with urllib.request.urlopen(request,timeout=8) as response:
            assert official_url(response.url,brand), 'unapproved redirect'
            data=response.read(MAX_BYTES+1);assert len(data)<=MAX_BYTES,'source too large'
            declared=re.search(br'charset\s*=\s*["\']?([a-zA-Z0-9_-]+)',data[:4096])
            encoding=response.headers.get_content_charset() or (declared.group(1).decode('ascii') if declared else 'utf-8')
        html=data.decode(encoding,errors='strict')
        if not html.strip(): raise ValueError('Empty official response')
        if source['adapter'] not in ('bon-api','starbucks-json') and not re.search(r'<html\b|<!doctype\b',html,re.I):
            raise ValueError('Malformed official HTML response')
        if capture_dir:
            capture_dir=Path(capture_dir);capture_dir.mkdir(parents=True,exist_ok=True)
            (capture_dir/(hashlib.sha256(brand['sourceUrl'].encode()).hexdigest()+'.txt')).write_text(html,encoding='utf-8')
        menus=candidates(brand,html,checked)
        return dict(brandId=brand['brandId'],sourceUrl=source['url'],adapter=source['adapter'],sourceKey=source.get('key',''),
            status='SUCCESS' if menus else 'REVIEW_REQUIRED',checkedAt=checked,menus=menus,htmlSha256=hashlib.sha256(html.encode()).hexdigest(),httpStatus=200)
    except Exception as error:
        return dict(brandId=brand['brandId'],sourceUrl=source['url'],adapter=source['adapter'],sourceKey=source.get('key',''),
            status='FETCH_FAILED',checkedAt=checked,menus=[],error=type(error).__name__,httpStatus=getattr(error,'code',None))

def fetch(brand, capture_dir=None):
    if not brand.get('sourceUrl'): return dict(brandId=brand['brandId'],status='SOURCE_MISSING',menus=[],sources=[])
    sources=brand.get('sources') or [dict(url=brand['sourceUrl'],adapter=brand['adapter'])]
    batches=[fetch_source(brand,source,capture_dir) for source in sources]
    if brand.get('publishPolicy')=='REVIEW_ONLY':
        for batch in batches:
            batch['withheldCandidates']=[dict(name=m['name'],id=m['id'],reason='OFFICIAL_STORE_REQUIRES_MENU_SOURCE') for m in batch['menus']]
            batch['menus']=[]
            if batch['status']=='SUCCESS': batch['status']='REVIEW_REQUIRED'
    success=any(b['status']=='SUCCESS' for b in batches)
    status='SUCCESS' if success else 'FETCH_FAILED' if all(b['status']=='FETCH_FAILED' for b in batches) else 'REVIEW_REQUIRED'
    return dict(brandId=brand['brandId'],status=status,checkedAt=max(b['checkedAt'] for b in batches),
        menus=list({m['id']:m for b in batches for m in b['menus']}.values()),sources=[{k:v for k,v in b.items() if k!='menus'} for b in batches])

def automation_state(brand,batch):
    sources=batch.get('sources',[])
    if not brand.get('sourceUrl'): return 'SOURCE_MISSING'
    if brand.get('publishPolicy')=='REVIEW_ONLY': return 'REVIEW_REQUIRED'
    if batch['status']=='SUCCESS' and all(s['status']=='SUCCESS' for s in sources): return 'AUTO_READY'
    if any(s.get('httpStatus') in (401,403,429) for s in sources): return 'SOURCE_BLOCKED'
    explicit=any(s.get('adapter')!='jsonld' for s in sources)
    if explicit and any(s['status']=='FETCH_FAILED' for s in sources): return 'BROKEN'
    return 'REVIEW_REQUIRED' if explicit else 'ADAPTER_REQUIRED'

def merge(existing, fetched, brands, static):
    by_brand={b['brandId']:b for b in brands}
    existing_menus=list(existing.get('menus',[]))
    for menu in existing_menus: validate(menu,by_brand[menu['brandId']])
    names={(r['brandId'],normalize(r['name'])) for r in static+existing_menus}
    ids={r['id'] for r in existing_menus}
    external={(r['brandId'],r.get('externalId')) for r in static+existing_menus if r.get('externalId')}
    added=[];duplicates=0;invalid=0;count=0;decisions=[]
    for batch in fetched:
        for menu in batch.get('menus',[]):
            count+=1
            try: validate(menu,by_brand[menu['brandId']])
            except (AssertionError,KeyError,TypeError,ValueError): invalid+=1;continue
            name=(menu['brandId'],normalize(menu['name'])); key=(menu['brandId'],menu.get('externalId'))
            if menu['id'] in ids or name in names or menu.get('externalId') and key in external:
                exact=any(r['brandId']==menu['brandId'] and r['name']==menu['name'] for r in static+existing_menus+added)
                decisions.append(dict(id=menu['id'],brandId=menu['brandId'],name=menu['name'],state='EXISTING' if exact or menu['id'] in ids or key in external else 'ALIAS_MATCH'))
                duplicates+=1;continue
            added.append(menu);ids.add(menu['id']);names.add(name)
            decisions.append(dict(id=menu['id'],brandId=menu['brandId'],name=menu['name'],state='NEW'))
            if menu.get('externalId'): external.add(key)
    return existing_menus+added,dict(candidateCount=count,publishedNewCount=len(added),duplicateCount=duplicates,invalidCount=invalid,
        newWithNutrition=sum(any(r.get(k) is not None for k in ('energyKcal','carbohydrateGrams','proteinGrams','fatGrams')) for r in added),
        newWithoutNutrition=sum(all(r.get(k) is None for k in ('energyKcal','carbohydrateGrams','proteinGrams','fatGrams')) for r in added),decisions=decisions)

def atomic_write(path,data):
    temporary=path.with_suffix(path.suffix+'.tmp');temporary.write_bytes(data);os.replace(temporary,path)

def run(out, run_key, brands=None, batches=None):
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    lock=out/'.sync-lock'
    try: fd=os.open(lock,os.O_CREAT|os.O_EXCL|os.O_WRONLY)
    except FileExistsError: return {'status':'RUN_LOCKED'}
    os.close(fd)
    try:
        brands=brands or json.loads(CONFIG.read_text(encoding='utf-8'))
        manifest_path=out/'latest.json';existing={}; manifest={}
        if manifest_path.exists():
            manifest=json.loads(manifest_path.read_text())
            data=(out/manifest['file']).read_bytes()
            assert hashlib.sha256(data).hexdigest()==manifest['sha256']
            existing=json.loads(data)
        report_path=out/'latest-franchise-sync-report.json'
        previous_report=json.loads(report_path.read_text(encoding='utf-8')) if report_path.exists() else {}
        if manifest.get('runKey')==run_key or previous_report.get('runId')==run_key:
            return {'status':'ALREADY_COMPLETED','runKey':run_key}
        started=datetime.now(timezone.utc).isoformat()
        if batches is None:
            with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: batches=list(pool.map(fetch,brands))
        static=json.loads(SEED.read_text(encoding='utf-8'))
        menus,counts=merge(existing,batches,brands,static)
        report=dict(runId=run_key,startedAt=started,endedAt=datetime.now(timezone.utc).isoformat(),timezone='Asia/Seoul',targetBrands=len(brands),
            successBrands=sum(b['status']=='SUCCESS' for b in batches),failedBrands=sum(b['status']=='FETCH_FAILED' for b in batches),
            sourceMissing=sum(b['status']=='SOURCE_MISSING' for b in batches),reviewRequired=sum(b['status']=='REVIEW_REQUIRED' for b in batches),
            **counts,brands=[{k:v for k,v in b.items() if k!='menus'} for b in batches])
        by_id={b['brandId']:b for b in brands}
        states=[automation_state(by_id[b['brandId']],b) for b in batches if b.get('brandId') in by_id]
        report.update(autoReadyBrands=states.count('AUTO_READY'),sourceBlockedBrands=states.count('SOURCE_BLOCKED'),
            adapterRequiredBrands=states.count('ADAPTER_REQUIRED'),brokenBrands=states.count('BROKEN'),
            reviewRequiredBrands=states.count('REVIEW_REQUIRED'),totalBrands=len(brands),finishedAt=report['endedAt'])
        successes=report['successBrands']
        if successes:
            payload=json.dumps(dict(schemaVersion=1,menus=sorted(menus,key=lambda m:m['id'])),ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
            checksum=hashlib.sha256(payload).hexdigest();filename='catalog-'+checksum+'.json'
            if manifest.get('sha256')!=checksum:
                atomic_write(out/filename,payload)
                manifest=dict(schemaVersion=1,file=filename,sha256=checksum,version=checksum,menuCount=len(menus),runKey=run_key,publishedAt=report['endedAt'])
                atomic_write(manifest_path,json.dumps(manifest,sort_keys=True).encode())
        report['catalogVersion']=manifest.get('version');report['checksum']=manifest.get('sha256')
        report['status']='PUBLISHED' if successes else 'EXISTING_CATALOG_RETAINED'
        atomic_write(out/'latest-franchise-sync-report.json',json.dumps(report,ensure_ascii=False,indent=2).encode())
        return report
    finally: lock.unlink(missing_ok=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',required=True);parser.add_argument('--run-key')
    args=parser.parse_args()
    key=args.run_key or datetime.fromtimestamp(time.time()+9*3600,timezone.utc).strftime('%Y-%m-%d')
    result=run(args.output,key)
    print(json.dumps({k:v for k,v in result.items() if k!='brands'},ensure_ascii=False))
