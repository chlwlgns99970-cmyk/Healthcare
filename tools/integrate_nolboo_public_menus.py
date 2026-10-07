"""Published restaurant menus on the public tourism sales platform, no nutrition."""
from pathlib import Path
import csv, json, hashlib
from lxml import html
from import_franchise_expansion import generate_menu_kotlin,read_csv
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-menu-completion'
SOURCE=ROOT/'data-source/franchise'
def build():
    captures=json.loads((OUT/'nolboo-source-audit.json').read_text(encoding='utf-8'))
    capture=next(r for r in captures if r['url']=='https://ydp.redtable.global/ko/store/28646')
    assert capture['status']==200
    raw=ROOT/capture['rawFile'];doc=html.fromstring(raw.read_text(encoding='utf-8'))
    assert '놀부부대찌개&철판구이' in doc.text_content() and '신길로 39' in doc.text_content()
    rows=[]
    for node in doc.xpath('//div[contains(@class,"store-menu-box")]'):
        title=node.xpath('.//span[contains(@class,"menu_tit")]/text()')[0].strip()
        if '이용권' in title:continue
        url=node.xpath('.//a/@href')[0];identity=url.rsplit('/',1)[-1]
        if not identity.isdigit():continue
        category='한식' if title in ('고기듬뿍김치찌개','돼지고기 묵은지김치찜','놀부햄김치찜') else ''
        rows.append(dict(id='official-menu-nolboo-ydp-'+identity,brand='놀부부대찌개',name=title,
            sourceUrl=capture['url'],verifiedAt='2026-10-04',sourceDate='',saleState='PUBLIC_TOURISM_STORE_MENU',
            energyKcal='',servingAmount='',servingUnit='',menuCategory=category,
            storeName='놀부부대찌개&철판구이 (서울 신길로 39)',sourceSha256=capture['sha256'],sourceListingUrl=capture['url'],detailUrl=url))
    assert len(rows)==7
    path=OUT/'nolboo-public-menu-snapshot.csv'
    with path.open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=rows[0]);writer.writeheader();writer.writerows(rows)
    menus=[]
    for filename in ('official-menu-snapshot.csv','additional-menu-snapshot.csv','quality-menu-snapshot.csv','delivery-menu-snapshot.csv'):
        menus+=read_csv(SOURCE/filename)
    extra=ROOT/'data-source/full-adjudication/nolboo-order-menu-snapshot.csv'
    menus+=rows+(read_csv(extra) if extra.exists() else []);generate_menu_kotlin(menus)
    print(json.dumps(dict(menuCount=len(rows),nutritionCount=0,ingredientsCount=0,allergenCount=0,storeScope=rows[0]['storeName']),ensure_ascii=False))
if __name__=='__main__':build()
