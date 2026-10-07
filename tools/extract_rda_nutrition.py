"""RDA 2026 DB 10.4; attribution required, edible portion per 100g."""
from pathlib import Path
import io,json,hashlib,openpyxl
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication'
p=OUT/'raw/b146dd6df98d23fa'
digest=hashlib.sha256(p.read_bytes()).hexdigest()
assert digest=='271cc431f2991b3c0c049ec6e05fb59a040319e984ab71468184530de61dec50'
w=openpyxl.load_workbook(io.BytesIO(p.read_bytes()),read_only=True,data_only=True)
s=w['국가표준식품성분 Database 10.4'];rows=list(s.values)
assert '100g' in rows[0][3] and rows[2][5]=='kcal'
codes={r['name'].strip():r['code'] for r in json.loads((OUT/'rda-10.4-nutrition-identities.json').read_text(encoding='utf-8'))}
facts=[]
for i,r in enumerate(rows[3:],4):
    name=r[3].strip()
    assert name in codes,name
    facts.append(dict(code=codes[name],name=name,energyKcal=r[5],referenceAmount=100,unit='g',worksheetRow=i,index=r[0],attribution='농촌진흥청, 2026. 국가표준식품성분 DB 10.4.',sourceUrl='https://www.nics.go.kr/food/kfi/fct/fctIntro/list?menuId=PS03562',sha256=digest))
assert len(facts)==3366
(OUT/'rda-10.4-nutrients.json').write_text(json.dumps(facts,ensure_ascii=False,indent=2),encoding='utf-8')
print('verified',len(facts),'official kcal rows')
