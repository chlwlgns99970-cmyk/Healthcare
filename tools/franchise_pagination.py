"""Reviewed pagination contracts; never follows arbitrary page links."""
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse, urljoin
from lxml import html
import re
import json

def confirmed_empty_source(brand,source,markup):
    adapter=source['adapter']
    if adapter=='twosome-menu-json' and brand['name']=='투썸플레이스':
        value=json.loads(markup)
        return value.get('queryCode')==1000 and value.get('queryMessage')=='SUCCESS' and value.get('rowCount')==0 and value.get('fetchResultListSet')==[]
    if adapter=='pizzahut-complete-json' and brand['name']=='피자헛' and source.get('optionalPromotion'):
        value=json.loads(markup)
        return value==[] or isinstance(value,dict) and value.get('menus')==[]
    if adapter=='sungsim-food-list' and brand['name']=='성심당':
        root=html.fromstring(markup)
        return any(' '.join(n.text_content().split())=='총 0개' for n in root.xpath('//div[@class="total"]')) and not root.xpath('//a[contains(@href,"product_view?")][normalize-space(.)]')
    if adapter=='ediya-fragment' and brand['name']=='이디야':return markup.strip()=='none'
    if adapter=='starbucks-json' and brand['name']=='스타벅스':
        value=json.loads(markup);return set(value)=={'list'} and value['list']==[]
    if adapter=='poke-fixed' and brand['name']=='포케올데이' and urlparse(source['url']).path=='/newmenu':
        root=html.fromstring(markup)
        return bool(root.xpath('//div[@class="item_count"][normalize-space(.)="총 0건"]')) and not root.xpath('//div[@class="bh_title"]/a[@data-srl]')
    if adapter=='hjh-list' and brand['name']=='홍종흔베이커리':
        root=html.fromstring(markup)
        return bool(root.xpath('//ul[@class="goods-list"]/li[normalize-space(.)="현재 등록된 상품이 없습니다."]'))
    if adapter=='reviewed-html' and brand['name']=='달콤':
        root=html.fromstring(markup);query=parse_qs(urlparse(source['url']).query)
        selected=root.xpath('//input[@name="dalMenu"][@checked]/@value')
        lists=root.xpath('//ul[contains(concat(" ",normalize-space(@class)," ")," data-list ")]')
        return selected==query.get('dalMenu') and len(lists)==1 and not lists[0].xpath('./*') and not lists[0].text_content().strip()
    return False

def additional_pages(brand,source,markup):
    mode=source.get('pagination')
    if not mode:return []
    if mode=='twosome-navigation':
        assert brand['name']=='투썸플레이스'
        root=html.fromstring(markup);codes=root.xpath('//a[@name="grtNm"]/@value')
        assert set(codes)=={'NEW','1','2','3','4','5'}
        return [dict(url='https://mo.twosome.co.kr/mn/menuInfoMidListAjax.json',adapter='twosome-categories-json',method='POST',key=code,form=dict(grtCd=code),pagination='twosome-categories') for code in codes if code!='NEW']+[dict(url='https://mo.twosome.co.kr/mn/menuInfoListAjax.json',adapter='twosome-menu-json',method='POST',key='NEW',form=dict(pageNum='1',grtCd='NEW',midCd=''))]
    if mode=='twosome-categories':
        assert brand['name']=='투썸플레이스'
        value=json.loads(markup);assert value['queryCode']==1000 and value['queryMessage']=='SUCCESS'
        data=value['fetchResultListSet'];assert len(data)==value['rowCount'] and len(data)<=32
        code=source['form']['grtCd']
        if code=='5':
            assert {r['MID_NM'] for r in data}<={'원두/티 상품','카페용품'}
            return []
        assert data and all(r['GRT_CD']==code for r in data)
        return [dict(url='https://mo.twosome.co.kr/mn/menuInfoListAjax.json',adapter='twosome-menu-json',method='POST',key=code+' / '+r['MID_NM'],form=dict(pageNum='1',grtCd=code,midCd=r['MID_CD'])) for r in data]
    if mode=='sungsim-store':
        assert brand['name']=='성심당'
        root=html.fromstring(markup);result=[]
        for node in root.xpath('//button[@onclick]'):
            match=re.fullmatch(r"location.href='(/shop/product/product_lists\?sh_category1_cd=(\d{5})&sh_category2_cd=(\d{5}))'",node.get('onclick'))
            if not match:continue
            # Explicit non-food merchandise/card categories; all public food tabs are traversed.
            if match[2]=='70000' or match[3]=='30401':continue
            assert match[2] in {'10000','20000','30000','40000','50000','60000','80000'},'Unreviewed new product category'
            result.append(dict(source,url=urljoin(source['url'],match[1]),key=' '.join(node.text_content().split())))
        assert 10<=len(result)<=64,'Official food category navigation changed'
        return result
    if mode=='isung-store':
        assert brand['name']=='이성당'
        parsed=urlparse(source['url']);root=html.fromstring(markup);result=[]
        assert root.xpath('//div[contains(@class,"xans-product-normalpaging")]'),'Official paging contract missing'
        for href in root.xpath('//div[contains(@class,"xans-product-normalpaging")]//a/@href'):
            if href=='#none':continue
            p=urlparse(urljoin(source['url'],href));query=parse_qs(p.query)
            assert p.netloc==parsed.netloc and p.path==parsed.path and set(query)=={'page'}
            number=query['page'][0];assert number.isdigit() and 1<=int(number)<=64
            result.append(dict(source,url=p.geturl()))
        return result
    if mode=='youngman':
        assert brand['name']=='청년피자'
        root=html.fromstring(markup);parsed=urlparse(source['url']);paths={'/sub01/menu.php','/sub01/menu2.php','/sub01/menu3.php','/sub01/menu4.php','/sub01/menu6.php','/sub01/menu7.php'}
        result=[]
        for href in root.xpath('//a/@href'):
            p=urlparse(urljoin(source['url'],href));q=parse_qs(p.query)
            if p.path not in paths:continue
            assert p.netloc==parsed.netloc
            if p.query and not q.get('page_num'):continue
            page=q.get('page_num',['1'])[0];assert page.isdigit() and 1<=int(page)<=64
            result.append(dict(url=p.geturl(),key='youngman-current',adapter='youngman-native',pagination='youngman'))
        return result
    if mode=='goobne':
        assert brand['name']=='굽네치킨' and urlparse(source['url']).path=='/menu/menu_list_p'
        root=html.fromstring(markup);codes=set()
        for value in root.xpath('//a/@onclick'):
            match=re.fullmatch(r'''menu_list\(["'](\d{1,3})["'],\s*["']["']\)''',value)
            if match:codes.add(match[1])
        assert len(codes)>=5,'Official Goobne category navigation disappeared'
        assert root.xpath('//form[@name="menuListForm"]/@method')==['get'],'Official category form method changed'
        return [dict(url=source['url']+'?'+urlencode(dict(classId=c,classId2='')),key='goobne-'+c,adapter='reviewed-html') for c in sorted(codes)]
    if mode=='teacherkim':
        assert brand['name']=='바르다김선생' and urlparse(source['url']).path=='/menu/list.html'
        root=html.fromstring(markup);parsed=urlparse(source['url']);result=[]
        for href in root.xpath('//a[contains(@href,"/menu/list.html")]/@href'):
            p=urlparse(urljoin(source['url'],href));q=parse_qs(p.query)
            if p.path!='/menu/list.html' or not q.get('bs'):continue
            assert p.netloc==parsed.netloc and re.fullmatch(r'004\d{3}',q['bs'][0])
            page=q.get('pg',['1'])[0];assert page.isdigit() and 1<=int(page)<=64
            query=dict(bs=q['bs'][0])
            if int(page)>1:query['pg']=page
            result.append(dict(source,url=urlunparse(parsed._replace(query=urlencode(query)))))
        assert result,'Official menu navigation schema changed'
        return result
    if mode=='dalkomm':
        assert brand['name']=='달콤' and urlparse(source['url']).path=='/menu'
        root=html.fromstring(markup);values=root.xpath('//input[@name="dalMenu"]/@value')
        assert 0<len(values)<=32 and len(values)==len(set(values)) and all(v.isdigit() and int(v)>0 for v in values)
        return [dict(source,url='https://www.dalkomm.com/menu?'+urlencode(dict(dalMenu=v))) for v in values]
    if mode=='starbucks-navigation':
        assert brand['name']=='스타벅스' and urlparse(source['url']).path in ('/menu/drink_list.do','/menu/food_list.do')
        root=html.fromstring(markup);ids=set(root.xpath('//input[starts-with(@id,"product_")]/@id'))-{'product_all'}
        pairs=re.findall(r'tmp_cate\s*==\s*["\'](product_\w+)["\']\s*\)\s*\{\s*result\s*=\s*["\'](W\d{7})["\']',markup)
        mapping=dict(pairs);assert ids and ids<=mapping.keys(),'Official category navigation schema changed'
        codes={mapping[i] for i in ids};codes.update(root.xpath('//img/@data-sbseq'))
        assert 0<len(codes)<=32 and all(re.fullmatch(r'W\d{7}',c) for c in codes)
        group='food' if '/food_' in source['url'] else 'drink'
        return [dict(url='https://www.starbucks.co.kr/upload/json/menu/'+c+'.js',adapter='starbucks-json',key=group) for c in sorted(codes)]
    if mode=='bbq-categories':
        assert brand['name']=='BBQ' and urlparse(source['url']).path=='/api/delivery/menu/category'
        value=json.loads(markup);assert isinstance(value,list) and 0<len(value)<=32
        assert all(isinstance(r['id'],int) and r['id']>0 and isinstance(r['categoryName'],str) and r['categoryName'] for r in value)
        return [dict(url='https://www.bbq.co.kr/api/delivery/menu/'+str(r['id']),adapter='bbq-json',key=r['categoryName']) for r in value]
    if mode=='dunkin-native':
        assert brand['name']=='던킨'
        from franchise_brand_adapters import dunkin_payload
        value=dunkin_payload(markup);parsed=urlparse(source['url']);query=parse_qs(parsed.query)
        key='productCats' if query['cat']==['6'] else 'products'
        products=value[key];meta=products['meta'];total=meta['total'];per=meta['per_page'];current=meta['current_page'];last=meta['last_page']
        assert total>0 and isinstance(per,int) and per>0 and 1<=last<=64 and last==(total+per-1)//per
        assert current==int(query.get('page',['1'])[0]) and len(products['data'])==min(per,total-(current-1)*per)
        if current!=1:return []
        assert len(value['categories'])==1
        categories=value['categories'][0]['data'];assert 0<len(categories)<=32
        result=[]
        for category in categories:
            assert category['PRODUCT_CAT1_NM'] in ('DONUT','FOOD','COFFEE','BEVERAGE','SNACK & MORE')
            result.append(dict(source,url=urlunparse(parsed._replace(query=urlencode(dict(cat=category['id'])))),key=category['PRODUCT_CAT1_NM']))
        result += [dict(source,url=urlunparse(parsed._replace(query=urlencode(dict(query,page=[str(p)]),doseq=True)))) for p in range(2,last+1)]
        return result
    if mode=='banolim':
        assert brand['name']=='반올림피자'
        from franchise_brand_adapters import public_next_records
        categories=[r['categoryList'] for r in public_next_records(markup) if 'categoryList' in r]
        assert len(categories)==1 and 0<len(categories[0])<=32
        parsed=urlparse(source['url']);query=parse_qs(parsed.query);result=[]
        for r in categories[0]:
            assert isinstance(r['id'],int) and r['id']>0 and isinstance(r['name'],str) and not r['children']
            result.append(dict(source,url=urlunparse(parsed._replace(query=urlencode(dict(categoryId=r['id'])))),key=r['name']))
        return result
    if mode in ('isaac','dunkin','mexicana'):
        contracts={'isaac':('이삭토스트','/menu/menu.php','catcode','//ul[@class="pagination"]//a/@href'),
            'dunkin':('던킨','/menu','cat','//a[contains(@href,"page=")]/@href'),
            'mexicana':('멕시카나','/menu/product.asp','CateCode','//div[contains(concat(" ",normalize-space(@class)," ")," paging ")]//a/@href')}
        name,path,category,selector=contracts[mode];assert brand['name']==name
        root=html.fromstring(markup);parsed=urlparse(source['url']);assert parsed.path==path
        query=parse_qs(parsed.query,keep_blank_values=True);result=[]
        for href in root.xpath(selector):
            link=urljoin(source['url'],href);p=urlparse(link);q=parse_qs(p.query,keep_blank_values=True)
            if not q.get('page'):continue
            assert p.netloc==parsed.netloc and p.path==parsed.path
            assert q['page'][0].isdigit() and 1<=int(q['page'][0])<=64
            assert q.get(category,[''])==query.get(category,[''])
            result.append(dict(source,url=link))
        return result
    if mode=='paris':
        assert brand['name']=='파리바게뜨'
        parsed=urlparse(source['url']);query=parse_qs(parsed.query)
        assert parsed.path=='/wp-admin/admin-ajax.php' and query['action']==['pb_get_product_list'] and query['per_page']==['12']
        root=html.fromstring(markup.lstrip('\ufeff'));containers=root.xpath('self::ul[@data-total-count]|//ul[@data-total-count]');assert len(containers)==1
        total=int(containers[0].get('data-total-count'));current=int(query['paged'][0]);last=(total+11)//12
        assert total>0 and 1<=last<=64 and 1<=current<=last
        assert len(containers[0].xpath('./li'))==min(12,total-(current-1)*12),'Official product count mismatch'
        if current!=1:return []
        return [dict(source,url=urlunparse(parsed._replace(query=urlencode(dict(query,paged=[str(p)]),doseq=True)))) for p in range(2,last+1)]
    if mode in ('ediya-start','ediya-next'):
        assert brand['name']=='이디야'
        if mode=='ediya-start':
            assert urlparse(source['url']).path in ('/contents/drink.html','/contents/bakery.html')
            category=7 if '/drink.' in source['url'] else 8
            return [dict(url='https://www.ediya.com/inc/ajax_brand.php?'+urlencode(dict(gubun='menu_more',product_cate=category,chked_val='',skeyword='',page=2)),adapter='ediya-fragment',method='POST',pagination='ediya-next')]
        if markup.strip()=='none':return []
        parsed=urlparse(source['url']);query=parse_qs(parsed.query,keep_blank_values=True)
        assert parsed.path=='/inc/ajax_brand.php' and query['gubun']==['menu_more'] and query['product_cate'][0] in ('7','8')
        page=int(query['page'][0]);assert 2<=page<64,'Official page limit exceeded'
        return [dict(source,url=urlunparse(parsed._replace(query=urlencode(dict(query,page=[str(page+1)]),doseq=True))))]
    if mode=='compose':
        assert brand['name']=='컴포즈커피'
        root=html.fromstring(markup);parsed=urlparse(source['url']);query=parse_qs(parsed.query);result=[]
        for href in root.xpath('//a[contains(@href,"act=dispCafemenuGalleryList") and contains(@href,"page=")]/@href'):
            link=urljoin(source['url'],href);p=urlparse(link);q=parse_qs(p.query)
            assert p.netloc==parsed.netloc and p.path==parsed.path
            assert q.get('mid')==query.get('mid') and q.get('act')==query.get('act') and q.get('category_srl')==query.get('category_srl')
            assert q.get('page') and q['page'][0].isdigit() and 1<=int(q['page'][0])<=64
            result.append(dict(source,url=link))
        return result
    if mode in ('mc-categories','mc-index','mc-pages'):
        assert brand['name']=='맥도날드'
        payload=json.loads(markup);assert payload['resultCode']==100 and payload['isOk']=='ok'
        value=payload['resultObject'];parsed=urlparse(source['url']);query=parse_qs(parsed.query,keep_blank_values=True)
        base='https://www.mcdonalds.co.kr/api/v1/kor/product/product/list'
        if mode=='mc-categories':
            assert value['list'] and len(value['list'])<=32
            return [dict(url=base+'?'+urlencode(dict(page=1,view_rows=6,mainCategory=r['seq'],subCategory=0,searchWord='')),
                adapter='mc-index',pagination='mc-index',key=r['korName']) for r in value['list']]
        if mode=='mc-index':
            assert value['subCategory'] and len(value['subCategory'])<=32
            return [dict(source,url=urlunparse(parsed._replace(query=urlencode(dict(query,subCategory=[str(r['seq'])]),doseq=True))),
                adapter='mc-json',pagination='mc-pages') for r in value['subCategory']]
        total=value['totalCount'];assert isinstance(total,int) and total>0
        last=(total+5)//6;assert 1<=last<=64
        current=int(query['page'][0]);assert len(value['list'])==min(6,total-(current-1)*6),'Official page count mismatch'
        if current!=1:return []
        return [dict(source,url=urlunparse(parsed._replace(query=urlencode(dict(query,page=[str(p)]),doseq=True)))) for p in range(2,last+1)]
    if mode=='bhc-categories':
        assert brand['name']=='BHC'
        payload=json.loads(markup);assert payload['status']=='success' and payload['body']
        assert all(isinstance(r['cateIdx'],int) and r['cateIdx']>0 and isinstance(r['cateNm'],str) and r['cateNm'] for r in payload['body']),'Official category schema changed'
        return [dict(url='https://www.bhc.co.kr/api/v1/web/categories/'+str(r['cateIdx'])+'/products',
            adapter='bhc-json',key=r['cateNm']) for r in payload['body']]
    if mode=='gnuboard':
        assert brand['name'] in ('미스터피자','이화수전통육개장','처갓집양념치킨','응급실국물떡볶이')
        root=html.fromstring(markup);parsed=urlparse(source['url']);query=parse_qs(parsed.query);result=[]
        for href in root.xpath('//a[contains(concat(" ",normalize-space(@class)," ")," pg_page ")]/@href'):
            link=urljoin(source['url'],href);page=urlparse(link);q=parse_qs(page.query)
            assert page.netloc.encode('idna').decode()==parsed.netloc.encode('idna').decode() and page.path==parsed.path
            assert q.get('bo_table')==query.get('bo_table') and q.get('sca')==query.get('sca')
            assert q.get('page') and q['page'][0].isdigit() and 1<=int(q['page'][0])<=64
            result.append(dict(source,url=link))
        return result
    if mode=='seventh':
        assert brand['name']=='7번가피자'
        root=html.fromstring(markup);parsed=urlparse(source['url']);query=parse_qs(parsed.query);result=[]
        for href in root.xpath('//*[contains(concat(" ",normalize-space(@class)," ")," paging ")]//a/@href'):
            match=re.fullmatch(r'javascript:goPage\((\d+)\);?',href)
            if not match:continue
            page=int(match[1]);assert 1<=page<=64
            result.append(dict(source,url=urlunparse(parsed._replace(query=urlencode(dict(query,page=[str(page)]),doseq=True)))))
        return result
    if mode=='hjh':
        assert brand['name']=='홍종흔베이커리'
        root=html.fromstring(markup);parsed=urlparse(source['url']);result=[]
        for href in root.xpath('//div[@class="pagination"]/a[@href]/@href'):
            link=urljoin(source['url'],href);page=urlparse(link);query=parse_qs(page.query)
            if page.scheme not in ('http','https'):continue
            assert page.netloc==parsed.netloc and page.path==parsed.path
            assert query.get('page') and query['page'][0].isdigit() and 1<=int(query['page'][0])<=64
            result.append(dict(source,url=link))
        return result
    assert mode=='mega' and brand['name']=='메가MGC커피'
    root=html.fragment_fromstring(markup,create_parent='div')
    values=root.xpath('//ul[@id="board_page"]//a[@data-page]/@data-page')
    assert root.xpath('//ul[@id="board_page"]'),'Official pagination schema changed'
    assert all(v.isdigit() for v in values),'Official pagination schema changed'
    last=max(map(int,values),default=1);assert 1<=last<=64,'Official page limit exceeded'
    parsed=urlparse(source['url']);query=parse_qs(parsed.query)
    assert query.get('page')==['1']
    result=[]
    for page in range(2,last+1):
        updated=dict(query,page=[str(page)])
        result.append(dict(source,url=urlunparse(parsed._replace(query=urlencode(updated,doseq=True))),pagination=None))
    return result
