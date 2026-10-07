"""Closed, individual review of the immutable 229-menu baseline.

Indexes are review-sheet positions only; generated lookup keys are immutable IDs.
Official DOM groups are scoped to exact items, never a brand's industry.
"""
from pathlib import Path
import csv,json,re,hashlib
from collections import Counter
from import_franchise_quality import captured_pages,document,text
from lxml import html
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication'
def read(p):
    with p.open(encoding='utf-8-sig') as f:return list(csv.DictReader(f))
def norm(v):return re.sub('[^가-힣a-z0-9]','',v.lower())
def run():
    baseline=[r for r in read(OUT/'menu-before.csv') if not r['menuCategory']];assert len(baseline)==229
    metadata={r['foodItemId']:r for r in read(ROOT/'app/src/main/assets/fooddata/food_metadata.csv')}
    pages=captured_pages()+[dict(p,url=p['sourceUrl'],sha256=p.get('originalSha256','')) for p in json.loads((ROOT/'data-source/franchise/raw/delivery-chain-pages.json').read_text(encoding='utf-8'))]
    reviews={}
    def put(indices,category,reason,proof=None):
        for i in indices:
            assert i not in reviews,i
            b=baseline[i];m=metadata.get(b['menuId'],{});evidence=m.get('ingredientText') or b['name'];url=m.get('sourceReference','https://ydp.redtable.global/ko/store/28646' if b['brand']=='놀부부대찌개' else '');sha=m.get('sourceHash','')
            if proof:
                evidence,url,sha=proof(b)
            reviews[i]=dict(menuId=b['menuId'],name=b['name'],brand=b['brand'],status='CATEGORY_ASSIGNED' if category else 'CATEGORY_UNRESOLVABLE',category=category or '',reason=reason,evidence=evidence,sourceUrl=url,sourceHash=sha,checkedAt='2026-10-05',brandIndustryUsed=False)
    def group(brand,urlpart,xpath,label):
        p=next(p for p in pages if p['brand']==brand and urlpart in p['url'] and document(p.get('text','')).xpath(xpath));nodes=document(p['text']).xpath(xpath);raw=' '.join(text(html.tostring(n,encoding='unicode')) for n in nodes)
        def proof(b):
            assert norm(b['name']) in norm(raw),(b['name'],label)
            return label+' :: '+b['name']+' :: '+raw,p['url'],p.get('sha256','')
        return proof
    side_salady=group('샐러디','list_3?type=side','//*[contains(@class,"menu")]','공식 개별 사이드 목록')
    put(range(9,15),'사이드','공식 음료/사이드 페이지의 사이드 목록에 정확한 메뉴가 등재됨.',side_salady)
    slow_topping=group('슬로우캘리','co_id=menu','//div[contains(@class,"menu_box02")][div[contains(@class,"menu_box02_title") and normalize-space(.)="Topping"]]','공식 Topping 영양표')
    put([20,21]+list(range(23,38))+list(range(39,45)),'사이드','완성 포케와 분리된 정확한 토핑 상품으로 공식 Topping 표에 등재됨.',slow_topping)
    slow_side=group('슬로우캘리','co_id=menu','//div[contains(@class,"menu_box02")][div[contains(@class,"menu_box02_title") and normalize-space(.)="Side"]]','공식 Side 영양표')
    put([38,169,170,171],'사이드','공식 개별 Side 표에 등재된 수프.',slow_side)
    put([22],'샐러드·포케','공식 개별 설명에 실속 포케로 명시됨.')
    young=group('청년다방','menu_view','//div[contains(@class,"menu_view_topping")]','공식 명품 토핑 목록')
    put(range(64,70),'사이드','공식 토핑 DOM 안에서 개별 메뉴 이름을 확인함.',young)
    school=group('스쿨푸드','menu/menu.html','//section[@id="menu1"]','공식 마리 메뉴 목록; 공식 브랜드 소개는 마리를 프리미엄 김밥으로 명시함')
    put([70,71,74]+list(range(77,92)),'김밥','개별 메뉴가 마리 그룹에 등재되고 공식 브랜드 소개가 마리=김밥임을 명시함.',school)
    kim=group('김가네','sca=2','//body','공식 김밥류 목록')
    put([92],'김밥','개별 통새우롤이 공식 김밥류 페이지에 등재됨.',kim)
    gobong=group('고봉민김밥인','03_menu/02.php','//body','공식 김밥류 및 개별 통김밥 설명')
    put(range(93,97),'김밥','개별 공식 설명에서 매운 통김밥으로 명시됨.',gobong)
    yup=group('동대문엽기떡볶이','yup-menu','//div[@id="custdiv5"]','공식 토핑 그룹')
    put([204,206,208,209,211,212,214,216,217,219,220,221,222],'사이드','공식 토핑 탭의 정확한 개별 이름을 확인함.',yup)
    egg_side=group('에그드랍','eggdrop.com/','//div[@data-category="6"]','공식 SIDE 목록')
    put([175,179],'사이드','개별 상품이 공식 SIDE DOM 그룹에 등재됨.',egg_side)
    egg_drink=group('에그드랍','eggdrop.com/','//div[@data-category="7"]','공식 DRINK, COFFEE 목록')
    put([182],'음료','오렌지 썬라이즈가 공식 DRINK, COFFEE 그룹에 등재됨.',egg_drink)
    egg_sandwich=group('에그드랍','eggdrop.com/','//div[@data-category="2"]','공식 SANDWICH 목록')
    put([15],'샌드위치','개별 베이컨 딥 치즈 번이 공식 SANDWICH 그룹에 등재됨.',egg_sandwich)
    put([191,194,197],'베이커리','개별 공식 설명에서 베이글로 명시됨. 세트 상품과 구분함.')
    poke=next(p for p in pages if p['brand']=='포케올데이' and 'nutrition_info' in p['url'])
    raw=poke['text'];start=raw.index('nutrition_sort_item_wrap.wrap8');end=raw.index('</script>',start);drink=raw[start:end]
    def drinkproof(b):
        assert norm(b['name']) in norm(drink)
        return '공식 음료 영양표 wrap8 :: '+b['name']+' :: '+drink,poke['url'],poke['sha256']
    put(range(163,169),'음료','개별 품목이 공식 음료 영양표에 등재됨.',drinkproof)
    # Explicit descriptions and recognizable individual dish forms, not industry labels.
    for category,indices,reason in [
        ('사이드',[49,50,55,60,76,108,113,123,124,125,140,141,142,143,144,145,146,152,153,160],'개별 메뉴명 또는 공식 설명이 토핑·반찬·추가·사이드·고로케 형태를 명시함.'),
        ('음료',[51,109,110,114,115],'개별 공식 설명이 음료임을 명시함.'),
        ('분식',[73,75,97],'개별 공식 메뉴 설명/이름이 떡볶이 또는 만둣국임을 명시함.'),
        ('한식',[98,111,112,136,137,138,139,148,149,150,151,154,155,158],'개별 메뉴의 떡국·된장/비지·전·갈비찜·잡채·미역국·오징어볶음·너비아니 등 한식 요리 형태를 확인함.'),
        ('디저트',[99],'공식 설명이 호두강정 주전부리로 명시됨.'),
        ('사이드',[105],'공식 개별 설명이 새우튀김으로 명시됨.'),
        ('일식',[120,201],'개별 메뉴명이 오코노미야키·타코야끼를 명시함.'),
        ('샌드위치',[184,186],'공식 개별 메뉴명과 식빵 사이 햄치즈·잼을 담은 샌드 형태를 확인함.'),
        ('족발·보쌈',[226],'공식 개별 설명이 프리미엄 족발임을 명시함.'),
        ('치킨',[227],'공식 개별 설명이 치킨과 안심 순살 형태를 명시함.'),
    ]:put(indices,category,reason)
    p=json.loads((OUT/'source-captures.json').read_text(encoding='utf-8'));crispy=next(p for p in p if p['url']=='https://www.9922.co.kr/180')
    story=next(c for c in pages if c['url']=='http://www.schoolfood.co.kr/about/story.html')
    for i in [70,71,74]+list(range(77,92)):
        reviews[i]['additionalSources']=[dict(url=story['url'],sha256=story['sha256'],evidence='김밥의 프리미엄 시대 마리')]
    jaws=next(c for c in p if c['url']=='https://jawsfood.co.kr/uploads/product/20230403555976.png')
    put([63],'음료','공식 개별 메뉴에 연결된 제품 사진의 죠스쿨 Peach 병 음료를 실제 확인함.',lambda b:('죠스쿨 복숭아 병 음료 500ml 제품 사진',jaws['url'],jaws['sha256']))
    hansot=next(c for c in p if c['url']=='https://www.hsd.co.kr/api/menu/menu_list/3/10|POST')
    hansotjson=json.loads((ROOT/hansot['rawFile']).read_text(encoding='utf-8'))
    assert hansotjson['cate1Info']['name']=='사각도시락'
    assert any(g['title']=='동백' for sub in hansotjson['subdata'] for g in sub['goodsList'])
    put([135],'도시락','공식 사각도시락/모둠 공개 API에 동백 개별 메뉴가 정확히 등재됨.',lambda b:('사각도시락 > 모둠 > 동백 (goods idx 17)',hansot['url'],hansot['sha256']))
    for i,seq in [(207,1),(213,21),(205,52),(210,51)]:
        c=next(c for c in p if c['url'].endswith('Mseq='+str(seq)) and '/j_menureg|' in c['url'])
        data=json.loads((ROOT/c['rawFile']).read_text(encoding='utf-8'))['rows']
        assert data['Mname']==baseline[i]['name']
        if seq in (1,21):
            reason='공식 개별 선택 구성인 떡볶이/오뎅/반반/분모자가 모두 분식 범주이며 다른 업종 추정이 필요하지 않음.'
            evidence=data['Mname']+' :: '+data['Option1']
        else:
            reason='공식 개별 API가 연결한 밀키트 제품 사진을 실제 확인: 엽기/로제 떡볶이로 명시된 포장.'
            evidence=data['Mname']+' :: 공식 제품 사진에 엽기떡볶이/로제떡볶이 및 떡볶이떡 명시'
        put([i],'분식',reason,lambda b,c=c,evidence=evidence:(evidence,c['url'],c['sha256']))
        if seq in (51,52):
            from urllib.parse import quote
            picture=next(x for x in p if x['url']=='https://www.yupdduk.com'+quote(data['W_furl']))
            reviews[i]['additionalSources']=[dict(url=picture['url'],sha256=picture['sha256'],evidence='실제 확인한 떡볶이 밀키트 포장')]
    put([228],'치킨','공식 개별 상세 페이지가 Chicken Menu 크리스피골드로 명시함.',lambda b:('Chicken Menu :: 크리스피골드',crispy['url'],crispy['sha256']))
    mega=next(r for r in read(ROOT/'app/src/main/assets/fooddata/food_items.csv') if r['sourceFoodCode']=='D220-748000000-1364')
    put([8],'카페','K-FIND의 정확한 메가리카노 아이스 개별 행이 커피로 명시됨. 영양값은 서로 대체하지 않음.',lambda b:(mega['sourceFoodCode']+' :: '+mega['name'],'https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do','1ef3551f9a1d0ee87891d6306fa22bbd6a7ffcc90f2c70a4a184dbfe3fce6ea6'))
    images={45:'white_choco_macadamia_20210315021402115.jpg',46:'double_chocolate_chip_20210315021500386.jpg',47:'chocolate_chip_20210315021446423.jpg',48:'oatmeal_rasin_20210315021428482.jpg'}
    for i,filename in images.items():
        c=next(c for c in p if c['url']=='https://www.subway.co.kr/upload/menu/'+filename)
        put([i],'디저트','공식 개별 상세/목록에 연결된 사진을 실제 확인하여 모두 쿠키 형태임을 확정함.',lambda b,c=c:('공식 개별 메뉴 사진: 원형 구운 쿠키',c['url'],c['sha256']))
    isaac=next(c for c in p if 'idx=1060' in c['url'])
    assert '토스트' in text(isaac['text']) and '그릴드 불갈비' in text(isaac['text'])
    put([174],'토스트','공식 신메뉴 공지에서 그릴드 불갈비를 브라운브레드로 만든 토스트로 명시함.',lambda b:('공식 그릴드 불갈비 토스트 출시 공지',isaac['url'],isaac['sha256']))
    chae=next(c for c in pages if c['brand']=='채선당' and 'sub_1_2.asp' in c['url'])
    assert '샤브샤브 4종' in text(chae['text'])
    for i,num in [(157,2),(159,3),(161,1),(162,4)]:
        imageurl=f'https://www.chaesundang.co.kr/images/menu1_{num}.jpg'
        assert f'menu1_{num}.jpg' in chae['text']
        c=next(c for c in p if c['url']==imageurl)
        put([i],'샤브샤브','공식 점심 특선 샤브샤브 4종 목록과 각 웰빙 메뉴 사진의 이름·육수·얇은 생고기·채소 구성을 실제 확인함.',lambda b,c=c:('공식 샤브샤브 4종 목록 및 해당 웰빙 개별 사진',c['url']+'|'+chae['url'],c['sha256']+'|'+chae['sha256']))
    put([127],'족발·보쌈','공식 개별 세트 설명이 보쌈 수육 반+매운 화족발 반으로 구성되며 두 요리가 같은 taxonomy 범주임을 확인함.')
    # Every unresolved item is explicitly enumerated with a final reason.
    reasons=[
        ([0,1,2,3,4,5],'공식 K-FIND 원문은 수프 종류까지 명시하지만 현재 메뉴 taxonomy에 수프 전용 범주가 없고 개별 사이드 판매 그룹 근거가 없음.'),
        ([6,7,147],'과일·견과류 개별 상품으로 확인했으나 현재 메뉴 taxonomy에 해당 범주가 없어 디저트/사이드로 임의 대체하지 않음.'),
        ([16,17,18,19],'공식 밸런스 박스 페이지는 개별 이름·영양표를 제공하지만 도시락/포케/샐러드 중 어느 형태인지 명시하지 않음.'),
        ([52],'데친 오징어라는 개별 조리 형태는 확인되나 현재 taxonomy에서 한식/사이드의 단일 분류 근거가 없음.'),
        ([53,54],'공식 떡볶이 메뉴 그룹의 맵닭은 개별 조리 형태 설명이 없으며 떡볶이/치킨 여부를 이름의 닭만으로 결정할 수 없음.'),
        ([56,57,58,59,61,62],'공식 기타 그룹은 서로 다른 요리·재료를 포함함. 해당 개별 식재료의 토핑/완성 메뉴 여부가 명시되지 않음.'),
        ([72],'개별 함바그 스테이크는 확인되지만 현재 taxonomy에 스테이크 범주가 없어 햄버거/덮밥으로 대체하지 않음.'),
        ([100,223,225],'참기름/원두 상품은 확인되지만 현재 완성 메뉴 taxonomy에 조리용 식재료·원두 범주가 없음.'),
        ([101,102,103,121,122,126,128,129,130,132,133,134,176,177,180,183,185,187,188,189,190,192,195,196,198,199,202,203,215,218],'공식 메뉴의 세트/복합 구성에 서로 다른 종류가 포함되거나 구성 설명이 부족해 단일 대표 메뉴 category를 확정할 수 없음.'),
        ([104,106,107],'개별 볶음/마라비빔 명칭·설명은 확인했으나 현재 중식/한식/국수 등 단일 메뉴 형태를 확정할 근거가 부족함.'),
        ([116,118,119,200],'공식 롤 명칭·설명만으로 김밥/일식/샌드위치 형태를 확정할 수 없음.'),
        ([117],'우동과 떡볶이가 결합된 공식 설명은 확인했으나 현재 분식/국수·우동 중 단일 대표 분류 근거가 없음.'),
        ([131],'공공 관광의 특정 매장 우곱새 이름만 확보했으며 현재 개별 요리 설명과 공식 공통 메뉴 근거가 없음.'),
        ([156],'흑염소 진액은 개별 상품으로 확인되지만 음료/보충식품 형태를 확정할 용량·제품 유형 근거가 부족함.'),
        ([172,173],'공식 스마일 썹 그룹에 수프가 등재됨. 그룹은 여러 음식 종류를 혼합하며 수프 전용 taxonomy가 없어 단일 분류를 확정하지 않음.'),
        ([178,181,193],'공식 브런치 목록은 확인했으나 현재 taxonomy에 브런치 범주가 없고 단일 대표 요리 형태가 명시되지 않음.'),
        ([224],'공식 두부면 비빔볼 이름은 확인했으나 면 요리/샐러드 중 대표 형태를 확정할 개별 설명이 없음.'),
    ]
    for indices,reason in reasons:put(indices,None,reason)
    missing=set(range(229))-set(reviews)
    # These six reviews use additional official evidence, finalized separately.
    for i in sorted(missing):print('REVIEW_REQUIRED',i,baseline[i]['name'])
    result=dict(totalUnknownBefore=229,categoryAssigned=sum(bool(r['category']) for r in reviews.values()),categoryUnresolvable=sum(not r['category'] for r in reviews.values()),unreviewed=len(missing),countsByCategory=dict(Counter(r['category'] for r in reviews.values() if r['category'])),reviews=[reviews[i] for i in sorted(reviews)])
    (OUT/'menu-final-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    fixture=ROOT/'app/src/androidTest/assets/fixtures/full-menu-adjudication.csv'
    fixture.parent.mkdir(parents=True,exist_ok=True)
    with fixture.open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=['menuId','brand','name','category','status'],extrasaction='ignore')
        writer.writeheader();writer.writerows(result['reviews'])
    print(json.dumps({k:v for k,v in result.items() if k!='reviews'},ensure_ascii=False))
    return result
if __name__=='__main__':run()
