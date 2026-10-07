from pathlib import Path
import json,hashlib
from capture_franchise_quality_sources import fetch
from lxml import html
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-menu-completion';OUT.mkdir(exist_ok=True)
urls=['https://www.nolboo.co.kr/','http://www.nolboo.co.kr/','https://www.nolboo.co.kr/pages/brand/boodae.asp','https://www.nolboo.co.kr/brand/budae/menu.aspx','https://www.nolboo.co.kr/brand/boodae/menu.aspx','https://www.nolboo.co.kr/robots.txt','https://pf.kakao.com/_Nxaxmxad','https://ydp.redtable.global/ko/store/28646']
rows=[]
for url in urls:
    row={'url':url,'checkedAt':'2026-10-04'}
    try:
        r=fetch(url)
        if 'error' in r:raise RuntimeError(r['error'])
        row.update(status=r['status'],finalUrl=r['resolvedUrl'],sha256=r['sha256'])
        file=OUT/(hashlib.sha256(url.encode()).hexdigest()[:16]+'.html');file.write_text(r['text'],encoding='utf-8')
        row['rawFile']=str(file.relative_to(ROOT))
        doc=html.fromstring(r['text'])
        row['links']=doc.xpath('//a/@href');row['scripts']=doc.xpath('//script/@src')
        row['text']=' '.join(doc.xpath('//body//text()'))[:18000]
    except Exception as e:row['error']=str(e)
    rows.append(row)
    print(json.dumps({k:v for k,v in row.items() if k in ('url','status','error')},ensure_ascii=False),flush=True)
(OUT/'nolboo-source-audit.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
