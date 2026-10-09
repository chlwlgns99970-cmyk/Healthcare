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
    out=blank();out['energyKcal']=measured(s,'(?:칼로리\s*)?','kcal')
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
