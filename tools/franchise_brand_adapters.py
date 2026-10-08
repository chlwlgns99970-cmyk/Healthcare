"""Reviewed brand-specific list extraction; nutrition is never inferred."""
from lxml import html
from urllib.parse import urljoin, urlparse, parse_qs
import json, re
import xml.etree.ElementTree as ET

# Each selector is taken from the previously reviewed official menu captures.
# Each supported source uses its own reviewed identity and container contract.
RULES = {
 '국수나무': ('/food/food.php', '//*[contains(concat(" ",normalize-space(@class)," ")," food-info ")]/h3'),
 '일미리금계찜닭': ('/html/menu.php', '//p[contains(@class,"menu_name")]'),
 '육수당': ('board=menu_01', '//p[contains(@class,"menu_name")]'),
 '이화수전통육개장': ('bo_table=menu', '//li[contains(@class,"gall_li")]//div[@class="bo_tit"]'),
 '죠스떡볶이': ('/menu/', '//a[contains(@href,"menu/view.html")]//h3[contains(@class,"tit")]|//*[contains(@class,"menu_detail")]//h3[contains(@class,"tit")]'),
 '바르다김선생': ('/menu/', '//div[@class="slick-item"]//div[@class="tit"]|//div[@class="menu_list"]//a[contains(@href,"/menu/view.html?")]//div[@class="tit"]'),
 '홍콩반점0410': ('/theborn_brand/', '//div[@class="menu_info"]/div[@class="name"]/p'),
 '역전우동0410': ('/menu/', '//li[contains(concat(" ",normalize-space(@class)," ")," menu-right ")]/h2'),
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
 '스쿨푸드': ('/menu/menu.html', '//div[@class="txt_bx"]/p[@class="tit" and following-sibling::p[@class="des"]]'),
 '에그드랍': ('/menu/list.php', '//a[starts-with(@href,"/menu/view.php?seq=")]/span[@class="text"]'),
 '굽네치킨': ('/menu/menu_list_p', '//div[@class="menu-grid"]/a[@class="item"]/div[@class="textbox"]/h4'),
 '현대옥': ('/menu', '//div[@class="txtBox"]/h4'),
 '새마을식당': ('/sub/menu.php', '//div[@class="view-box"]/h3'),
 '슬로우캘리': ('co_id=menu', '//div[contains(concat(" ",normalize-space(@class)," ")," menu_title ")]/p'),
 '달콤': ('/menu', '//div[@class="text-wrap"]/p[@class="title"]'),
 '교촌치킨': ('/menu/', '//ul[@class="menuProduct"]/li/a/dl[@class="txt"]/dt'),
 '미스터피자': ('bo_table=menu', '//li[contains(concat(" ",normalize-space(@class)," ")," gall_li ")]//a[@class="bo_tit"]'),
 '춘리마라탕': ('/menu/', '//div[@class="menu-card"]/h3'),
 '우지커피': ('/', '//div[contains(concat(" ",normalize-space(@class)," ")," item_gallary ")]/div[starts-with(@id,"caption_")]/h4'),
 '캠토토스트': ('/', '//div[contains(concat(" ",normalize-space(@class)," ")," item_gallary ")]/div[starts-with(@id,"caption_")]/h4'),
 '컴포즈커피': ('act=dispCafemenuGalleryList', '//a[@class="cafemenu-menu-item"]/div[@class="cafemenu-menu-info"]/div[@class="cafemenu-menu-name"]'),
 '맘스터치': ('/menu/', '//li/a[starts-with(@href,"javascript:go_view(")]/h3'),
}

def dunkin_payload(markup):
    root=html.fromstring(markup);values=root.xpath('//*[@id="app"]/@data-page');assert len(values)==1
    value=json.loads(values[0]);assert value['component']=='Client/Menu/List'
    return value['props']

def public_next_records(markup):
    """Decode public Next Flight JSON literals without evaluating JavaScript."""
    root=html.fromstring(markup);chunks=[]
    projections=root.xpath('//script[@type="application/json"][@id="recorded-next-menu-data"]/text()')
    if projections:
        assert len(projections)==1
        return json.loads(projections[0])
    for script in root.xpath('//script/text()'):
        for match in re.finditer(r'self\.__next_f\.push\((\[.*?\])\)\s*;?',script):
            try:value=json.loads(match[1])
            except ValueError:continue
            if len(value)==2 and value[0]==1 and isinstance(value[1],str):chunks.append(value[1])
    records=[]
    def walk(value):
        if isinstance(value,dict):
            records.append(value)
            for child in value.values():walk(child)
        elif isinstance(value,list):
            for child in value:walk(child)
    for line in ''.join(chunks).splitlines():
        _,separator,payload=line.partition(':')
        if not separator:continue
        try:walk(json.loads(payload))
        except ValueError:continue
    return records

def reviewed_rows(brand, markup):
    adapter=brand['adapter']; url=brand['sourceUrl']
    if adapter=='poke-fixed':
        assert brand['name']=='포케올데이' and urlparse(url).path in ('/menu_balance_box','/protein_poke','/rice_bowl','/side','/drink','/newmenu')
        root=html.fromstring(markup);rows=[]
        for link in root.xpath('//div[@class="bh_title"]/a[@data-srl]'):
            identity=link.get('data-srl');assert identity.isdigit() and int(identity)>0
            href=urljoin(url,link.get('href'));href=url if link.get('href')=='#' else href
            rows.append((clean(link.text_content()),brand['sourceKey'],href,identity))
        return rows
    if adapter=='emergency-list':
        assert brand['name']=='응급실국물떡볶이' and urlparse(url).path=='/bbs/board.php'
        root=html.fromstring(markup);board=parse_qs(urlparse(url).query)['bo_table'][0]
        assert board in ('tteok_menu','chicken_menu')
        nodes=root.xpath('//form[@id="fboardlist"]//div[@class="menu-list"]/ul[@class="list"]/li/div[@class="conts"]/div[@class="title"]')
        return [(clean(' '.join(n.itertext())),'떡볶이' if board=='tteok_menu' else '닭볶음탕',url,'') for n in nodes]
    if adapter=='dominos-list':
        assert brand['name']=='도미노피자' and urlparse(url).path=='/goods/list'
        root=html.fromstring(markup);rows=[];category=parse_qs(urlparse(url).query)['dsp_ctgr'][0]
        group={'C0101':'피자','C0201':'사이드디시','C0202':'음료·기타'}[category]
        for box in root.xpath('//div[@class="prd-img"]'):
            if box.xpath('./a[@href="/goods/hnh"]'):continue # Custom-combination navigation, not a fixed menu.
            links=box.xpath('./a[contains(@href,"/goods/detail?") or starts-with(@href,"detail?")]')
            if links:
                raw=links[0].get('href','')
                if raw.startswith('detail?'):href=urljoin(url,raw)
                else:
                    match=re.search(r"['\"](/goods/detail\?[^'\"]+)['\"]",raw);assert match
                    href=urljoin(url,match[1])
                query=parse_qs(urlparse(href).query);identity=query['code_01'][0]
                names=links[0].xpath('./img/@alt')
            else:
                names=box.xpath('./img/@alt');detail=box.xpath('./a[contains(@href,"getDetailSlide(")]/@href')
                if detail:
                    match=re.search(r"getDetailSlide\('([^']+)'",detail[0]);assert match
                    identity=match[1]
                else:
                    ids=box.getparent().xpath('.//input[contains(@id,"_qty")]/@id')
                    assert ids and all(x==ids[0] for x in ids);identity=ids[0].removesuffix('_qty')
                href=url
            assert len(names)==1 and names[0] and identity
            rows.append((clean(names[0]),group,href,identity))
        return rows
    if adapter=='starbucks-navigation':
        assert brand['name']=='스타벅스' and urlparse(url).path in ('/menu/drink_list.do','/menu/food_list.do')
        root=html.fromstring(markup);assert root.xpath('//input[starts-with(@id,"product_")]')
        return []
    if adapter=='pizzahut-json':
        assert brand['name']=='피자헛' and urlparse(url).path.startswith('/api/menu/')
        value=json.loads(markup);assert isinstance(value,list) and value
        rows=[]
        for group in value:
            products=group['items'] if 'items' in group else [group]
            assert isinstance(products,list) and products
            for product in products:
                assert isinstance(product['menuCd'],str) and product['menuCd'] and isinstance(product['name'],str)
                rows.append((clean(product['name']),brand['sourceKey'],'https://www.pizzahut.co.kr/menu/',product['menuCd']))
        return rows
    if adapter=='burgerking-json':
        assert brand['name']=='버거킹' and url=='https://web-prd.burgerking.co.kr/burgerking/BKR0632.json'
        value=json.loads(markup);assert value['header']['result'] is True and not value['header']['error_code']
        categories=value['body']['allMenuList'];assert isinstance(categories,list) and 0<len(categories)<=32
        rows=[]
        for category in categories:
            assert isinstance(category['menuCategoryCd'],str) and category['menuCategoryCd'] and isinstance(category['menuInfo'],list)
            for product in category['menuInfo']:
                assert isinstance(product['menuCd'],str) and product['menuCd'] and isinstance(product['menuNm'],str)
                rows.append((clean(product['menuNm']),category['menuCategoryNm'],'https://www.burgerking.co.kr/menu/main',product['menuCd']))
        return rows
    if adapter in ('bbq-categories','bbq-json'):
        assert brand['name']=='BBQ' and urlparse(url).path.startswith('/api/delivery/menu/')
        value=json.loads(markup);assert isinstance(value,list) and value
        if adapter=='bbq-categories':
            assert all(isinstance(r['id'],int) and r['id']>0 and isinstance(r['categoryName'],str) and r['categoryName'] for r in value)
            return []
        rows=[]
        for r in value:
            assert isinstance(r['id'],int) and r['id']>0 and isinstance(r['menuName'],str)
            assert isinstance(r['isSoldOut'],bool) and isinstance(r['isAdultOnly'],bool)
            if not r['isSoldOut'] and not r['isAdultOnly']:rows.append((clean(r['menuName']),brand['sourceKey'],url,str(r['id'])))
        return rows
    if adapter=='dunkin-native':
        assert brand['name']=='던킨' and urlparse(url).path=='/menu'
        value=dunkin_payload(markup);query=parse_qs(urlparse(url).query);rows=[]
        key='productCats' if query['cat']==['6'] else 'products'
        for r in value[key]['data']:
            assert isinstance(r['id'],int) and r['id']>0 and str(r['dd_product_cat1_id'])==query['cat'][0]
            rows.append((clean(r['TITLE']),r['PRODUCT_CAT2_NM'],url,key+':'+str(r['id'])))
        return rows
    if adapter=='banolim-native':
        assert brand['name']=='반올림피자' and urlparse(url).path=='/menu/list'
        records=public_next_records(markup)
        selected=[r['categoryId'] for r in records if set(r)=={'categoryId'}]
        assert selected==[int(parse_qs(urlparse(url).query)['categoryId'][0])],'Official category response mismatch'
        products=[r for r in records if {'id','name','basePrice','soldOut','optionGroups'}<=r.keys()]
        assert products,'Official embedded menu schema changed'
        rows=[]
        for r in products:
            assert isinstance(r['id'],int) and r['id']>0 and isinstance(r['soldOut'],bool)
            if not r['soldOut']:rows.append((clean(r['name']),brand['sourceKey'],url,str(r['id'])))
        return rows
    if adapter=='jaws-set':
        assert brand['name']=='죠스떡볶이' and urlparse(url).path=='/menu/setmenu.html'
        root=html.fromstring(markup);nodes=root.xpath('//*[@id="setMain"]/div[@class="section"]/img[@alt]');assert nodes
        return [(clean(n.get('alt')),'세트',url,'') for n in nodes]
    if adapter=='paris-fragment':
        assert brand['name']=='파리바게뜨' and urlparse(url).path=='/wp-admin/admin-ajax.php'
        query=parse_qs(urlparse(url).query);assert query.get('action')==['pb_get_product_list']
        root=html.fromstring(markup.lstrip('\ufeff')); containers=root.xpath('self::ul[@data-total-count]|//ul[@data-total-count]')
        assert len(containers)==1,'Official product count schema changed'
        rows=[]
        for link in containers[0].xpath('./li/a[@class="product-list-item"]'):
            names=link.xpath('.//h3[@class="product-name"]');assert len(names)==1
            href=urljoin(url,link.get('href'));assert urlparse(href).path.startswith('/product/')
            rows.append((clean(names[0].text_content()),query['cat1'][0],href,urlparse(href).path.rstrip('/')))
        return rows
    if adapter=='salady-side':
        assert brand['name'] in ('샐러디','샐러디&샌드위치') and urlparse(url).path in ('/menu/list_3','/menu2/list_3')
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//li[a[@class="pop_click"]]/div[@class="text"]/strong'):
            rows.append((clean(node.text_content()),'사이드·음료',url,''))
        return rows
    if adapter=='hollys-list':
        assert brand['name']=='할리스' and urlparse(url).path.startswith('/menu/')
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//div[@class="menu_view01"]'):
            identity=re.fullmatch(r'menuView\d+_(\d+)',node.get('id',''));assert identity
            names=node.xpath('.//div[@class="menu_detail"]/p/span');assert len(names)==1
            rows.append((clean(names[0].text_content()),brand['sourceKey'],url,identity[1]))
        return rows
    if adapter=='kfc-json':
        assert brand['name']=='KFC' and urlparse(url).path=='/kfc/interface/selectDeliveryList'
        payload=json.loads(markup);assert payload['resultState']=='00' and payload['resultCode']=='Y'
        assert payload['kfcs']['resultCd']=='0000'
        value=payload['kfcs']['data'];assert isinstance(value['list'],list) and value['totCnt']==len(value['list'])
        rows=[]
        for row in value['list']:
            assert (row['subGroupCd']==brand['sourceKey'] or brand['sourceKey']=='RCMD' and row['subGroupCd'] in ('BEGR','CHKN'))
            assert isinstance(row['menuCd'],str) and row['menuCd']
            rows.append((clean(row['menuNm']),row['subGroupNm'],url,row['menuCd']))
        return rows
    if adapter in ('ediya-list','ediya-fragment'):
        assert brand['name']=='이디야'
        if adapter=='ediya-fragment':
            assert urlparse(url).path=='/inc/ajax_brand.php' and parse_qs(urlparse(url).query).get('gubun')==['menu_more']
            if markup.strip()=='none':return []
        else:assert urlparse(url).path in ('/contents/drink.html','/contents/bakery.html')
        root=html.fragment_fromstring(markup,create_parent='div');rows=[]
        for node in root.xpath('//div[@class="pro_detail"][@id]'):
            code=re.fullmatch(r'nutri_(\d+)',node.get('id'))
            names=node.xpath('./div[@class="detail_con"]/h2')
            assert code and len(names)==1,'Official Ediya product schema changed'
            name=clean(' '.join(names[0].xpath('./text()')))
            assert name
            rows.append((name,'',url,code[1]))
        return rows
    if adapter=='hongik-text':
        assert brand['name']=='홍익돈까스' and urlparse(url).path.startswith('/menu_2021_')
        root=html.fromstring(markup);rows=[]
        selector='//p[.//span[@text-style-option="fontStyleBold"]]|//p[contains(@style,"font-size: 20px")][./span[@text-style-option="color"]]'
        for node in root.xpath(selector):
            name=clean(node.text_content())
            name=re.sub(r'\s+\d[\d,]*원$','',name)
            if name.startswith(('<','(')) or not re.search('[가-힣]',name) or re.search(r'원|변경|시즌|메뉴|포장|가맹|홍익돈까스만|사이즈업',name) or len(name)>=40:continue
            rows.append((name,'',url,''))
        return rows
    if adapter=='subway-list':
        assert brand['name']=='써브웨이' and urlparse(url).path.startswith('/menuList/')
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//li[./a[@data-menuitemidx]]'):
            name=text(node,'./strong[@class="tit"]');link=node.xpath('./a[@data-menuitemidx]')[0]
            code=link.get('data-menuitemidx');category=link.get('data-category')
            assert code.isdigit() and category==urlparse(url).path.rsplit('/',1)[-1]
            if name:rows.append((name,category,'https://www.subway.co.kr/menuView/'+category+'?menuItemIdx='+code,code))
        return rows
    if adapter in ('mc-categories','mc-index','mc-json'):
        assert brand['name']=='맥도날드' and urlparse(url).path.startswith('/api/v1/kor/')
        payload=json.loads(markup);assert payload['resultCode']==100 and payload['isOk']=='ok'
        value=payload['resultObject'];assert isinstance(value['list'],list)
        if adapter!='mc-json':return []
        rows=[]
        for row in value['list']:
            assert isinstance(row['seq'],int) and row['seq']>0
            name=row['korName'];root=html.fragment_fromstring(name,create_parent='span')
            assert all(n.tag in ('span','sub','sup','br') for n in root.iter()),'Unexpected markup in official product name'
            rows.append((clean(root.text_content()),'',url,str(row['seq'])))
        return rows
    if adapter=='bhc-json':
        assert brand['name']=='BHC' and '/api/v1/web/categories/' in url
        payload=json.loads(markup);assert payload['status']=='success' and isinstance(payload['body'],list)
        rows=[]
        for row in payload['body']:
            name=clean(row['productNm']);code=str(row['productCd'])
            assert name.strip() and code.isdigit() and isinstance(row['options'],list)
            variants=[o['optionNm'] for o in row['options']] or ['']
            for option in variants:
                assert isinstance(option,str)
                option=clean(option)
                rows.append((name+(' ('+option+')' if option else ''),'',url,code+(':'+option if option else '')))
        return rows
    if adapter=='bhc-categories':
        assert brand['name']=='BHC';payload=json.loads(markup)
        assert payload['status']=='success' and isinstance(payload['body'],list) and payload['body']
        return []
    if adapter=='nene-list':
        assert brand['name']=='네네치킨' and urlparse(url).path=='/home_menu.asp'
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//div[contains(concat(" ",normalize-space(@class)," ")," MenuBox ")][@onclick]'):
            route=re.fullmatch(r"location.href='(/home_menu_detail.asp\?[^']+)'",node.get('onclick'))
            assert route,'Official Nene product route changed'
            names=node.xpath('.//div[contains(concat(" ",normalize-space(@class)," ")," MenuTitle ")]')
            assert len(names)==1,'Official Nene name schema changed'
            name=clean(' '.join(names[0].xpath('./text()')))
            link=urljoin(url,route[1]);code=parse_qs(urlparse(link).query).get('no',[''])[0]
            assert code.isdigit(),'Official Nene product identity changed'
            if name:rows.append((name,'',link,code))
        return rows
    if adapter=='cheogajip-list':
        assert brand['name']=='처갓집양념치킨' and 'bo_table=allmenu' in url
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//li[contains(concat(" ",normalize-space(@class)," ")," gall_li ")]//li[@class="gall_text_href"]'):
            name=clean(' '.join(node.xpath('./text()')))
            if name:rows.append((name,'',url,''))
        return rows
    if adapter=='venti-list':
        assert brand['name']=='더벤티' and '/new2022/menu/all.html' in url
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//a[contains(@href,"all-view.new.html?uid=")]'):
            name=text(node,'./div[@class="txt_bx"]/p[@class="tit"]')
            link=urljoin(url,node.get('href'));code=parse_qs(urlparse(link).query).get('uid',[''])[0]
            assert code.isdigit(),'Official Venti product identity changed'
            if name:rows.append((name,'',link,code))
        return rows
    if adapter=='pascucci-list':
        assert brand['name']=='파스쿠찌' and '/product/productList.asp' in url
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//a[@class="product"][@data-productseq]'):
            code=node.get('data-productseq');name=text(node,'./figure/figcaption/h2')
            assert code.isdigit(),'Official Pascucci product identity changed'
            if name:rows.append((name,'',url,code))
        return rows
    if adapter=='eatz-list':
        expected={'롯데리아':'ria','엔제리너스':'angel','크리스피크림도넛':'kkd'}
        assert urlparse(url).path=='/brand/'+expected[brand['name']]
        root=html.fromstring(markup);rows=[]
        tabs=root.xpath('//button[contains(concat(" ",normalize-space(@class)," ")," mn-nav-btn ")][@data-target]')
        assert tabs,'Official Eatz category schema changed'
        for tab in tabs:
            sections=root.xpath('//*[@id=$target]',target=tab.get('data-target'))
            assert len(sections)==1,'Official Eatz category missing'
            for node in sections[0].xpath('.//div[@class="mn-card-body"][@id]'):
                name=text(node,'./p[@class="mn-card-name"]');code=node.get('id')
                assert re.fullmatch(r'REP_\d+',code),'Official Eatz product identity changed'
                if name:rows.append((name,clean(tab.text_content()),url,code))
        return rows
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
    if adapter=='hansot-json':
        assert brand['name']=='한솥'
        payload=json.loads(markup);assert payload['cate1Info']['name'] and isinstance(payload['subdata'],list)
        return [(row['title'].strip(),payload['cate1Info']['name']+' / '+section['cate2Info']['name'],
            'https://www.hsd.co.kr/menu/menu_view/'+str(row['idx']),str(row['idx']))
            for section in payload['subdata'] for row in section['goodsList']]
    if adapter=='mega-fragment':
        assert brand['name']=='메가MGC커피'
        root=html.fragment_fromstring(markup,create_parent='div');rows=[]
        for node in root.xpath('//ul[@id="menu_list"]/li'):
            name=text(node,'./a//div[contains(@class,"cont_text_title")]//b')
            variant=text(node,'./a//div[contains(@class,"cont_gallery_list_label")]')
            if name:rows.append((name+(' ('+variant+')' if variant else ''),'',url,''))
        return rows
    if adapter=='seventh-list':
        assert brand['name']=='7번가피자'
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//div[@class="p_list"]//li/a[starts-with(@href,"javascript:goView(")]'):
            code=re.fullmatch(r'javascript:goView\((\d+)\);?',node.get('href'))
            name=text(node,'.//div[@class="t_info"]/strong')
            if code and name:rows.append((name,'',urljoin(url,'view.php?menuSeq='+code[1]),code[1]))
        return rows
    if adapter=='hjh-list':
        assert brand['name']=='홍종흔베이커리'
        root=html.fromstring(markup);rows=[]
        for node in root.xpath('//ul[contains(concat(" ",normalize-space(@class)," ")," goods-list ")]/li'):
            links=node.xpath('.//a[starts-with(@href,"javascript:LinkProductView(")]')
            name=text(node,'.//div[@class="info"]/strong')
            if not links or not name:continue
            code=re.fullmatch(r'javascript:LinkProductView\((\d+)\);?',links[0].get('href'))
            if code:rows.append((name,'',url,code[1]))
        return rows
    if adapter=='tlj':
        root=html.fromstring(markup)
        rows=[]
        for block in root.xpath('//li[contains(concat(" ",normalize-space(@class)," ")," item_wrap ")]'):
            names=block.xpath('.//span[@class="name"]')
            code=re.search(r"viewDetail\('([0-9]+)'\)",html.tostring(block,encoding='unicode'))
            if names and code: rows.append((clean(names[0].text_content()),'',urljoin(url,'detail.asp?gubun=result&prod_num='+code[1]),code[1]))
        return rows
    if adapter=='norang-list':
        assert brand['name']=='노랑통닭'
        root=html.fromstring(markup);rows=[]
        for node in root.xpath("//a[contains(@href,'_view.html')]/p"):
            link=urljoin(url,node.getparent().get('href'));query=parse_qs(urlparse(link).query)
            code=query.get('p_no',[''])[0]
            assert code.isdigit(),'Official Norang product identity changed'
            rows.append((clean(node.text_content()),'사이드' if '/side' in url else '치킨',link,code))
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
            key=next((k for k in ('prdcode','seq','idx','wr_id','num','id','item_srl') if k in query),None)
            identity=key+':'+query[key][0] if key else ''
            if brand['name']=='굽네치킨' and links:
                code=re.fullmatch(r'menu_view\("(\d+)",\s*0\)',links[0].get('onclick',''))
                assert code,'Official Goobne product identity changed'
                identity=code[1];link=url
            if brand['name']=='맘스터치' and links:
                code=re.fullmatch(r"javascript:go_view\('(\d+)'\);?",links[0].get('href',''))
                assert code,'Official Momstouch product identity changed'
                identity=code[1];link='https://www.momstouch.co.kr/menu/view.php?idx='+code[1]
            result.append((name,'',link,identity))
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
        group = {"paik-coffee": "커피", "paik-drink": "음료", "paik-dessert": "디저트", "paik-ccino": "빽스치노", "paik-new": "신메뉴"}[key]
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
