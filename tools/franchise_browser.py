"""Normal public Chromium renderer; no stealth, login, cookie injection or TLS bypass."""
from contextlib import contextmanager
from threading import BoundedSemaphore, local
from urllib.parse import urlparse
import re,json

def hansot_public_menu(page):
    """Read the JSON the site's own category links request, without replaying credentials."""
    responses={}
    def capture(response):
        if re.fullmatch(r'https://www\.hsd\.co\.kr/api/menu/menu_list/\d+/\d+',response.url):
            responses[response.url]=response
    page.on('response',capture)
    try:
        response=page.goto('https://www.hsd.co.kr/menu/menu_list',wait_until='domcontentloaded',timeout=20000)
        if response.status in (401,403,429):raise PublicAccessDenied(response.status)
        page.wait_for_timeout(1200)
        links=page.locator('a[onclick^="showMenuList("]').evaluate_all('(xs)=>xs.map(x=>x.getAttribute("onclick"))')
        pairs=[]
        for action in links:
            match=re.fullmatch(r"showMenuList\('(\d+)', '(\d+)'\);return false;",action)
            assert match,'Official category action changed'
            pair=tuple(match.groups())
            if pair not in pairs:pairs.append(pair)
        assert 7<=len(pairs)<=64,'Official category inventory changed'
        categories=[]
        for main,sub in pairs:
            url=f'https://www.hsd.co.kr/api/menu/menu_list/{main}/{sub}'
            with page.expect_response(lambda r:r.url==url,timeout=15000) as event:
                page.locator('a[onclick="'+f"showMenuList('{main}', '{sub}');return false;"+'"]').first.click(timeout=3000)
            result=event.value
            if result.status in (401,403,429):raise PublicAccessDenied(result.status)
            assert result.ok,'Official menu category failed'
            value=result.json()
            assert value['cate1Info']['idx']==int(main)
            assert len(value['subdata'])==1 and value['subdata'][0]['cate2Info']['idx']==int(sub)
            assert isinstance(value['subdata'][0]['goodsList'],list)
            categories.append(value)
        return json.dumps(categories,ensure_ascii=False),dict(transport='normal-chromium-ui-xhr',httpStatus=200,finalUrl=page.url,tlsVerification=True,categoryCount=len(pairs),allCategoryActionsCompleted=True)
    finally:page.remove_listener('response',capture)

_slots=BoundedSemaphore(2)
_state=local()

class PublicAccessDenied(ValueError):
    def __init__(self,status):
        self.code=status
        super().__init__('ACCESS_DENIED: '+str(status))

def denied(status,text):
    return status in (401,403,429) or bool(re.search(r'captcha|verify you are human|access denied|접근이\s*차단|보안문자',text,re.I))

@contextmanager
def brand_browser_session():
    _state.context=None;_state.browser=None;_state.playwright=None;_state.acquired=False
    try:yield
    finally:
        if _state.context:_state.context.close()
        if _state.browser:_state.browser.close()
        if _state.playwright:_state.playwright.stop()
        if _state.acquired:_slots.release()

def render(brand,source):
    from playwright.sync_api import sync_playwright
    from franchise_daily_sync import official_url
    api=source.get('method','GET')=='POST'
    assert not api or brand['name']=='한솥' and source['adapter']=='hansot-json','Unreviewed browser API'
    assert official_url(source['url'],brand)
    if not getattr(_state,'context',None):
        _slots.acquire();_state.acquired=True
        _state.playwright=sync_playwright().start()
        _state.browser=_state.playwright.chromium.launch(headless=True)
        _state.context=_state.browser.new_context(ignore_https_errors=False)
    page=_state.context.new_page()
    try:
        def route(request_route):
            request=request_route.request
            if request.is_navigation_request() and request.frame==page.main_frame and not official_url(request.url,brand):request_route.abort()
            else:request_route.continue_()
        page.route('**/*',route)
        if source.get('adapter')=='hansot-browser-json':
            assert brand['name']=='한솥' and source['url']=='https://www.hsd.co.kr/menu/menu_list'
            return hansot_public_menu(page)
        entry='https://www.hsd.co.kr/menu/menu_list' if api else source['url']
        response=page.goto(entry,wait_until='domcontentloaded',timeout=20000)
        status=response.status if response else None
        if status in (401,403,429):raise PublicAccessDenied(status)
        page.wait_for_timeout(1200)
        assert official_url(page.url,brand),'Unapproved official redirect'
        text=page.locator('body').inner_text(timeout=3000)
        if denied(status,text):raise ValueError('ACCESS_CHALLENGE: normal rendering stopped')
        if api:
            response=_state.context.request.post(source['url'],timeout=20000)
            assert official_url(response.url,brand),'Unapproved API redirect'
            if response.status in (401,403,429):raise PublicAccessDenied(response.status)
            if denied(response.status,response.text()):raise ValueError('ACCESS_CHALLENGE: browser API')
            response.json() # Schema parser checks identity separately.
            return response.text(),dict(transport='normal-chromium-public-api',httpStatus=response.status,finalUrl=response.url,tlsVerification=True)
        # Bounded scrolling only; load-more contracts must be explicitly reviewed per source.
        previous=None;stable=0
        for step in range(12):
            height=page.locator('body').evaluate('(el)=>el.scrollHeight')
            page.locator('body').evaluate('(el)=>window.scrollTo(0,el.scrollHeight)')
            page.wait_for_timeout(350)
            current=(height,page.locator('body').inner_text(timeout=3000))
            stable=stable+1 if current==previous else 0
            if stable>=2:break
            previous=current
        else:raise ValueError('LAZY_LOAD_LIMIT: content did not stabilize')
        assert not page.locator('button:visible').filter(has_text=re.compile(r'^(더\s*보기|load more)$',re.I)).count(),'Unreviewed load-more contract'
        markup=page.content()
        assert len(markup.encode())<=brand.get('maxSourceBytes',2_000_000),'Rendered source too large'
        return markup,dict(transport='normal-chromium',httpStatus=status,finalUrl=page.url,scrollSteps=step+1,scrollStable=True,tlsVerification=True)
    finally:page.close()
