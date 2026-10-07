"""Capture the last public candidates and inspect exact salt source identities."""
import hashlib,json,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-final-residual'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def run():
    raw=OUT/'raw';raw.mkdir(exist_ok=True)
    captures=[]
    for name,url in [('foodnuri-mungge','https://www.foodnuri.go.kr/portal/bbs/B0000279/view.do?deleteCd=0&menuNo=300056&nttId=234725&pageIndex=1'),('goe-school-recipes','https://www.goe.go.kr/resource/old/BBSMSTR_000000030174/BBS_202503040153062180.pdf')]:
        try:
            req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'})
            data=urllib.request.urlopen(req,timeout=45).read()
            path=raw/(name+('.pdf' if name.startswith('goe') else '.html'));path.write_bytes(data)
            item=dict(name=name,url=url,sha256=hashlib.sha256(data).hexdigest(),rawFile=path.relative_to(ROOT).as_posix(),bytes=len(data))
            if path.suffix=='.pdf':
                from pypdf import PdfReader
                doc=PdfReader(path);texts=[p.extract_text() for p in doc.pages]
                (raw/(name+'.txt')).write_text('\n'.join(texts),encoding='utf-8')
                words=['백합','멍게','파래','주꾸미','달래','묵밥','두부김치','양상추샐러드']
                item['pages']=len(texts);item['relevantPages']=[dict(page=i+1,text=t) for i,t in enumerate(texts) if any(w in t.replace(' ','') for w in words)]
                print(json.dumps(dict(name=name,pages=len(texts),relevantPages=item['relevantPages']),ensure_ascii=False))
            captures.append(item)
        except Exception as exc:captures.append(dict(name=name,url=url,error=str(exc)))
    (OUT/'last-public-source-captures.json').write_text(json.dumps(captures,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    ns={n['code']:n for n in load(ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json')}
    for group in load(ROOT/'data-source/recipe-amount-priority/kdca-reference-compositions.json'):
        if group[0]['excelRow']==13104:
            for row in group:
                if '소금' in row['ingredient']:print(json.dumps(dict(row=row,currentNutrition=ns.get(row['officialCode'])),ensure_ascii=False))
if __name__=='__main__':run()
