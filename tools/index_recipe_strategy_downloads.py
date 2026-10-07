"""Reproduce the two new official bulk indexes from captured HTML/XLS bytes."""
import hashlib,json,sys
from lxml import html
from relink_recipe_strategy import ROOT,OUT,load,save

def run():
    manifest=load(OUT/'source-captures.json');derived=[]
    raw=next(c for c in manifest if c['url'].endswith('searchKeyword1=R&limit=10000'))
    p=ROOT/raw['rawFile'];assert hashlib.sha256(p.read_bytes()).hexdigest()==raw['sha256']
    doc=html.fromstring(p.read_bytes(),parser=html.HTMLParser(encoding='utf-8'))
    table=max(doc.xpath('//table'),key=lambda t:len(t.xpath('.//tr')));tr=table.xpath('.//tr')
    head=[x.text_content().strip() for x in tr[0].xpath('./th|./td')]
    rows=[dict(zip(head,[x.text_content().strip() for x in r.xpath('./td')])) for r in tr[1:]]
    assert len(rows)==3672 and all(r['데이터구분코드']=='R' for r in rows)
    save('public-raw-nutrients.json',rows)
    derived.append(dict(file='public-raw-nutrients.json',rows=len(rows),sourceSha256=raw['sha256']))
    sys.path.insert(0,str(ROOT/'app/build/recipe-linkage-strategy/python-deps'))
    import xlrd
    raw=next(c for c in manifest if 'downloadFoodNutrientDB.do|POST|' in c['url'])
    p=ROOT/raw['rawFile'];assert hashlib.sha256(p.read_bytes()).hexdigest()==raw['sha256']
    sheet=xlrd.open_workbook(str(p)).sheet_by_index(0);rows=[sheet.row_values(i) for i in range(sheet.nrows)]
    assert len(rows)==4907 and rows[0][3:5]==['1회제공량 (g)','열량 (kcal)']
    save('mfds-legacy-nutrients-rows.json',rows)
    derived.append(dict(file='mfds-legacy-nutrients-rows.json',dataRows=len(rows)-1,sourceSha256=raw['sha256']))
    for r in derived:r['derivedSha256']=hashlib.sha256((OUT/r['file']).read_bytes()).hexdigest()
    save('bulk-transform-provenance.json',derived)
    print(json.dumps(derived,ensure_ascii=False))
if __name__=='__main__':run()
