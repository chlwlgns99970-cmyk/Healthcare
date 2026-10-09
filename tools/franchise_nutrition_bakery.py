"""Exact official detail nutrition contracts for Paris Baguette and Tous Les Jours."""
import re
import unicodedata
from urllib.parse import urlparse, parse_qs
from lxml import html

FIELDS = ('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams', 'sodiumMilligrams')

def norm(value):
    return re.sub(r'\s+', '', unicodedata.normalize('NFKC', value)).casefold()

def numeric(value):
    value = value.strip()
    return float(value) if re.fullmatch(r'\d+(?:\.\d+)?', value) else None

def empty():
    return dict.fromkeys(FIELDS + ('servingAmount', 'servingUnit', 'servingDescription'))

def parse_paris(menu, payload):
    assert menu['brand'] == '파리바게뜨'
    root = html.fromstring(payload)
    canonical = root.xpath('//link[@rel="canonical"]/@href')
    assert len(canonical) == 1 and urlparse(canonical[0]).path.rstrip('/') == menu['externalId']
    titles = root.xpath('//h1[@class="product-name"]')
    assert len(titles) == 1 and norm(titles[0].text_content()) == norm(menu['name']), 'Official product title changed'
    blocks = root.xpath('//div[contains(concat(" ",normalize-space(@class)," ")," product-nutrition ")]')
    assert len(blocks) == 1, 'No unique published nutrition block'
    text = ' · '.join(' '.join(p.text_content().split()) for p in blocks[0].xpath('.//p'))
    result = empty()
    # A per-100g panel must not accidentally use the whole package mass.
    basis = re.search(r'총\s*내용량\s*:\s*(\d+(?:\.\d+)?)\s*(g|ml)', text)
    energy = re.search(r'총\s*내용량당\s*칼로리\s*\(kcal\)\s*:\s*(\d+(?:\.\d+)?)', text, re.I)
    per100 = re.search(r'100\s*(g|ml)당\s*칼로리\s*\(kcal\)\s*:\s*(\d+(?:\.\d+)?)', text, re.I)
    per_serving = re.search(r'1회\s*제공\s*칼로리\s*:\s*(\d+(?:\.\d+)?)\s*kcal', text, re.I)
    if not energy and per100:
        assert float(per100[2]) <= 900, 'Official per-100g kcal exceeds physical upper bound; conflicting unit label withheld'
        result.update(energyKcal=float(per100[2]), servingAmount=100.0, servingUnit=per100[1], servingDescription='공식 100'+per100[1]+' 기준')
    if not energy and not per100 and per_serving:
        # Keep the explicit one serving; do not use whole cake/package grams.
        result.update(energyKcal=float(per_serving[1]),servingAmount=1,servingUnit='인분',
                      servingDescription='공식 1회 제공량 기준 · 제공 중량 미공개')
    if energy:
        result['energyKcal'] = float(energy[1])
        if basis and float(basis[1]) > 0:
            result.update(servingAmount=float(basis[1]), servingUnit=basis[2], servingDescription='공식 총 내용량 기준')
    if result['energyKcal'] is not None:
        for field, label, unit in [('carbohydrateGrams','탄수화물','g'),('proteinGrams','단백질','g'),('fatGrams','지방','g'),('sodiumMilligrams','나트륨','mg')]:
            match = re.search(r'(?<![가-힣])'+label+r'\s*\('+unit+r'\)\s*:\s*([^·]+)', text)
            if match:
                token = re.fullmatch(r'\s*(\d+(?:\.\d+)?)\s*(?:'+unit+r')?\s*',match[1])
                if token: result[field] = float(token[1])
    assert any(result[f] is not None for f in FIELDS), 'Published nutrition format not supported'
    return result

def parse_tlj(menu, payload):
    assert menu['brand'] == '뚜레쥬르'
    root = html.fromstring(payload)
    canonical = root.xpath('//link[@rel="canonical"]/@href')
    assert len(canonical) == 1 and parse_qs(urlparse(canonical[0]).query).get('prod_num') == [menu['externalId']]
    titles = root.xpath('//span[@class="name"]')
    assert len(titles) == 1 and norm(titles[0].text_content()) == norm(menu['name']), 'Official product title changed or catalog title truncated'
    tables = root.xpath('//table[caption="영양성분"]')
    assert len(tables) == 1, 'No unique published nutrition table'
    table = tables[0]
    result = empty()
    for field, label in [('energyKcal','열량(kcal)'),('carbohydrateGrams','탄수화물(g/%)'),('proteinGrams','단백질(g/%)'),('fatGrams','지방(g/%)'),('sodiumMilligrams','나트륨(mg/%)')]:
        values = table.xpath('.//tbody/tr[th="'+label+'"] /td')
        assert len(values) <= 1, 'Duplicate official nutrient'
        if values: result[field] = numeric(values[0].text_content().split('/')[0])
    # Explicit body header weight is authoritative; the legacy table summary
    # uses a hardcoded 108g even for other products and is deliberately ignored.
    weights = []
    for cell in table.xpath('.//thead//td'):
        match = re.fullmatch(r'중량\(g\)\s*(\d+(?:\.\d+)?)', ' '.join(cell.text_content().split()))
        if match: weights.append(float(match[1]))
    assert len(weights) <= 1
    if weights and weights[0] > 0:
        result.update(servingAmount=weights[0], servingUnit='g', servingDescription='공식 1회 제공량 기준')
    assert any(result[f] is not None for f in FIELDS), 'Published nutrition values absent'
    return result
