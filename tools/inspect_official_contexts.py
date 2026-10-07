from pathlib import Path
import re,json
ROOT=Path(__file__).resolve().parents[1]
rows=json.loads((ROOT/'data-source/full-adjudication/menu-source-context.json').read_text(encoding='utf-8'))
for brand,url,term in [('슬로우캘리','co_id=menu','훈제오리 40g'),('슬로우캘리','co_id=menu','Topping'),('포케올데이','menu_balance_box','훈제오리'),('써브웨이','1311','쿠키'),('스쿨푸드','menu/menu.html','마리'),('신전떡볶이','menu06','납작면')]:
    c=next((c for c in rows if c['brand']==brand and url in c['url']),None)
    print(brand,url)
    if c:
        for m in list(re.finditer(re.escape(term),c['text']))[:3]:print(c['text'][max(0,m.start()-150):m.end()+400])
text=(ROOT/'data-source/full-adjudication/raw/e311d27953dd736a').read_text(encoding='utf-8')
for term in ['/web/profiles/','/web/v2/profiles/','/rocket-web/web/profiles/','/posts/recent','baseURL']:
    print('KAKAO',term)
    for m in list(re.finditer(re.escape(term),text))[:2]:print(text[max(0,m.start()-180):m.end()+180])
