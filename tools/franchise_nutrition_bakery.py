"""Exact official detail nutrition contracts for Paris Baguette and Tous Les Jours."""
import re
import hashlib
import unicodedata
from urllib.parse import urlparse, parse_qs, quote
from lxml import html

TLJ_LIST_URL = 'https://www.tlj.co.kr/product/result.asp'
TLJ_CROSSWALK_KEYS = {'matchedBy', 'listSourceURL', 'listSourceSha256', 'officialDetailFullName', 'listingDisplayName'}

FIELDS = ('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams', 'sodiumMilligrams')

def norm(value):
    return re.sub(r'\s+', '', unicodedata.normalize('NFKC', value)).casefold()

def numeric(value):
    value = value.strip()
    return float(value) if re.fullmatch(r'\d+(?:\.\d+)?', value) else None

def empty():
    return dict.fromkeys(FIELDS + ('servingAmount', 'servingUnit', 'servingDescription'))

def official_product_path(value):
    """Compare UTF-8 URL spellings without decoding reserved path delimiters."""
    path = quote(urlparse(value).path.rstrip('/'), safe='/%-._~')
    return re.sub(r'%[0-9a-fA-F]{2}', lambda match: match[0].upper(), path)

def parse_paris(menu, payload):
    assert menu['brand'] == '파리바게뜨'
    root = html.fromstring(payload)
    canonical = root.xpath('//link[@rel="canonical"]/@href')
    assert len(canonical) == 1 and urlparse(canonical[0]).hostname in ('www.paris.co.kr', 'paris.co.kr')
    identity = urlparse(menu['externalId'])
    assert not any((identity.scheme, identity.netloc, identity.query, identity.fragment)), 'Product ID must remain an exact path'
    assert official_product_path(canonical[0]) == official_product_path(menu['externalId']), 'Official product URL identity changed'
    titles = root.xpath('//h1[@class="product-name"]')
    assert len(titles) <= 1, 'Duplicate legacy product title'
    # The second official template uses an empty class and an adjacent excerpt.
    # Navigation and arbitrary h1 nodes are not product identity evidence.
    titles += root.xpath('//h1[@class and normalize-space(@class)=""][following-sibling::*[1][self::div[contains(concat(" ",normalize-space(@class)," ")," excerpt ")]]]')
    assert 1 <= len(titles) <= 2 and all(norm(t.text_content()) == norm(menu['name']) for t in titles), 'Official product title changed'
    blocks = root.xpath('//div[contains(concat(" ",normalize-space(@class)," ")," product-nutrition ")]')
    texts = [' · '.join(' '.join(p.text_content().split()) for p in b.xpath('.//p')) for b in blocks]
    for block in root.xpath('//div[contains(concat(" ",normalize-space(@class)," ")," product-info ") and not(contains(concat(" ",normalize-space(@class)," ")," product-nutrition "))]'):
        candidate = ' · '.join(' '.join(p.text_content().split()) for p in block.xpath('./p'))
        # Ingredient panels share this class. Require a published calorie basis.
        if re.search(r'(?:총\s*내용량당|100\s*(?:g|ml)당)\s*칼로리\s*\(kcal\)\s*:', candidate, re.I):
            texts.append(candidate)
    assert texts and len({norm(t) for t in texts}) == 1, 'No unique published nutrition block'
    text = texts[0]
    result = empty()
    result.update(sugarsGrams=None, saturatedFatGrams=None)
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
        for field, label, unit in [('carbohydrateGrams','탄수화물','g'),('proteinGrams','단백질','g'),('fatGrams','지방','g'),('sodiumMilligrams','나트륨','mg'),('sugarsGrams','당류','g'),('saturatedFatGrams','포화지방','g')]:
            match = re.search(r'(?<![가-힣])'+label+r'\s*\('+unit+r'\)\s*:\s*([^·]+)', text)
            if match:
                token = re.fullmatch(r'\s*(\d+(?:\.\d+)?)\s*(?:'+unit+r')?\s*',match[1])
                if token: result[field] = float(token[1])
    assert any(result[f] is not None for f in FIELDS), 'Published nutrition format not supported'
    return result

class TljListingRequired(AssertionError):
    """A validated detail identity needs the optional listing title bridge."""


def tlj_listing_crosswalk(menu, detail_title, listing_payload):
    """Exact official li.item_wrap / span.name / img alt / viewDetail contract."""
    assert isinstance(listing_payload, bytes) and listing_payload, 'Official listing bytes required'
    root = html.fromstring(listing_payload)
    cards = []
    for card in root.xpath('//li[contains(concat(" ",normalize-space(@class)," ")," item_wrap ")]'):
        links = [link for link in card.xpath('.//a/@href') if link != '#']
        ids = [re.fullmatch(r"javascript:viewDetail\('([0-9]+)'\)\s*;?", link.strip()) for link in links]
        if any(match and match[1] == menu['externalId'] for match in ids):
            cards.append((card, ids))
    assert len(cards) == 1, 'No unique listing product-ID card'
    card, ids = cards[0]
    assert ids and all(match and match[1] == menu['externalId'] for match in ids), 'Conflicting listing product links'
    names = card.xpath('.//span[contains(concat(" ",normalize-space(@class)," ")," name ")]')
    assert names and all(norm(node.text_content()) == norm(menu['name']) for node in names), 'Listing display title changed'
    alts = card.xpath('.//img/@alt')
    assert alts and len({norm(value) for value in alts}) == 1 and all(norm(value) == norm(detail_title) for value in alts), 'Listing full image-alt title changed'
    return dict(matchedBy='tlj-listing-display-id-alt', listSourceURL=TLJ_LIST_URL,
                listSourceSha256=hashlib.sha256(listing_payload).hexdigest(),
                officialDetailFullName=detail_title, listingDisplayName=menu['name'])


def parse_tlj(menu, payload, listing_payload=None):
    assert menu['brand'] == '뚜레쥬르'
    root = html.fromstring(payload)
    canonical = root.xpath('//link[@rel="canonical"]/@href')
    assert len(canonical) == 1
    uri = urlparse(canonical[0])
    assert uri.scheme == 'https' and uri.netloc == 'www.tlj.co.kr' and not uri.username and not uri.password and uri.path == '/product/detail.asp' and not uri.fragment
    assert parse_qs(uri.query, keep_blank_values=True).get('prod_num') == [menu['externalId']]
    titles = root.xpath('//span[@class="name"]')
    assert len(titles) == 1, 'No unique official detail title'
    detail_title = titles[0].text_content().strip()
    crosswalk = {}
    if norm(detail_title) != norm(menu['name']):
        if listing_payload is None:
            raise TljListingRequired('Official product title changed or catalog title truncated')
        crosswalk = tlj_listing_crosswalk(menu, detail_title, listing_payload)
    tables = root.xpath('//table[caption="영양성분"]')
    assert len(tables) == 1, 'No unique published nutrition table'
    table = tables[0]
    result = empty()
    result.update(crosswalk)
    result.update(sugarsGrams=None, saturatedFatGrams=None)
    for field, label in [('energyKcal','열량(kcal)'),('carbohydrateGrams','탄수화물(g/%)'),('proteinGrams','단백질(g/%)'),('fatGrams','지방(g/%)'),('sodiumMilligrams','나트륨(mg/%)')]:
        values = table.xpath('.//tbody/tr[th="'+label+'"] /td')
        assert len(values) <= 1, 'Duplicate official nutrient'
        if values: result[field] = numeric(values[0].text_content().split('/')[0])
    for field, label in [('sugarsGrams', '당류(g/%)'), ('saturatedFatGrams', '포화지방(g/%)')]:
        values = table.xpath('.//tbody/tr[th="'+label+'"] /td')
        assert len(values) <= 1, 'Duplicate official nutrient'
        if values:
            # Only the gram amount before the slash; never the daily percentage.
            amount = values[0].text_content().split('/')[0].strip()
            match = re.fullmatch(r'(\d+(?:\.\d+)?)\s*g?', amount)
            if match: result[field] = float(match[1])
    # Explicit body header weight is authoritative; the legacy table summary
    # uses a hardcoded 108g even for other products and is deliberately ignored.
    weights = []
    for cell in table.xpath('.//thead//td | .//tbody//td'):
        text = ' '.join(cell.text_content().split())
        if not text.startswith('중량('):
            continue
        if re.fullmatch(r'중량\((g|ml)\)', text):
            continue
        match = re.fullmatch(r'중량\((g|ml)\)\s*(\d+(?:\.\d+)?)\s*(g|ml)?', text)
        assert match, 'Ambiguous official weight unit'
        weights.append((float(match[2]), match[3] or match[1]))
    assert len(weights) <= 1
    if weights and weights[0][0] > 0:
        result.update(servingAmount=weights[0][0], servingUnit=weights[0][1], servingDescription='공식 1회 제공량 기준')
    assert any(result[f] is not None for f in FIELDS), 'Published nutrition values absent'
    return result
