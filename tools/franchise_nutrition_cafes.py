"""Strict official cafe nutrition tables; no cup-to-serving conversions."""
import re
import unicodedata
from lxml import html

FIELDS=('energyKcal','carbohydrateGrams','proteinGrams','fatGrams','sodiumMilligrams')
def clean(s):return re.sub(r'\s+',' ',s).strip()
def norm(s):return re.sub(r'\s+','',unicodedata.normalize('NFKC',s)).casefold()
def root(payload):return html.fromstring(payload.decode('utf-8') if isinstance(payload,bytes) else payload)
def blank():return dict.fromkeys(FIELDS, None)|dict(servingAmount=None,servingUnit=None,servingDescription='')
def numeric(s,unit=''):
    m=re.fullmatch(r'\s*(\d+(?:\.\d+)?)\s*'+re.escape(unit)+r'\s*',s.replace(',',''))
    return float(m[1]) if m else None
def measured(s,label,unit):
    ms=re.findall(r'(?<![가-힣])'+label+r'\s*:?\s*(\d+(?:\.\d+)?)\s*'+unit+r'(?![a-z])',s)
    return float(ms[0]) if len(ms)==1 else None
def basis(s):
    ms=re.findall(r'(?:1회\s*)?제공량\s*[:：]?\s*(\d+(?:\.\d+)?)\s*(ml|g)',s)
    return (float(ms[0][0]),ms[0][1]) if len(ms)==1 else (None,None)

def parse_mega(menu,payload):
    assert menu['brand']=='메가MGC커피'
    matches=[]
    for n in root(payload).xpath('//ul[@id="menu_list"]/li'):
        names=n.xpath('./a//div[contains(@class,"cont_text_title")]//b')
        labels=n.xpath('./a//div[contains(@class,"cont_gallery_list_label")]')
        if len(names)!=1:continue
        name=clean(names[0].text_content())+(' ('+clean(labels[0].text_content())+')' if labels else '')
        if norm(name)==norm(menu['name']):matches.append(n)
    assert len(matches)==1,'Exact Mega product and HOT/ICE variant not unique'
    n=matches[0];modal=n.xpath('./div[@class="inner_modal"]');assert len(modal)==1
    text=clean(modal[0].text_content());out=blank()
    out['energyKcal']=measured(text,'1회 제공량','kcal')
    for field,label,unit in [('proteinGrams','단백질','g'),('sodiumMilligrams','나트륨','mg'),('carbohydrateGrams','탄수화물','g')]:out[field]=measured(text,label,unit)
    # Separate capacity field says 컵용량; it is not serving quantity.
    values=modal[0].xpath('./div[@class="cont_text_box"]/div[@class="cont_text"][1]/div[@class="cont_text_inner"][1]')
    if len(values)==1:
        t=clean(values[0].text_content());m=re.fullmatch(r'(\d+(?:\.\d+)?)\s*(g|ml)',t)
        if m:out.update(servingAmount=float(m[1]),servingUnit=m[2])
    if out['servingAmount'] is None and out['energyKcal'] is not None:
        # This product's calorie label explicitly says 1회 제공량. Preserve
        # that one serving without treating cup capacity as consumed ml.
        out.update(servingAmount=1,servingUnit='인분',servingDescription='공식 1회 제공량 기준 · 컵용량을 섭취 ml로 환산하지 않음')
    return out

def parse_hollys(menu,payload):
    assert menu['brand']=='할리스' and menu['externalId'].isdigit()
    r=root(payload);n=r.xpath('//*[@id=$id]',id='menuView1_'+menu['externalId']);assert len(n)==1
    names=n[0].xpath('.//div[@class="menu_detail"]/p/span');assert len(names)==1 and norm(names[0].text_content())==norm(menu['name'])
    ns=r.xpath('//*[@id=$id]',id='menuView2_'+menu['externalId']);assert len(ns)==1
    n=ns[0];rows=n.xpath('.//tbody/tr');assert len(rows)==1,'HOT/ICED or size ambiguity cannot assign unqualified menu'
    headers=[clean(x.text_content()) for x in n.xpath('.//thead/tr/th')];cells=[clean(x.text_content()) for x in rows[0].xpath('./th|./td')];assert len(headers)==len(cells)
    out=blank();values=dict(zip(headers,cells))
    for field,label,unit in [('energyKcal','칼로리','kcal'),('proteinGrams','단백질','g'),('sodiumMilligrams','나트륨','mg'),('carbohydrateGrams','탄수화물','g'),('fatGrams','지방','g')]:
        if label in values:out[field]=numeric(values[label].split('(')[0],unit)
    caption=clean(' '.join(n.xpath('.//div[@class="tableInfo03"]//text()')))
    amount,unit=basis(caption)
    if amount:out.update(servingAmount=amount,servingUnit=unit,servingDescription=caption)
    return out

def parse_dalkomm(menu,payload):
    assert menu['brand']=='달콤'
    matches=[n for n in root(payload).xpath('//div[contains(concat(" ",normalize-space(@class)," ")," item ")][contains(concat(" ",normalize-space(@class)," ")," menu ")]') if any(norm(x.text_content())==norm(menu['name']) for x in n.xpath('./div[@class="text-wrap"]/p[@class="title"]'))]
    assert len(matches)==1,'Exact Dalkomm menu not unique';n=matches[0]
    s=clean(' '.join(n.xpath('.//ul[@class="spec"]//text()')))
    assert len(re.findall(r'\[Size\s*:',s))<=1,'Multiple published sizes require variant selection'
    out=blank();out['energyKcal']=measured(s,r'(?:칼로리\s*)?','kcal')
    for field,label,unit in [('proteinGrams','단백질','g'),('sodiumMilligrams','나트륨','mg'),('carbohydrateGrams','탄수화물','g'),('fatGrams','지방','g')]:out[field]=measured(s,label,unit)
    amount,unit=basis(s)
    if amount:out.update(servingAmount=amount,servingUnit=unit)
    sizes=re.findall(r'\[Size\s*:\s*([^]]+)\]',s)
    if sizes:out['servingDescription']='공식 '+sizes[0]+' 기준'
    return out

def parse_paik(menu,payload):
    assert menu['brand']=='빽다방'
    matches=[]
    for h in root(payload).xpath('//h3[@class="font-bl"]'):
        if norm(h.text_content())==norm(menu['name']):
            n=h.getparent();out=blank()
            for row in n.xpath('.//ul[@class="ingredient_table"]/li'):
                cells=row.xpath('./div');assert len(cells)==2
                label=clean(cells[0].text_content());value=clean(cells[1].text_content())
                for field,key in [('energyKcal','칼로리'),('proteinGrams','단백질'),('sodiumMilligrams','나트륨'),('carbohydrateGrams','탄수화물'),('fatGrams','지방')]:
                    if re.fullmatch(key+r'\s*(?:\([^)]*\))?',label):out[field]=numeric(value)
            matches.append(out)
    unique={tuple(x.items()) for x in matches};assert len(unique)==1,'Exact Paik variant not unique or inconsistent duplicate'
    return matches[0]

PARSERS={'mega-official-nutrition':parse_mega,'hollys-official-nutrition':parse_hollys,'dalkomm-official-nutrition':parse_dalkomm,'paik-official-nutrition':parse_paik}

def parse_gongcha(menu,payload):
    assert menu['brand']=='공차'
    r=root(payload);titles=r.xpath('//div[@class="menu-detail-conts"]//p[contains(@class,"t1")]')
    assert len(titles)==1 and norm(titles[0].text_content())==norm(menu['name'])
    blocks=r.xpath('//div[@class="menu-detail-conts"]//div[contains(concat(" ",normalize-space(@class)," ")," table-item ")]')
    assert len(blocks)==1,'HOT/ICE or size tables require an explicit variant'
    block=blocks[0];variant=clean(' '.join(block.xpath('./div[@class="table-title"]//text()')))
    assert not variant,'Unqualified name cannot assume a declared temperature/size variant'
    headers=[norm(x.text_content()) for x in block.xpath('.//thead/tr/th')]
    rows=block.xpath('.//tbody/tr');assert len(rows)==1
    cells=[clean(x.text_content()) for x in rows[0].xpath('./td')];assert len(headers)==len(cells)
    values=dict(zip(headers,cells));out=blank()
    for field,label in [('energyKcal','열량(kcal)'),('proteinGrams','단백질(g)'),('fatGrams','지방(g)'),('carbohydrateGrams','탄수화물(g)'),('sodiumMilligrams','나트륨(mg)')]:
        out[field]=numeric(values.get(norm(label),''))
    for unit in ('g','ml'):
        amount=numeric(values.get(norm('1회 제공량('+unit+')'),''))
        if amount is not None and amount>0:out.update(servingAmount=amount,servingUnit=unit,servingDescription='공식 1회 제공량 기준')
    assert out['energyKcal'] is not None
    return out

PARSERS['gongcha-official-nutrition']=parse_gongcha

def parse_pascucci(menu,payload):
    assert menu['brand']=='파스쿠찌' and menu['externalId'].isdigit()
    r=root(payload);titles=r.xpath('//div[@class="productDetail"]//h1/strong')
    assert len(titles)==1 and norm(titles[0].text_content())==norm(menu['name'])
    options=r.xpath('//select[@name="sizeGubun"]/option')
    assert len(options)<=1,'Multiple serving sizes cannot be assigned to an unqualified menu'
    values={}
    for row in r.xpath('//ul[@class="nutri"]/li'):
        labels=row.xpath('./span');numbers=row.xpath('./p')
        assert len(labels)==len(numbers)==1
        label=norm(labels[0].text_content());assert label not in values
        values[label]=clean(numbers[0].text_content())
    out=blank()
    for field,label,unit in [('energyKcal','kcal','kcal'),('proteinGrams','단백질','g'),('sodiumMilligrams','나트륨','mg'),('carbohydrateGrams','탄수화물','g'),('fatGrams','지방','g')]:
        out[field]=numeric(values.get(norm(label),''),unit)
    amount=re.fullmatch(r'(\d+(?:\.\d+)?)\s*(g|ml)',values.get(norm('총 내용량'),''))
    if amount and float(amount[1])>0:out.update(servingAmount=float(amount[1]),servingUnit=amount[2],servingDescription='공식 총 내용량 기준')
    assert out['energyKcal'] is not None
    return out

PARSERS['pascucci-official-nutrition']=parse_pascucci
