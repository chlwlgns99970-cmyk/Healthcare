"""Normal public Chromium renderer; no stealth, login, cookie injection or TLS bypass."""
from contextlib import contextmanager
from threading import BoundedSemaphore, local
from urllib.parse import urlparse
import re

_slots=BoundedSemaphore(2)
_state=local()

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
        entry='https://www.hsd.co.kr/menu/menu_list' if api else source['url']
        response=page.goto(entry,wait_until='domcontentloaded',timeout=20000)
        status=response.status if response else None
        if status in (401,403,429):raise ValueError('ACCESS_DENIED: '+str(status))
        page.wait_for_timeout(1200)
        assert official_url(page.url,brand),'Unapproved official redirect'
        text=page.locator('body').inner_text(timeout=3000)
        if denied(status,text):raise ValueError('ACCESS_CHALLENGE: normal rendering stopped')
        if api:
            response=_state.context.request.post(source['url'],timeout=20000)
            assert official_url(response.url,brand),'Unapproved API redirect'
            if denied(response.status,response.text()):raise ValueError('ACCESS_DENIED: browser API')
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
