from pathlib import Path
import re
from import_franchise_quality import captured_pages,document,content
root=document(next(p['text'] for p in captured_pages() if p['brand']=='써브웨이' and 'menuList/sidedrink' in p['url']))
print([(content(n),n.xpath('.//img/@src')) for n in root.xpath('//li[strong[@class="tit"]]') if any(t in content(n) for t in ['화이트 초코','초코칩','오트밀'])])
for fn in ['128b6e7d0bae6ca7','0a0a7ff05117c9e5','37482a4e4c8ce354']:
    raw=(Path('data-source/full-adjudication/raw')/fn).read_text(encoding='utf-8')
    print(fn)
    for term in ['wapi','brandCode','/brand','/menus','/foods','/brands','baseURL']:
        for m in list(re.finditer(re.escape(term),raw))[:4]:print(term,raw[max(0,m.start()-120):m.start()+250])
