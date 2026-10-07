import sys,re
from import_franchise_quality import captured_pages,text,document
from lxml import html
qs=[('써브웨이','1311'),('에그드랍','seq=222'),('에그드랍','seq=253'),('청년다방','menu_view'),('동대문엽기떡볶이','yup-menu'),('슬로우캘리','co_id=menu'),('포케올데이','nutrition_info'),('샐러디','type=side'),('고봉민김밥인','menu02'),('김가네','menu_01')]
pages=captured_pages()
for b,u in qs:
    cs=[p for p in pages if p['brand']==b and u in p['url']]
    if not cs:print('MISSING',b,u);continue
    for p in cs[:2]:
        print('\nSOURCE',b,p['url'],p.get('sha256'))
        raw=p.get('text','');d=document(raw)
        if b in ('동대문엽기떡볶이','청년다방','김가네','고봉민김밥인'):
            print(text(raw)[-10000:])
        elif b=='슬로우캘리':
            for node in d.xpath('//*[contains(@class,"menu_box02") and contains(@class,"menu_change")]'):print('BLOCK',text(html.tostring(node,encoding='unicode'))[:10000])
        elif b=='포케올데이':
            m=re.search('토마토.{0,30}당근',raw)
            if m:print(raw[max(0,m.start()-800):m.start()+3500])
        else:print(text(raw)[-5000:])
if __name__=='__main__':pass
