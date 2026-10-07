"""Index captured primary documents against all 155 recipes; no auto publication."""
import json,re
from pathlib import Path
from pypdf import PdfReader
from html.parser import HTMLParser
class PlainHTML(HTMLParser):
    def __init__(self):super().__init__();self.parts=[]
    def handle_data(self,data):self.parts.append(data)
def html_text(raw):
    parser=PlainHTML();parser.feed(raw.decode('utf-8',errors='replace'));return ' '.join(parser.parts)
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-reference-finalization'
def run():
    queue=json.loads((OUT/'reference-first-work-queue.json').read_text(encoding='utf-8'))
    captures=json.loads((ROOT/'data-source/recipe-full-reference/source-captures.json').read_text(encoding='utf-8'))
    selected=[c for c in captures if c.get('status')=='CAPTURED' and any(h in c['url'] for h in ['dietitian.or.kr','images.samsung.com','webzine.seoulmc','50plus.or.kr','okitchen.co.kr/category/detail?idx=2','kamis.or.kr/images/economic/1821/4327','hansik.or.kr/magazines/list/magazineDetail/96/2278','text.cnu.ac.kr','dspace.hansung.ac.kr','li01.tci-thaijo.org','epubs.icar.org.in','ebook.gg.go.kr/src/viewer/download.php','f7a6a03854d3679ada7fd7c52ea5652d','d21559d6fae1c9e601d495c4d41ce04a','ksn.or.kr/upload/general/ebook','dcollection.gwnu.ac.kr','itrc.honam.ac.kr','hansik.or.kr/magazines/list/magazineDetail/33/3019'])]
    documents=[]
    for c in selected:
        path=ROOT/c['rawFile']; pdf=path.read_bytes().startswith(b'%PDF')
        pages=[p.extract_text() or '' for p in PdfReader(path).pages] if pdf else [html_text(path.read_bytes())]
        (OUT/(path.name+'.txt')).write_text('\n\n'.join('PAGE '+str(n+1)+'\n'+s for n,s in enumerate(pages)),encoding='utf-8')
        matches=[]
        for q in queue:
            names=list(dict.fromkeys([q['name'],re.split(r'[<(]',q['name'])[0]]))
            hits=[]
            for n,s in enumerate(pages):
                compact=re.sub(r'\s','',s)
                if any(re.sub(r'\s','',name) in compact for name in names):hits.append(n+1)
            if hits:matches.append(dict(recipeId=q['recipeId'],name=q['name'],pages=hits,appComplete=q['appComplete']))
        documents.append(c|dict(pageCount=len(pages),matches=matches,reviewStatus='INDEXED_REQUIRES_PAGE_REVIEW'))
        print(json.dumps(dict(url=c['url'],pages=len(pages),matches=matches),ensure_ascii=False))
    (OUT/'primary-document-index.json').write_text(json.dumps(documents,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':run()
