"""Official-source daily menu overlay. Never writes Food assets or app releases."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlparse, urljoin, quote, urlunparse, urlencode
import argparse, concurrent.futures, hashlib, json, math, os, re, time, unicodedata
import subprocess, shutil, copy
from types import FunctionType
import urllib.request
import urllib.error
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / 'data-source/franchise-sync/brands.json'
SEED = ROOT / 'data-source/franchise-sync/static-identities.json'
MAX_BYTES = 2_000_000
MAX_CATALOG_BYTES = 8_000_000
SOURCE_TYPES = {'API','JSON','HTML','BROWSER','PDF','XLSX','CSV','HYBRID'}
LEGACY_PROVENANCE = ROOT / 'data-source/franchise-sync/legacy-source-types.json'
DEFERRED_BRANDS = frozenset(('청년피자', '놀부부대찌개', '탕화쿵푸', '채선당', '국대떡볶이'))

def update_targets(brands, healthy_only=False, names=None):
    """Scope acquisition only; never filter already verified catalog rows."""
    selected = [b for b in brands if not healthy_only or b['name'] not in DEFERRED_BRANDS]
    if names is not None:
        assert names <= {b['name'] for b in selected}, 'Unknown or deferred verification brand'
        selected = [b for b in selected if b['name'] in names]
    return {b['brandId'] for b in selected}

def source_type(source):
    if source.get('transport') == 'browser': return 'BROWSER'
    adapter=source.get('adapter','')
    if adapter.endswith('-api'): return 'API'
    if adapter.endswith('-json'): return 'JSON'
    return 'HTML'

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
    assert 'sourceType' not in menu or menu['sourceType'] in SOURCE_TYPES
    assert menu['brandId'] == brand['brandId'] and menu['brand'] == brand['name']
    assert isinstance(menu['name'], str) and 0 < len(menu['name']) <= 160 and not any(ord(c)<32 for c in menu['name'])
    assert not re.search(r'<\s*/?\s*[a-zA-Z!][^>]*>',menu['name']), 'HTML garbage menu name'
    assert official_url(menu['sourceUrl'], brand)
    if menu.get('collectionSourceUrl'):
        assert official_url(menu['collectionSourceUrl'],brand)
        assert menu['sourceUrl']==brand.get('provenanceAuthorityUrl')
    assert menu['checkedAt'] and menu['externalId'] is not None
    assert datetime.fromisoformat(menu['checkedAt'].replace('Z','+00:00')).utcoffset() is not None
    nutrition = [menu.get(k) for k in ('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams')]
    for value in nutrition:
        assert value is None or (isinstance(value,(int,float)) and not isinstance(value,bool) and math.isfinite(value) and 0 <= value <= 100_000)
    if any(v is not None for v in nutrition):
        assert isinstance(menu.get('servingAmount'), (int,float)) and 0 < menu['servingAmount'] <= 10_000
        assert menu.get('servingUnit') in ('g','ml','개','인분')
        assert menu.get('nutritionSourceUrl') == menu['sourceUrl']
    expected = stable_id(menu['brandId'], menu['externalId'], menu['name'], menu.get('category',''))
    assert menu['id'] == expected
    if menu.get('nutritionStatus'):
        from franchise_nutrition import STATUSES, validate_nutrition
        assert menu['nutritionStatus'] in STATUSES
        if menu.get('officialNutrition'):
            validate_nutrition(menu['officialNutrition'], brand['allowedHosts'])

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
        # The official Banolim product schema contains priced pizza + merchandise bundles.
        # Keep that actual food product; standalone merchandise/coupons remain excluded.
        pizza_bundle = brand['name']=='반올림피자' and brand['adapter']=='banolim-native' and re.fullmatch(r'.+ 세트\([^()]*피자\+굿즈\)',name)
        if not pizza_bundle and any(word in name for word in ('텀블러','머그컵','에코백','키링','굿즈','기프티콘','상품권')): return
        menu=dict(brandId=brand['brandId'],brand=brand['name'],name=name,normalizedName=normalize(name),
            externalId=external_id,category=category,sourceUrl=url,checkedAt=checked,sourceType=brand.get('sourceType',source_type(brand)),
            energyKcal=None,carbohydrateGrams=None,proteinGrams=None,fatGrams=None,servingAmount=None,servingUnit=None)
        if brand.get('provenanceAuthorityUrl'):
            menu['collectionSourceUrl']=url
            menu['sourceUrl']=brand['provenanceAuthorityUrl']
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
    brand=dict(brand,sourceUrl=source['url'],adapter=source['adapter'],sourceKey=source.get('key',''),sourceType=source_type(source))
    if not brand.get('sourceUrl'): return dict(brandId=brand['brandId'],status='SOURCE_MISSING',menus=[])
    checked=datetime.now(timezone.utc).isoformat()
    try:
        assert official_url(brand['sourceUrl'],brand)
        parsed=urlparse(brand['sourceUrl'])
        encoded=urlunparse(parsed._replace(netloc=parsed.netloc.encode('idna').decode(),path=quote(parsed.path,safe='/%'),query=quote(parsed.query,safe='=&%+')))
        method=source.get('method','GET')
        assert method=='GET' or method=='POST' and source['adapter'] in ('hansot-json','ediya-fragment','kfc-json','burgerking-json','twosome-categories-json','twosome-menu-json')
        body=None;headers={'User-Agent':'HealthcareMenuAudit/1.0 (once-daily official-menu check)', 'Accept':'application/json,text/html;q=0.9,*/*;q=0.5'}
        if method=='POST':body=b''
        if source['adapter'] in ('twosome-categories-json','twosome-menu-json'):
            assert brand['name']=='투썸플레이스' and method=='POST' and parsed.hostname=='mo.twosome.co.kr'
            form=source['form']
            if source['adapter']=='twosome-categories-json':assert parsed.path=='/mn/menuInfoMidListAjax.json' and set(form)=={'grtCd'}
            else:assert parsed.path=='/mn/menuInfoListAjax.json' and set(form)=={'pageNum','grtCd','midCd'} and form['pageNum']=='1' and (form['midCd']=='NEW' or form['midCd']=='' or form['midCd'].isdigit())
            assert form['grtCd'] in ('NEW','1','2','3','4','5')
            body=urlencode(form).encode();headers['Content-Type']='application/x-www-form-urlencoded'
        if source['adapter']=='burgerking-json':
            assert method=='POST' and brand['name']=='버거킹' and source['url']=='https://web-prd.burgerking.co.kr/burgerking/BKR0632.json'
            message=dict(header=dict(result=True,error_code='',error_text='',info_text='',message_version='',login_session_id='',trcode='BKR0632'),body=dict(menuKeywordList=[]))
            body=urlencode(dict(message=json.dumps(message,separators=(',',':')))).encode()
            headers['Content-Type']='application/x-www-form-urlencoded; charset=UTF-8'
        if source['adapter']=='kfc-json':
            assert brand['name']=='KFC' and method=='POST'
            form=source['form'];assert set(form)=={'product_ordertype','delivery_subGroupCd'}
            assert form['product_ordertype']=='D' and form['delivery_subGroupCd'] in ('RCMD','CHKN','BEGR','SIDE','DRNK')
            assert form['delivery_subGroupCd']==source['key']
            body=urlencode(form).encode();headers['Content-Type']='application/x-www-form-urlencoded';headers['Accept-Language']='ko-KR'
        request=urllib.request.Request(encoded,method=method,data=body,headers=headers)
        tls_evidence=None;browser_evidence=None
        if source.get('transport')=='browser':
            from franchise_browser import render
            html,browser_evidence=render(brand,source)
            data=html.encode('utf-8');encoding='utf-8'
        elif source.get('transport')=='default-jvm':
            assert brand['name']=='노랑통닭' and method=='GET'
            java=shutil.which('java')
            if not java and os.environ.get('JAVA_HOME'):
                java=str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java'))
            assert java and Path(java).is_file(),'Default JVM runtime unavailable'
            result=subprocess.run([java,str(ROOT/'tools/FranchiseOfficialTlsFetch.java'),encoded],capture_output=True,timeout=25,check=True)
            data=result.stdout;assert 0<len(data)<=MAX_BYTES
            declared=re.search(br'charset\s*=\s*["\']?([a-zA-Z0-9_-]+)',data[:4096])
            encoding=declared.group(1).decode('ascii') if declared else 'utf-8'
            tls_evidence=result.stderr.decode('utf-8').strip()
            assert tls_evidence.startswith('DEFAULT_JVM_TLS status=200 cipher=')
        else:
            with urllib.request.urlopen(request,timeout=8) as response:
                assert official_url(response.url,brand), 'unapproved redirect'
                source_limit=brand.get('maxSourceBytes',MAX_BYTES)
                assert isinstance(source_limit,int) and MAX_BYTES<=source_limit<=8_000_000
                data=response.read(source_limit+1);assert len(data)<=source_limit,'source too large'
                declared=re.search(br'charset\s*=\s*["\']?([a-zA-Z0-9_-]+)',data[:4096])
                encoding=response.headers.get_content_charset() or (declared.group(1).decode('ascii') if declared else 'utf-8')
        html=data.decode(encoding,errors='strict')
        if source['adapter']=='reviewed-image-labels':
            from franchise_menu_images import enrich
            html=enrich(dict(brand, adapter=source['adapter'], sourceUrl=source['url']),html)
        if not html.strip(): raise ValueError('Empty official response')
        diagnostics=dict(responseBytes=len(data))
        if re.search(r'<html\b|<!doctype\b',html,re.I):
            from franchise_source_diagnostics import public_response_diagnostics
            diagnostics=public_response_diagnostics(html,len(data))
        if source['adapter'] not in ('bon-api','starbucks-json','hansot-json','mega-fragment','bhc-json','bhc-categories','mc-categories','mc-index','mc-json','ediya-fragment','kfc-json','paris-fragment','bbq-json','bbq-categories','burgerking-json','pizzahut-json','hansot-browser-json','pizzahut-complete-json','twosome-categories-json','twosome-menu-json') and not re.search(r'<html\b|<!doctype\b',html,re.I):
            raise ValueError('Malformed official HTML response')
        if capture_dir:
            capture_dir=Path(capture_dir);capture_dir.mkdir(parents=True,exist_ok=True)
            (capture_dir/(hashlib.sha256(source_identity(source).encode()).hexdigest()+'.txt')).write_text(html,encoding='utf-8')
        if diagnostics.get('officialTrafficLimit'):
            return dict(brandId=brand['brandId'],sourceUrl=source['url'],adapter=source['adapter'],
                sourceKey=source.get('key',''),status='REVIEW_REQUIRED',menus=[],checkedAt=checked,
                sourceType=source_type(source),httpStatus=200,accessBlocked=True,
                error='OfficialTrafficLimit',errorReason='공식 사이트의 트래픽 한도 안내 응답입니다. 제한을 우회하지 않고 보류합니다.',
                sourceDiagnostics=diagnostics)
        menus=candidates(brand,html,checked)
        from franchise_pagination import additional_pages
        next_pages=additional_pages(brand,source,html)
        from franchise_pagination import confirmed_empty_source
        confirmed_empty=confirmed_empty_source(brand,source,html)
        # Keep unresolved official sources distinguishable from an empty menu.
        # These diagnostics do not authorize partial coverage or invent products.
        empty_reason=None
        if not menus and not confirmed_empty:
            if brand['name']=='청년피자' and source['adapter']=='youngman-native' and diagnostics.get('hasBody') is False:
                empty_reason='공식 목록은 HTTP 200이지만 body와 메뉴 본문이 없습니다. 전체 메뉴 수집 완료로 처리하지 않습니다.'
            elif brand['name'] in ('탕화쿵푸','채선당','국대떡볶이'):
                empty_reason=brand.get('reviewReason')
        coverage_refs=[]
        if brand['name']=='노랑통닭' and source['adapter']=='norang-list' and urlparse(source['url']).path=='/menu/best.html':
            from lxml import html as dom
            from urllib.parse import parse_qs
            root=dom.fromstring(re.sub(r'<!--.*?-->','',html,flags=re.S))
            for href in root.xpath('//div[@class="slider-for3"]//a[contains(@href,"chicken_view.html")]/@href'):
                code=parse_qs(urlparse(href).query).get('p_no',[''])[0]
                assert code.isdigit(),'Official best-menu reference changed'
                coverage_refs.append(code)
            assert coverage_refs,'Official best-menu references disappeared'
        return dict(brandId=brand['brandId'],sourceUrl=source['url'],adapter=source['adapter'],sourceKey=source.get('key',''),
            status='SUCCESS' if menus or confirmed_empty or coverage_refs or source['adapter']=='twosome-categories-json' or source['adapter'] in ('bhc-categories','mc-categories','mc-index','bbq-categories','starbucks-navigation','twosome-navigation') and next_pages else 'REVIEW_REQUIRED' if source['adapter']=='jsonld' else 'FAIL',checkedAt=checked,menus=menus,htmlSha256=hashlib.sha256(html.encode()).hexdigest(),httpStatus=200,
            coverageReferenceIds=coverage_refs,
            sourceDiagnostics=diagnostics,
            **({'errorReason':empty_reason} if empty_reason else {}),
            confirmedEmpty=confirmed_empty,tlsEvidence=tls_evidence,
            browserEvidence=browser_evidence,sourceType=source_type(source),
            captureKey=hashlib.sha256(source_identity(source).encode()).hexdigest(),
            discoveredPages=next_pages)
    except Exception as error:
        return dict(brandId=brand['brandId'],sourceUrl=source['url'],adapter=source['adapter'],sourceKey=source.get('key',''),
            status='REVIEW_REQUIRED' if getattr(error,'review_required',False) else 'FETCH_FAILED',checkedAt=checked,sourceType=source_type(source),menus=[],error=type(error).__name__,errorReason=str(error)[:200],httpStatus=getattr(error,'code',None))

def source_identity(source):
    return source['url']+(('|'+json.dumps(source['form'],sort_keys=True)) if source.get('form') else '')

def fetch(brand, capture_dir=None):
    try:
        if any(s.get('transport')=='browser' for s in brand.get('sources',[])):
            from franchise_browser import brand_browser_session
            with brand_browser_session():return _fetch(brand,capture_dir)
        return _fetch(brand,capture_dir)
    except Exception as error:
        return dict(brandId=brand['brandId'],status='FETCH_FAILED',checkedAt=datetime.now(timezone.utc).isoformat(),menus=[],sources=[],warnings=['BRAND_FAILURE_ISOLATED'],error=type(error).__name__)

def _fetch(brand, capture_dir=None):
    if not brand.get('sourceUrl'): return dict(brandId=brand['brandId'],status='SOURCE_MISSING',menus=[],sources=[])
    sources=brand.get('sources') or [dict(url=brand['sourceUrl'],adapter=brand['adapter'])]
    batches=[];seen=set();queue=list(sources)
    while queue:
        source=queue.pop(0)
        identity=source_identity(source)
        if identity in seen:continue
        if len(seen)>=256:
            return dict(brandId=brand['brandId'],status='FETCH_FAILED',menus=[],sources=batches,
                checkedAt=datetime.now(timezone.utc).isoformat(),warnings=['SOURCE_TRAVERSAL_LIMIT'])
        seen.add(identity)
        batch=fetch_source(brand,source,capture_dir)
        reason=batch.get('errorReason','').lower()
        retryable=batch.get('httpStatus') in (500,502,503,504) or batch.get('error') in ('TimeoutError','ConnectionResetError') or batch.get('error')=='URLError' and any(t in reason for t in ('timed out','reset','temporary failure in name resolution')) or batch.get('error')=='Error' and any(t in reason for t in ('net::err_connection_reset','net::err_connection_timed_out'))
        if retryable:
            time.sleep(0.5);batch=fetch_source(brand,source,capture_dir);batch['attempts']=2
        else:batch['attempts']=1
        queue+=batch.pop('discoveredPages',[]);batches.append(batch)
    external_ids={m['externalId'] for batch in batches for m in batch['menus']}
    for batch in batches:
        if not set(batch.get('coverageReferenceIds',[]))<=external_ids:
            batch['status']='FAIL';batch['errorReason']='Best-menu ID not present in complete menu tabs'
    observed_ids={m['id'] for batch in batches for m in batch['menus']}
    if brand.get('publishPolicy')=='REVIEW_ONLY':
        for batch in batches:
            batch['withheldCandidates']=[dict(name=m['name'],id=m['id'],reason=brand.get('reviewReason','OFFICIAL_STORE_REQUIRES_MENU_SOURCE')) for m in batch['menus']]
            batch['menus']=[]
            if batch['status']=='SUCCESS': batch['status']='REVIEW_REQUIRED'
    success=any(b['status']=='SUCCESS' for b in batches)
    status='SUCCESS' if batches and all(b['status']=='SUCCESS' for b in batches) else 'FETCH_FAILED' if any(b['status'] in ('FETCH_FAILED','FAIL') for b in batches) else 'REVIEW_REQUIRED'
    menus=list({m['id']:m for b in batches for m in b['menus']}.values())
    warnings=[];observed_count=len(observed_ids)
    if any(b['status']!='SUCCESS' for b in batches):
        # A missing tab/page cannot authorize an incomplete new publication.
        warnings.append('INCOMPLETE_SOURCE_COVERAGE_WITHHELD');menus=[]
    expected=brand.get('expectedMenuCount')
    if expected is not None:
        assert isinstance(expected,int) and expected>0
        if len(menus)>max(expected*3,expected+50):
            warnings.append('MENU_COUNT_SPIKE_REVIEW');status='REVIEW_REQUIRED';menus=[]
        elif len(menus)<expected*0.5:warnings.append('MENU_COUNT_DROP_EXISTING_RETAINED')
    return dict(brandId=brand['brandId'],status=status,checkedAt=max(b['checkedAt'] for b in batches),
        menuCount=len(menus),observedMenuCount=observed_count,warnings=warnings,
        menus=menus,sources=[{k:v for k,v in b.items() if k!='menus'} for b in batches])

def automation_state(brand,batch):
    sources=batch.get('sources',[])
    if not brand.get('sourceUrl'): return 'SOURCE_MISSING'
    if brand.get('publishPolicy')=='REVIEW_ONLY': return 'REVIEW_REQUIRED'
    if 'MENU_COUNT_SPIKE_REVIEW' in batch.get('warnings',[]):return 'REVIEW_REQUIRED'
    if batch['status']=='SUCCESS' and all(s['status']=='SUCCESS' for s in sources): return 'AUTO_READY'
    if any(s.get('accessBlocked') or s.get('httpStatus') in (401,403,429) for s in sources): return 'SOURCE_BLOCKED'
    explicit=any(s.get('adapter')!='jsonld' for s in sources)
    if explicit and any(s['status']=='FETCH_FAILED' for s in sources): return 'BROKEN'
    return 'REVIEW_REQUIRED' if explicit else 'ADAPTER_REQUIRED'

def merge(existing, fetched, brands, static):
    by_brand={b['brandId']:b for b in brands}
    existing_menus=[dict(m) for m in existing.get('menus',[])]
    provenance={(r['brandId'],r['checkedAt']):r for r in json.loads(LEGACY_PROVENANCE.read_text(encoding='utf-8'))} if LEGACY_PROVENANCE.exists() else {}
    for menu in existing_menus:
        if 'sourceType' not in menu:
            evidence=provenance.get((menu['brandId'],menu['checkedAt']))
            if evidence: menu['sourceType']=evidence['sourceType']
    for menu in existing_menus: validate(menu,by_brand[menu['brandId']])
    names={(r['brandId'],normalize(r['name'])) for r in static+existing_menus}
    ids={r['id'] for r in existing_menus}
    external={(r['brandId'],r.get('externalId')) for r in static+existing_menus if r.get('externalId')}
    exact_names={(r['brandId'],r['name']) for r in static+existing_menus}
    menu_names={}
    for row in existing_menus:menu_names.setdefault((row['brandId'],normalize(row['name'])),[]).append(row)
    by_id={r['id']:r for r in existing_menus}
    added=[];duplicates=0;invalid=0;count=0;decisions=[];identity_reviews=0;changes=0;missing=0
    for batch in fetched:
        if batch.get('status') not in (None,'SUCCESS') or any(s.get('status')!='SUCCESS' for s in batch.get('sources',[])):
            continue
        for menu in batch.get('menus',[]):
            count+=1
            try: validate(menu,by_brand[menu['brandId']])
            except (AssertionError,KeyError,TypeError,ValueError): invalid+=1;continue
            name=(menu['brandId'],normalize(menu['name'])); key=(menu['brandId'],menu.get('externalId'))
            old=by_id.get(menu['id'])
            if old is not None:
                # A repeated ID is not proof that its product/size/basis stayed
                # the same. Missing parser values are not evidence of deletion.
                fields=('name','category','externalId','sourceUrl','servingAmount','servingUnit',
                    'energyKcal','carbohydrateGrams','proteinGrams','fatGrams','sodiumMilligrams',
                    'imageUrl','imageSha256','saleState')
                changed=[k for k in fields if menu.get(k) is not None and old.get(k) is not None
                         and menu[k]!=old[k]]
                old_fact=old.get('officialNutrition') or {}; new_fact=menu.get('officialNutrition') or {}
                fact_fields=('energyKcal','carbohydrateGrams','proteinGrams','fatGrams','sodiumMilligrams',
                    'sugarsGrams','saturatedFatGrams','servingAmount','servingUnit','officialProductId')
                fact_changes=[k for k in fact_fields if old_fact.get(k) is not None and new_fact.get(k) is not None
                              and old_fact[k]!=new_fact[k]]
                changed+=['officialNutrition.'+k for k in fact_changes]
                if changed:
                    changes+=1
                    decisions.append(dict(id=old['id'],brandId=old['brandId'],name=old['name'],
                        state='REVIEW_REQUIRED',reason='EXISTING_PRODUCT_CHANGED',changedFields=changed,
                        previous={k:(old_fact[k.split('.')[1]] if '.' in k else old[k]) for k in changed},
                        observed={k:(new_fact[k.split('.')[1]] if '.' in k else menu[k]) for k in changed}))
                    continue
            matches=menu_names.get(name,[])
            if menu.get('externalId') and matches and menu['id'] not in ids and key not in external and any(r.get('externalId') and r['externalId']!=menu['externalId'] for r in matches):
                identity_reviews+=1
                decisions.append(dict(id=menu['id'],brandId=menu['brandId'],name=menu['name'],state='REVIEW_REQUIRED',reason='SAME_DISPLAY_NAME_DIFFERENT_OFFICIAL_ID'))
                continue
            if menu['id'] in ids or name in names or menu.get('externalId') and key in external:
                exact=(menu['brandId'],menu['name']) in exact_names
                decisions.append(dict(id=menu['id'],brandId=menu['brandId'],name=menu['name'],state='EXISTING' if exact or menu['id'] in ids or key in external else 'ALIAS_MATCH'))
                duplicates+=1;continue
            added.append(menu);ids.add(menu['id']);names.add(name);by_id[menu['id']]=menu
            menu_names.setdefault(name,[]).append(menu);exact_names.add((menu['brandId'],menu['name']))
            decisions.append(dict(id=menu['id'],brandId=menu['brandId'],name=menu['name'],state='NEW'))
            if menu.get('externalId'): external.add(key)
        if batch.get('status')=='SUCCESS' and batch.get('brandId'):
            observed={m['id'] for m in batch.get('menus',[])}
            for old in existing_menus:
                if old['brandId']==batch['brandId'] and old['id'] not in observed:
                    missing+=1
                    decisions.append(dict(id=old['id'],brandId=old['brandId'],name=old['name'],
                        state='REVIEW_REQUIRED',reason='NOT_OBSERVED_EXISTING_RETAINED'))
    return existing_menus+added,dict(candidateCount=count,publishedNewCount=len(added),duplicateCount=duplicates,invalidCount=invalid,identityReviewCount=identity_reviews,
        changeReviewCount=changes,availabilityReviewCount=missing,
        newWithNutrition=sum(any(r.get(k) is not None for k in ('energyKcal','carbohydrateGrams','proteinGrams','fatGrams')) for r in added),
        newWithoutNutrition=sum(all(r.get(k) is None for k in ('energyKcal','carbohydrateGrams','proteinGrams','fatGrams')) for r in added),decisions=decisions)

def atomic_write(path,data):
    temporary=path.with_suffix(path.suffix+'.tmp');temporary.write_bytes(data);os.replace(temporary,path)

def published_menu(menu):
    """Keep nutrition values/source proof; duplicate renderer proof is in audit."""
    if not menu.get('officialNutrition'):
        return menu
    return dict(menu, officialNutrition={k:v for k,v in menu['officialNutrition'].items()
                if v is not None and k not in ('optionalFieldEvidence', 'retainedFieldEvidence',
                    'sourceType', 'listSourceURL', 'listSourceSha256', 'officialDetailFullName',
                    'listingDisplayName', 'groupCrosswalkEvidence')})

def daily_nutrition(menus, live_fetch, target_brand_ids=None):
    """Seed the complete range before live acquisition; fail before publication."""
    from franchise_nutrition import REGISTRY, load_reviewed_nutrition, refresh, FIELDS, OPTIONAL_FIELDS
    original = copy.deepcopy(menus)
    registry = json.loads(REGISTRY.read_text(encoding='utf-8')) if REGISTRY.exists() else []
    seeded, replay = load_reviewed_nutrition(menus, registry)
    if any('differences' in row for row in replay):
        raise ValueError('Reviewed nutrition conflicts with retained catalog facts')
    if replay:
        # Android's previous catalog deliberately omits audit dictionaries.
        # Reconstruct only equal-field proof from the validated replay, even
        # when no numeric NULL remains to fill on a repeated daily run.
        proof_inputs = copy.deepcopy(menus)
        for menu in proof_inputs:
            menu.pop('officialNutrition', None)
        proof_menus, _ = load_reviewed_nutrition(proof_inputs, registry)
        for menu, proof_menu in zip(seeded, proof_menus):
            fact = menu.get('officialNutrition')
            proof = proof_menu.get('officialNutrition') or {}
            if not fact:
                continue
            for key in ('optionalFieldEvidence', 'retainedFieldEvidence'):
                for field, evidence in proof.get(key, {}).items():
                    if fact.get(field) == proof.get(field):
                        fact.setdefault(key, {}).setdefault(field, copy.deepcopy(evidence))
    audit = {}
    refreshed = seeded
    if live_fetch and REGISTRY.exists():
        if target_brand_ids is None:
            refreshed, audit = refresh(seeded)
        else:
            selected = [m for m in seeded if m['brandId'] in target_brand_ids]
            selected_registry = [b for b in registry if b['brandId'] in target_brand_ids]
            # Refresh also validates the complete replay index. Bind that
            # validation to the full policy registry, while its acquisition
            # registry and menu range remain target-only. A private globals
            # copy avoids changing the shared helper or any module globals.
            scoped_globals = dict(refresh.__globals__)
            scoped_globals['load_reviewed_nutrition'] = lambda menus, policies: load_reviewed_nutrition(menus, registry)
            scoped_refresh = FunctionType(refresh.__code__, scoped_globals,
                                          refresh.__name__, refresh.__defaults__, refresh.__closure__)
            rows, audit = scoped_refresh(selected, registry=selected_registry)
            assert [m['id'] for m in rows] == [m['id'] for m in selected], 'Refresh identity range changed'
            by_id = {m['id']: m for m in rows}
            refreshed = [by_id.get(m['id'], m) for m in seeded]
    assert [m['id'] for m in refreshed] == [m['id'] for m in original], 'Nutrition identity range changed'
    # Live parsers can omit proof for previously known equal/retained values.
    # Preserve that reusable evidence without changing primary live provenance.
    for old, menu in zip(seeded, refreshed):
        fact = menu.get('officialNutrition')
        if not fact:
            continue
        for key in ('optionalFieldEvidence', 'retainedFieldEvidence'):
            for field, evidence in (old.get('officialNutrition') or {}).get(key, {}).items():
                if fact.get(field) == old['officialNutrition'].get(field):
                    fact.setdefault(key, {}).setdefault(field, copy.deepcopy(evidence))
    fields = FIELDS + OPTIONAL_FIELDS
    old_facts = {m['id']: m.get('officialNutrition') or {} for m in original}
    linked = lambda fact: any(fact.get(k) is not None for k in FIELDS)
    audit.update(reviewedReplay=replay,
        reviewedReplayAppliedMenus=sum(any(old_facts[m['id']].get(k) is None and
            (m.get('officialNutrition') or {}).get(k) is not None for k in fields) for m in seeded),
        newlyVerifiedMenus=sum(not linked(old_facts[m['id']]) and linked(m.get('officialNutrition') or {}) for m in refreshed),
        nutritionChangedMenus=sum(linked(old_facts[m['id']]) and any(old_facts[m['id']].get(k) !=
            (m.get('officialNutrition') or {}).get(k) for k in fields + ('servingAmount', 'servingUnit')) for m in refreshed))
    audit['conflicts'] = [row for row in audit.get('conflicts', []) if 'differences' in row]
    audit['conflictCount'] = len(audit['conflicts'])
    evidence_by_id = {row['id']: copy.deepcopy(row) for row in audit.get('menus', [])}
    audit['menus'] = []
    for menu in refreshed:
        row = evidence_by_id.get(menu['id'], dict(id=menu['id'], brandId=menu['brandId']))
        if menu.get('officialNutrition'):
            row['officialNutrition'] = copy.deepcopy(menu['officialNutrition'])
            for key in ('optionalFieldEvidence', 'retainedFieldEvidence'):
                if key in menu['officialNutrition']:
                    row[key] = copy.deepcopy(menu['officialNutrition'][key])
        audit['menus'].append(row)
    return refreshed, audit

def seed_verified_catalog(existing, path, brands):
    """Use the checksum-validated app baseline without replacing remote facts.

    This is explicit input, not an unverified discovery or a publication action.
    Nutrition patch replay below supplies reviewed NULL-only enrichment.
    """
    path=Path(path)
    payload=path.read_bytes()
    manifest=json.loads(path.with_name('catalog-manifest.json').read_bytes())
    assert manifest['schemaVersion']==1 and hashlib.sha256(payload).hexdigest()==manifest['sha256']
    baseline=json.loads(payload)
    assert baseline['schemaVersion']==1 and len(baseline['menus'])==manifest['menuCount']
    assert len({m['id'] for m in baseline['menus']})==len(baseline['menus'])
    merged,audit=merge(existing,[dict(menus=baseline['menus'])],brands,[])
    return dict(schemaVersion=1,menus=merged),audit

def run(out, run_key, brands=None, batches=None, target_brand_ids=None, baseline_catalog=None):
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
        state_path=out/'latest-franchise-brand-status.json'
        previous_state=json.loads(state_path.read_text(encoding='utf-8')) if state_path.exists() else previous_report.get('brands',[])
        if manifest.get('runKey')==run_key or previous_report.get('runId')==run_key:
            return {'status':'ALREADY_COMPLETED','runKey':run_key}
        baseline_audit=None
        if baseline_catalog is not None:
            existing,baseline_audit=seed_verified_catalog(existing,baseline_catalog,brands)
        started=datetime.now(timezone.utc).isoformat()
        live_fetch = batches is None
        if batches is None:
            selected=[b for b in brands if target_brand_ids is None or b['brandId'] in target_brand_ids]
            assert selected
            with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: batches=list(pool.map(fetch,selected))
        static=json.loads(SEED.read_text(encoding='utf-8'))
        menus,counts=merge(existing,batches,brands,static)
        menus,nutrition_audit=daily_nutrition(menus,live_fetch,target_brand_ids)
        previous_ids={m['id'] for m in existing.get('menus',[])}
        new_menus=[m for m in menus if m['id'] not in previous_ids]
        counts['newWithNutrition']=sum(any(m.get('officialNutrition',{}).get(k) is not None for k in ('energyKcal','carbohydrateGrams','proteinGrams','fatGrams','sodiumMilligrams')) for m in new_menus)
        counts['newWithoutNutrition']=len(new_menus)-counts['newWithNutrition']
        report=dict(runId=run_key,triggerType=os.environ.get('GITHUB_EVENT_NAME','local'),startedAt=started,endedAt=datetime.now(timezone.utc).isoformat(),timezone='Asia/Seoul',
            dateKst=datetime.fromtimestamp(time.time()+9*3600,timezone.utc).date().isoformat(),
            scope=('ACTIVE_BRANDS' if target_brand_ids==update_targets(brands,True) else 'TARGETED_VERIFICATION') if target_brand_ids is not None else 'FULL',registryBrands=len(brands),
            targetBrands=len(target_brand_ids) if target_brand_ids is not None else len(brands),
            successBrands=sum(b['status']=='SUCCESS' for b in batches),failedBrands=sum(b['status']=='FETCH_FAILED' for b in batches),
            sourceMissing=sum(b['status']=='SOURCE_MISSING' for b in batches),reviewRequired=sum(b['status']=='REVIEW_REQUIRED' for b in batches),
            **counts,brands=[{k:v for k,v in b.items() if k!='menus'} for b in batches])
        if nutrition_audit:
            report['nutritionAudit']={k:v for k,v in nutrition_audit.items() if k!='menus'}
        if baseline_audit is not None:
            report['baselineAudit']=baseline_audit
        by_id={b['brandId']:b for b in brands}
        states=[automation_state(by_id[b['brandId']],b) for b in batches if b.get('brandId') in by_id]
        report.update(autoReadyBrands=states.count('AUTO_READY'),sourceBlockedBrands=states.count('SOURCE_BLOCKED'),
            adapterRequiredBrands=states.count('ADAPTER_REQUIRED'),brokenBrands=states.count('BROKEN'),
            reviewRequiredBrands=states.count('REVIEW_REQUIRED'),totalBrands=len(brands),finishedAt=report['endedAt'])
        previous_by_id={b['brandId']:b for b in previous_state if b.get('brandId')}
        for batch in report['brands']:
            old=previous_by_id.get(batch.get('brandId'),{})
            new_count=sum(d['brandId']==batch.get('brandId') and d['state']=='NEW' for d in counts['decisions'])
            review_count=sum(d['brandId']==batch.get('brandId') and d['state']=='REVIEW_REQUIRED' for d in counts['decisions'])
            ok=batch['status']=='SUCCESS'
            batch.update(lastCheckedAt=batch.get('checkedAt',started),lastSuccessAt=batch.get('checkedAt',started) if ok else old.get('lastSuccessAt'),
                lastResult='REVIEW_REQUIRED' if review_count else 'SUCCESS' if ok and new_count else 'NO_CHANGE' if ok else 'FAILED',
                reviewMenuCount=review_count,lastMenuCount=batch.get('observedMenuCount',0),lastNewCount=new_count,
                adapterTypes=sorted({s['adapter'] for s in batch.get('sources',[])}),
                sourceTypes=sorted({s['sourceType'] for s in batch.get('sources',[]) if s.get('sourceType')}))
        report.update(noChangeBrands=sum(b['lastResult']=='NO_CHANGE' for b in report['brands']),newMenuBrands=sum(b['lastResult']=='SUCCESS' for b in report['brands']),
            failedCheckBrands=sum(b['lastResult']=='FAILED' for b in report['brands']),
            browserBasedBrands=sum(any(s.get('browserEvidence') for s in b.get('sources',[])) for b in report['brands']),
            apiBasedBrands=sum(any(s.get('sourceType') in ('API','JSON') for s in b.get('sources',[])) for b in report['brands']),
            parserBasedBrands=sum(any(s.get('sourceType')=='HTML' for s in b.get('sources',[])) for b in report['brands']),
            sourceTypeMenuCounts={t:sum(m.get('sourceType')==t for m in menus) for t in sorted(SOURCE_TYPES)},
            missingSourceTypeCount=sum('sourceType' not in m for m in menus))
        successes=report['successBrands']
        report['catalogWritten']=False
        report['publicationStage']='LOCAL_ARTIFACT_ONLY'
        if successes:
            by_menu_id={m['id']:m for m in menus}
            assert len(by_menu_id)==len(menus),'Duplicate stable menu ID'
            for old in existing.get('menus',[]):
                assert old['id'] in by_menu_id and all(by_menu_id[old['id']].get(k)==v for k,v in old.items() if k not in ('officialNutrition','nutritionStatus')),'Existing menu identity or legacy field changed'
            for menu in menus:validate(menu,by_id[menu['brandId']])
            assert all(m.get('sourceType') in SOURCE_TYPES for m in menus),'Missing verified menu provenance'
            # Optional unknown nutrition fields deserialize as null in Android.
            # Omit their repeated JSON keys to keep the verified catalog inside
            # its download limit, without losing facts or changing legacy fields.
            published=[published_menu(m) for m in sorted(menus,key=lambda m:m['id'])]
            payload=json.dumps(dict(schemaVersion=1,menus=published),ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
            checksum=hashlib.sha256(payload).hexdigest();filename='catalog-'+checksum+'.json'
            assert len(payload)<=MAX_CATALOG_BYTES,'Validated catalog exceeds Android download limit; existing published files unchanged'
            if manifest.get('sha256')!=checksum:
                atomic_write(out/filename,payload)
                manifest=dict(schemaVersion=1,file=filename,sha256=checksum,version=checksum,menuCount=len(menus),runKey=run_key,publishedAt=report['endedAt'])
                atomic_write(manifest_path,json.dumps(manifest,sort_keys=True).encode())
                report['catalogWritten']=True
        if nutrition_audit:
            atomic_write(out/'latest-franchise-nutrition-audit.json',json.dumps(nutrition_audit,ensure_ascii=False,indent=2).encode())
        report['catalogVersion']=manifest.get('version');report['checksum']=manifest.get('sha256')
        report['status']='PUBLISHED' if successes else 'EXISTING_CATALOG_RETAINED'
        persistent=dict(previous_by_id)
        for batch in report['brands']:
            if not batch.get('brandId'):continue
            persistent[batch['brandId']]={k:batch[k] for k in ('brandId','lastCheckedAt','lastSuccessAt','lastResult','lastMenuCount','lastNewCount','adapterTypes','sourceTypes')}
            persistent[batch['brandId']]['sources']=[{k:s.get(k) for k in ('sourceUrl','adapter','sourceType','status','error','errorReason','attempts')} for s in batch.get('sources',[])]
        atomic_write(state_path,json.dumps(list(persistent.values()),ensure_ascii=False,indent=2).encode())
        history=out/'sync-history';history.mkdir(exist_ok=True)
        summary={k:v for k,v in report.items() if k not in ('brands','decisions')}
        summary['reviewDecisions']=[d for d in report['decisions'] if d['state']=='REVIEW_REQUIRED']
        summary['brands']=[persistent[b['brandId']] for b in report['brands'] if b.get('brandId')]
        atomic_write(history/(hashlib.sha256(run_key.encode()).hexdigest()+'.json'),json.dumps(summary,ensure_ascii=False,indent=2).encode())
        atomic_write(out/'latest-franchise-sync-report.json',json.dumps(report,ensure_ascii=False,indent=2).encode())
        return report
    finally: lock.unlink(missing_ok=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',required=True);parser.add_argument('--run-key');parser.add_argument('--brand-only')
    parser.add_argument('--healthy-only',action='store_true')
    parser.add_argument('--baseline-catalog',type=Path)
    args=parser.parse_args()
    key=args.run_key or datetime.fromtimestamp(time.time()+9*3600,timezone.utc).strftime('%Y-%m-%d')
    all_brands=json.loads(CONFIG.read_text(encoding='utf-8'))
    targets=None
    if args.brand_only or args.healthy_only:
        targets=update_targets(all_brands,args.healthy_only,set(args.brand_only.split(',')) if args.brand_only else None)
    result=run(args.output,key,all_brands,target_brand_ids=targets,baseline_catalog=args.baseline_catalog)
    print(json.dumps({k:v for k,v in result.items() if k!='brands'},ensure_ascii=False))
