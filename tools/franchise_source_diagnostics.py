"""Structural diagnostics only: never records scripts, cookies, inputs or tokens."""
import re
from lxml import html

def public_response_diagnostics(markup, byte_count):
    root=html.fromstring(markup)
    scripts=root.xpath('//script[not(@src)]/text()')
    signals={key:any(re.search(pattern,s,re.I) for s in scripts) for key,pattern in {
        'cookieWrite':r'document\.cookie\s*=', 'locationNavigation':r'location\.(?:href|replace|assign)',
        'captcha':r'captcha','challenge':r'challenge','eval':r'\beval\s*\('
    }.items()}
    for node in root.xpath('//script|//style|//input|//textarea'):node.getparent().remove(node)
    visible=' '.join(root.text_content().split())
    titles=root.xpath('//title/text()')
    denied=bool(re.search(r'access\s+denied|request\s+(?:blocked|rejected)|forbidden|접근\s*차단|접근이\s*차단',visible,re.I))
    return dict(responseBytes=byte_count,documentTitle=' '.join(titles)[:120],visibleTextLength=len(visible),
        inlineScriptCount=len(scripts),inlineSignals=signals,accessDeniedIndicator=denied,
        hasBody=bool(root.xpath('//body')),hasMenuNames=bool(root.xpath('//*[contains(@class,"menu_name")]')))
