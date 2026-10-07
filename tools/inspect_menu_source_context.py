from pathlib import Path
import sys,re,json
from lxml import html
from import_franchise_quality import captured_pages
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication'
brands=set(sys.argv[1:]);results=[]
for capture in captured_pages():
    if capture['brand'] not in brands:continue
    text=capture.get('text','') or capture.get('html','') or capture.get('body','')
    if not text:continue
    try:
        doc=html.fromstring(text);plain=re.sub(r'\s+',' ',' '.join(doc.itertext())).strip()
    except Exception:plain=text
    results.append(dict(brand=capture['brand'],url=capture['url'],sha256=capture.get('sha256',''),text=plain))
for r in results:
    print(r['brand'],r['url'],r['sha256'])
    for term in ('토핑','사이드','메가리카노','김밥','마리','연.참.문','맵닭','엽기메뉴','카사바','쿠키','밸런스'):
        for match in list(re.finditer(re.escape(term),r['text']))[:2]:print(term,r['text'][max(0,match.start()-100):match.end()+160])
(OUT/'menu-source-context.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
