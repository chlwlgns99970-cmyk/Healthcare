from import_franchise_quality import captured_pages,document,text
from lxml import html
import json,re
from pathlib import Path
pages=captured_pages()+[dict(p,url=p['sourceUrl'],sha256=p.get('originalSha256','')) for p in json.loads(Path('data-source/franchise/raw/delivery-chain-pages.json').read_text(encoding='utf-8'))]
for b,t in [('써브웨이','화이트 초코 마카다미아'),('에그드랍','오렌지 썬라이즈'),('청년다방','두부피롤'),('동대문엽기떡볶이','우삼겹'),('호식이두마리치킨','크리스피골드'),('슬로우캘리','훈제오리 40g')]:
    print('\n',b,t)
    for p in [p for p in pages if p['brand']==b and t in p.get('text','')][:2]:
        print(p['url'],p.get('sha256'));d=document(p['text'])
        nodes=d.xpath('//*[contains(text(),$term)]',term=t)
        for n in nodes[:2]:
            parent=n
            for _ in range(3):
                if parent.getparent() is not None:parent=parent.getparent()
            print(html.tostring(parent,encoding='unicode')[:7000])
