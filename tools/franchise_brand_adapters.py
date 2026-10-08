"""Reviewed brand-specific list extraction; nutrition is never inferred."""
from lxml import html
from urllib.parse import urljoin, urlparse, parse_qs
import json, re
import xml.etree.ElementTree as ET

# Each selector is taken from the previously reviewed official menu captures.
# Generic headings (newmaul/7th/chunli/hyundaiok) remain review-only.
RULES = {
 '국수나무': ('/food/food.php', '//*[contains(concat(" ",normalize-space(@class)," ")," food-info ")]/h3'),
 '일미리금계찜닭': ('/html/menu.php', '//p[contains(@class,"menu_name")]'),
 '육수당': ('board=menu_01', '//p[contains(@class,"menu_name")]'),
 '이화수전통육개장': ('bo_table=menu', '//li[contains(@class,"gall_li")]//div[@class="bo_tit"]'),
 '죠스떡볶이': ('/menu/', '//a[contains(@href,"menu/view.html")]//h3[contains(@class,"tit")]|//*[contains(@class,"menu_detail")]//h3[contains(@class,"tit")]'),
 '바르다김선생': ('/menu/view.html', '//div[@class="slick-item"]//div[@class="tit"]'),
 '홍콩반점0410': ('/theborn_brand/', '//div[@class="menu_info"]/div[@class="name"]/p'),
 '역전우동0410': ('/theborn_brand/', '//div[@class="menu_info"]/div[@class="name"]/p'),
 '이비가짬뽕': ('/page/sub2_', '//p[@class="m-tit"]'),
 '죽이야기': ('/15', '//*[starts-with(@id,"caption_")]/h4'),
 '원할머니보쌈': ('/bossam/menu.asp', '//div[@class="info"]/strong'),
 '소담촌': ('hid=allMenu', '//div[@class="main_allMenu_textbox"]/h3[normalize-space()]|//div[@class="main_allMenu_textbox"]/h3[not(normalize-space())]/following-sibling::p[1]'),
 '봉추찜닭': ('go=page1.0', '//div[@class="menu_text"]/h2'),
 '김가네': ('board=menu_01', '//p[@class="menu_name"]'),
 '고봉민김밥인': ('/03_menu/', '//span[@class="name"]'),
 '신전떡볶이': ('/doc/menu0', '//p/span'),
 '두찜': ('co_id=menu', '//a[contains(@class,"d_sLightBox2") and contains(@href,"bo_table=menu")]'),
 '샐러디': ('/menu/list_1', '//li[.//a[@href]]/h6|//li[.//h6]//h6'),
 '샐러디&샌드위치': ('/menu2/list_1', '//li[.//a[@href]]/h6|//li[.//h6]//h6'),
 '이삭토스트': ('/menu/menu.php', '//a[contains(@href,"ptype=view")]//h4'),
 '매머드커피': ('/sub/menu/', '//li//div[@class="txt_wrap"]/strong'),
}

def reviewed_rows(brand, markup):
    adapter=brand['adapter']; url=brand['sourceUrl']
    if adapter=='bon-api':
        code=brand['brandCode']
        if markup.lstrip().startswith('<ResultData>'):
            root=ET.fromstring(markup)
            payload=dict(status=root.findtext('status'),data=dict(
                brand={n.tag:n.text for n in root.find('data/brand')},
                menuList=[{n.tag:n.text for n in row} for row in root.findall('data/menuList/menuList')]))
        else: payload=json.loads(markup)
        assert payload['status']=='success' and payload['data']['brand']['brdCd']==code
        assert payload['data']['brand']['brdNm']==brand['name']
        rows=[]
        for row in payload['data']['menuList']:
            assert row['brdCd']==code and isinstance(row['cmdtNm'],str) and row['cmdtNm'].strip()
            # The official brand page is the app-approved provenance origin.
            link='https://www.bonif.co.kr/brand/menu/detail?brdCd='+code+'&cmdtIdx='+str(row['cmdtIdx'])
            rows.append((row['cmdtNm'].strip(),'',link,code+':'+str(row['cmdtIdx'])))
        return rows
    if adapter=='starbucks-json':
        payload=json.loads(markup); assert isinstance(payload['list'],list)
        kind=brand['sourceKey']
        return [(row['product_NM'],'','https://www.starbucks.co.kr/menu/'+kind+'_view.do?product_cd='+str(row['product_CD']),kind+':'+str(row['product_CD'])) for row in payload['list']]
    if adapter=='tlj':
        root=html.fromstring(markup)
        rows=[]
        for block in root.xpath('//li[contains(concat(" ",normalize-space(@class)," ")," item_wrap ")]'):
            names=block.xpath('.//span[@class="name"]')
            code=re.search(r"viewDetail\('([0-9]+)'\)",html.tostring(block,encoding='unicode'))
            if names and code: rows.append((clean(names[0].text_content()),'',urljoin(url,'detail.asp?gubun=result&prod_num='+code[1]),code[1]))
        return rows
    if adapter=='reviewed-delivery':
        return [(name, group, link, '') for name,group,_,link in card_rows(dict(key=brand['sourceKey'],sourceUrl=url,text=markup))]
    if adapter=='reviewed-html':
        required,selector=RULES[brand['name']]
        assert required in url
        root=html.fromstring(re.sub(r'<!--.*?-->','',markup,flags=re.S))
        result=[]
        for node in root.xpath(selector):
            name=clean(node.text_content())
            if not name: continue
            links=node.xpath('ancestor::a[@href][1]');link=urljoin(url,links[0].get('href')) if links else url
            if urlparse(link).scheme not in ('https','http'): link=url
            query=parse_qs(urlparse(link).query)
            key=next((k for k in ('prdcode','seq','idx','wr_id','num') if k in query),None)
            result.append((name,'',link,key+':'+query[key][0] if key else ''))
        return result
    return []
def clean(value):
    return re.sub(r"\s+", " ", value or "").strip()

def text(node, xpath="."):
    found = node.xpath(xpath)
    return clean(found[0].text_content()) if found else ""

def card_rows(page):
    tree = html.fromstring(re.sub(r"<!--.*?-->", "", page["text"], flags=re.S))
    key, url = page["key"], page["sourceUrl"]
    results = []

    def add(name, group="", description="", link=""):
        name = clean(name)
        if name:
            results.append((name, group, clean(description), urljoin(url, link) if link else url))

    if key == "pizzaschool-menu":
        for node in tree.xpath("//h3[contains(@class,'grid-entry-title')]/a"):
            name = clean(node.text_content())
            if not any(w in name for w in ("피자", "스파게티", "치즈볼", "치킨텐더", "치킨스틱", "새우링", "꽈배기", "그라탕")):
                continue
            add(name, "피자" if "피자" in name else "사이드", text(node.getparent().getparent().getparent(), ".//*[contains(@class,'grid-entry-excerpt')]"), node.get("href"))
    elif key == "papajohns-pizza":
        for node in tree.xpath("//a[starts-with(@href,'/menu/pizza/')]"):
            add(text(node, ".//p[contains(@class,'font-bold')]"), "피자", link=node.get("href"))
    elif key == "papajohns-other-side":
        for node in tree.xpath("//p[contains(@class,'lg:text-xl') and contains(@class,'font-bold')]"):
            name = clean(" ".join(node.xpath("./text()")))
            if "굿즈" not in name:
                add(name, "사이드")
    elif key.startswith("youngman-"):
        group = {"youngman-menu": "프리미엄 피자", "youngman-original": "세트 메뉴", "youngman-two": "두 판 세트"}.get(key, "사이드")
        for node in tree.xpath("//*[contains(@class,'menu_name')]"):
            ancestors = node.xpath("ancestor::a[1]")
            add(node.text_content(), group, link=ancestors[0].get("href", "") if ancestors else "")
    elif key.startswith("norang-"):
        for node in tree.xpath("//a[contains(@href,'_view.html')]/p"):
            add(node.text_content(), "치킨" if "chicken" in key else "사이드", link=node.getparent().get("href"))
    elif key.startswith("gcova-chicken"):
        for node in tree.xpath("//*[contains(@class,'sub_title')]"):
            add(node.text_content(), "치킨")
    elif key.startswith("cheogajip-"):
        for node in tree.xpath("//li[@class='gall_text_href']/h2"):
            add(node.text_content(), "치킨" if "치킨" in node.text_content() or "윙" in node.text_content() else "사이드")
    elif key == "pizzamaru-menu":
        for node in tree.xpath("//*[@id='content_wrap']//div[contains(@class,'con08_menu_tt')]"):
            group = node.xpath("ancestor::div[contains(@class,'con08_menu ')][1]")
            # Official sixth carousel is the side group, separate from pizzas.
            is_side = group and "con08_menu06" in group[0].get("class", "")
            add(node.text_content(), "사이드" if is_side else "피자", text(node.getparent(), ".//*[contains(@class,'con08_menu_desc')]"))
    elif key.startswith("mexicana-"):
        for node in tree.xpath("//dl[@class='tit']"):
            add(text(node, "./dt"), "치킨", text(node, "./dd"))
    elif key == "60chicken-list":
        for node in tree.xpath("//p[@class='name']"):
            # Responsive clones retain identical identities; de-duplicated below.
            add(node.text_content(), "치킨" if "치킨" in node.text_content() or "콤보" in node.text_content() or "윙" in node.text_content() else "사이드", text(node.getparent(), ".//p[@class='text']"))
    elif key == "banolim-menu":
        for node in tree.xpath("//p[contains(@class,'_title_')]"):
            add(node.text_content(), "")
    elif key == "frank-menu":
        for node in tree.xpath("//p[@class='menu_ko']"):
            add(node.text_content(), "버거" if "버거" in node.text_content() else "사이드", text(node.getparent(), ".//p[@class='stext']"))
    elif key == "nobrand-menu-native":
        for node in tree.xpath("//li[contains(@class,'menu_item')]/button[@data-name]"):
            group = text(node.xpath("ancestor::div[contains(@class,'menu_group')][1]")[0], ".//em[@class='menu_group_title']")
            name = re.split(r"<\s*/?\s*br\s*/?>", node.get("data-name", ""), flags=re.I)[0]
            description = html.fromstring("<div>" + node.get("data-story", "") + "</div>").text_content()
            add(name, group, description)
    elif key == "newmaul-list":
        for node in tree.xpath("//h3[not(@class='blind')]"):
            add(node.text_content(), "고기류")
    elif key.startswith("paik-"):
        group = {"paik-coffee": "커피", "paik-drink": "음료", "paik-dessert": "디저트", "paik-ccino": "빽스치노"}[key]
        for node in tree.xpath("//h3[@class='font-bl']"):
            add(node.text_content(), group, text(node.getparent(), ".//p"))
    elif key.startswith("baskin-"):
        group = text(tree, "//h2[@class='page-header__title']")
        group = {"Ice Cream": "아이스크림", "Ice Cream Cake": "아이스크림 케이크", "Dessert": "디저트", "Prepack": "프리팩", "Beverage": "음료", "Coffee": "커피"}.get(group, group)
        for node in tree.xpath("//strong[@class='menu-list__title']"):
            links = node.getparent().xpath("./a[@href]")
            add(node.text_content(), group, link=links[0].get("href") if links else "")
    elif key == "paris-menu":
        for node in tree.xpath("//h3[@class='product-name']"):
            preceding = node.xpath("preceding::h2[@class='category-title'][1]")
            ancestors = node.xpath("ancestor::a[1]")
            add(node.text_content(), clean(preceding[0].text_content()) if preceding else "", link=ancestors[0].get("href") if ancestors else "")
    elif key == "yoajung-list":
        for node in tree.xpath("//*[contains(@class,'main_renew_top_con_top_2nd_item')][@data-total]"):
            add(text(node, ".//*[contains(@class,'main_renew_top_con_top_2nd_item_top_2nd')]/p"), "요거트 아이스크림/토핑")
    elif key == "misoya-menu":
        for node in tree.xpath("//p[@class='title']/strong"):
            if clean(node.text_content()) in ("신선합니다","맛있습니다","건강합니다"):
                continue
            add(node.text_content(), "", text(node.getparent(), ".//span[@class='body']"))
    elif key == "hosigi-list":
        for node in tree.xpath("//div[starts-with(@id,'caption_')]/h4"):
            # The caption's first text node is the food identity; span is an option.
            name = clean(" ".join(node.xpath("./text()")))
            add(name, "치킨" if "치킨" in name else "", text(node.getparent(), "./p"))
    elif key == "hollys-home":
        for node in tree.xpath("//div[@class='menu_view01']"):
            add(text(node, ".//div[@class='menu_detail']/p/span"), "커피", text(node, ".//p[@class='menu_info']"))
    elif key.startswith("7th-"):
        for node in tree.xpath("//strong[ancestor::div[contains(@class,'menu')]]"):
            name = clean(node.text_content())
            if name in ("MENU", "NEW"):
                continue
            add(name, "사이드" if key == "7th-cate8" else "피자")
    elif key == "tomato-list":
        for node in tree.xpath("//p[@class='menu_tit']"):
            add(node.text_content(), "도시락")
    elif key == "yupdduk-list":
        for node in tree.xpath("//p[@class='menutitle']"):
            preceding = node.xpath("preceding::p[@class='menutabtitle'][1]")
            add(node.text_content(), clean(preceding[0].text_content()) if preceding else "")
    elif key == "gongcha-list":
        for node in tree.xpath("//a[contains(@href,'product_detail')]"):
            add(text(node, ".//div[@class='text-a']/p"), "음료", link=node.get("href"))
    elif key.startswith("dunkin-"):
        for node in tree.xpath("//a[contains(@href,'/menu/view?') or contains(@href,'/menu/viewProductCat?')]"):
            group = "디저트" if "cat=1" in node.get("href") else "커피" if "cat=6" in node.get("href") else "음료" if "cat=3" in node.get("href") else ""
            name = text(node, ".//h4")
            if not any(word in name for word in ("텀블러", "머그", "키링", "굿즈")):
                add(name, group, link=node.get("href"))
    elif key == "jokbal-menu-resolved":
        for node in tree.xpath("//p[contains(@class,'menu_list_tit')]"):
            add(node.text_content(), "", text(node.getparent(), ".//p[contains(@class,'menu_list_desc')]"))
    return results
