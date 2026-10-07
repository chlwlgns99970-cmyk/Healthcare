"""Select exact unbranded analyzed ingredient references, never retail namesakes."""
import hashlib,json,re,zipfile
from collections import defaultdict
from pathlib import Path
from lxml import etree
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-linkage-maximization'
SOURCE=ROOT/'data-source/kfind/kfind-processed-food-db-2026-08-28.xlsx'
EXPECTED='b074d98e75d2d087dc1b193f0056affbf9afd14524c9ccb556cb2b20978504f7'
REVIEWED_GROUPS={'어묵':'어묵','식빵':'빵류','생크림':'가공유크림','버터':'버터','고추장':'고추장'}
norm=lambda s:re.sub(r'\s+','',s or '')
def extract():
    sha=hashlib.sha256(SOURCE.read_bytes()).hexdigest();assert sha==EXPECTED
    decisions=json.loads((OUT/'baseline-ingredient-decisions.json').read_text(encoding='utf-8'))
    wanted={norm(d['ingredient']) for d in decisions if d['identityStatus']=='AMBIGUOUS_IDENTITY'}
    ns='{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
    candidates=[];count=0
    with zipfile.ZipFile(SOURCE) as z:
        strings=[]
        if 'xl/sharedStrings.xml' in z.namelist():
            root=etree.fromstring(z.read('xl/sharedStrings.xml'))
            strings=[''.join(si.itertext()) for si in root]
        with z.open('xl/worksheets/sheet1.xml') as stream:
            for _,row in etree.iterparse(stream,events=('end',),tag=ns+'row'):
                cells={}
                for c in row:
                    v=c.find(ns+'v');value=v.text if v is not None else ''.join(c.find(ns+'is').itertext()) if c.find(ns+'is') is not None else ''
                    cells[re.sub(r'\d','',c.get('r',''))]=strings[int(value)] if c.get('t')=='s' and value else value
                if count==0:headers=cells
                else:
                    name=cells.get('B','')
                    if norm(name) in wanted:
                        candidate={headers.get(k,k):v for k,v in cells.items()}
                        candidate['worksheetRow']=int(row.get('r'))
                        candidates.append(candidate)
                count+=1;row.clear()
                while row.getprevious() is not None:del row.getparent()[0]
    assert count-1==316734,count
    groups=defaultdict(list)
    for r in candidates:
        if r.get('데이터생성방법명')=='분석' and all(r.get(k) in ('','해당없음',None) for k in ('제조사명','수입업체명','유통업체명')) and r.get('영양성분함량기준량')=='100g':
            groups[norm(r['식품명'])].append(r)
    accepted={k:v[0] for k,v in groups.items() if len(v)==1 and v[0].get('에너지(kcal)') not in ('',None)
              and v[0].get('식품소분류명')==REVIEWED_GROUPS.get(k)}
    result=dict(sourceRows=count-1,sourceSha256=sha,sourceUrl='https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do',checkedAt='2026-10-05',
        exactNameCandidates=candidates,accepted=accepted,ambiguousAnalyzed={k:v for k,v in groups.items() if len(v)>1})
    (OUT/'kfind-generic-fallbacks.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(dict(rows=count-1,accepted=list(accepted),ambiguous=list(result['ambiguousAnalyzed'])),ensure_ascii=False),flush=True)
if __name__=='__main__':extract()
