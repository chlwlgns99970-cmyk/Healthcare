"""Official menu nutrition with explicit nullable serving units."""
import json
import re
import unicodedata
from lxml import html

def slow_finished_rows(payload):
    root=html.fromstring(payload)
    headers=[' '.join(x.itertext()).strip() for x in root.xpath('//div[@class="menu_table_top rltv"]/div')]
    assert all(any(label in h for h in headers) for label in ('중량','열량','탄수화물','지방','단백질','나트륨'))
    base_names={norm(' '.join(x.itertext())) for x in root.xpath('//div[contains(concat(" ",normalize-space(@class)," ")," menu_title ")]/p')}
    result=[]
    for row in root.xpath('//div[contains(concat(" ",normalize-space(@class)," ")," menu_table_item ")]'):
        values=[' '.join(x.itertext()).strip() for x in row.xpath('./div[contains(@class,"menu_table_td")]')]
        assert len(values)==8
        match=re.fullmatch(r'(.+?)\s*\((현미밥&샐러드|메밀면&샐러드|샐러드 ONLY)\)',values[0])
        if match and norm(match[1]) in base_names:
            numbers=[number(v) for v in values[1:]];assert all(n is not None for n in numbers)
            result.append((values[0],numbers))
    assert result and len({norm(n) for n,_ in result})==len(result)
    return result

def parse_slowcali(menu,payload):
    assert menu['brand']=='슬로우캘리'
    rows=[values for name,values in slow_finished_rows(payload) if norm(name)==norm(menu['name'])]
    assert len(rows)==1,'Finished base variant must be explicit, never assumed from generic name'
    amount,kcal,sodium,carbs,sugar,fat,protein=rows[0]
    return dict(energyKcal=kcal,servingAmount=amount,servingUnit='g',carbohydrateGrams=carbs,
                fatGrams=fat,proteinGrams=protein,sodiumMilligrams=sodium,
                servingDescription='공식 선택 베이스 포함 완성 메뉴 기준',matchedBy='EXACT_FINISHED_NAME_AND_BASE')

def parse_isaac_not_published(menu,payload):
    assert menu['brand']=='이삭토스트'
    text=' '.join(html.fromstring(payload).itertext())
    assert '이삭토스트 영양성분이 어떻게 되나요?' in text
    assert '별도 안내 드릴 수 있는 자료가 없는 점' in text
    assert '영양성분표 및 칼로리 표기 대상 업종이 아닌 관계로' in text
    return dict.fromkeys(('energyKcal','proteinGrams','sodiumMilligrams','carbohydrateGrams','fatGrams','servingAmount','servingUnit')) | dict(servingDescription='',notPublishedConfirmed=True)

def norm(value):
    return re.sub(r'[\s®™]', '', unicodedata.normalize('NFKC',value).casefold())

def number(value):
    s=str(value).split('(')[0].strip()
    return float(s) if re.fullmatch(r'\d+(?:\.\d+)?',s) else None

def parse_burgerking(menu,payload):
    assert menu['brand']=='버거킹'
    data=json.loads(payload)
    assert data['header']['result'] is True and data['header']['trcode']=='BKR0347'
    rows=data['body']['allNutrientList'];assert rows and isinstance(rows,list)
    matches=[r for r in rows if norm(r['menuNm'])==norm(menu['name'])]
    assert len(matches)==1,'Exact Burger King name/variant not unique'
    row=matches[0]
    amount=re.fullmatch(r'(\d+(?:\.\d+)?)\s*(g|ml)',str(row.get('weight','')))
    # The official table labels weight as g/ml, with no per-row unit. Never
    # choose a unit from food/drink appearance or convert this ambiguous value.
    return dict(energyKcal=number(row.get('calory')),proteinGrams=number(row.get('protein')),
                sodiumMilligrams=number(row.get('natrium')),carbohydrateGrams=None,fatGrams=None,
                servingAmount=float(amount[1]) if amount else None,servingUnit=amount[2] if amount else None,
                servingDescription='',matchedBy='EXACT_BRAND_NAME_AND_VARIANT',
                publishedAmbiguousWeight=str(row.get('weight','')),publishedWeightHeader='g/ml')
