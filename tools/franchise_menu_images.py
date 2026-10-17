"""Reviewed labels visible in official images, guarded by exact inventories/hashes.

No OCR guesses or visual product-to-ID matching. Image changes stop publication
for review. This adapter is scoped to the inspected official pages only.
"""
from pathlib import Path
import hashlib
import json
import re
import urllib.request
from urllib.parse import urlparse, urljoin
from lxml import html

INDEX = Path(__file__).resolve().parents[1] / 'data-source/franchise-sync/reviewed-menu-images.json'
SCOPES = {('캠토토스트', 'https://camtotoast.com/45'): '음료',
          ('캠토토스트', 'https://camtotoast.com/46'): '사이드',
          ('우지커피', 'https://www.oozycoffee.com/Vision'): 'NEW',
          ('청년다방', 'https://youngdabang.com/html/menu_1.html'): '메뉴'}
MAX_IMAGE_BYTES = 2_000_000


class ImageReviewRequired(ValueError):
    review_required = True


def require(condition, reason):
    if not condition:
        raise ImageReviewRequired(reason)


def inventory(brand, markup):
    scope = (brand['name'], brand['sourceUrl'])
    require(scope in SCOPES, 'UNREVIEWED_IMAGE_SOURCE')
    root = html.fromstring(markup)
    if scope[0] == '캠토토스트':
        nodes = root.xpath('//main//div[contains(concat(" ",normalize-space(@class)," ")," _img_box ")]/img')
        pairs = [(node.get('id'), node.get('src')) for node in nodes]
    elif scope[0] == '청년다방':
        nodes = root.xpath('//*[@id="menubook-popup"]//*[contains(concat(" ",normalize-space(@class)," ")," swiper-slide ")]/img')
        pairs = []
        for node in nodes:
            url = urljoin(scope[1], node.get('src', ''))
            uri = urlparse(url)
            require(uri.scheme == 'https' and uri.netloc == 'youngdabang.com'
                    and re.fullmatch(r'/img/page/menu/01/menu_table[1-9][0-9]*\.jpg', uri.path)
                    and not uri.query and not uri.fragment, 'UNAPPROVED_IMAGE_HOST_OR_PATH')
            pairs.append((uri.path.rsplit('/', 1)[-1], url))
    else:
        nodes = root.xpath('//*[contains(concat(" ",normalize-space(@class)," ")," item_gallary ")]//*[@data-bg]')
        pairs = []
        for node in nodes:
            match = re.fullmatch(r'url\((https://cdn\.imweb\.me/thumbnail/[^?()]+)\)', node.get('data-bg', ''))
            require(match is not None, 'OFFICIAL_IMAGE_REFERENCE_CHANGED')
            pairs.append((node.get('id'), match[1]))
    require(1 <= len(pairs) <= 64 and len({key for key, _ in pairs}) == len(pairs), 'OFFICIAL_IMAGE_INVENTORY_CHANGED')
    for key, url in pairs:
        if scope[0] == '청년다방':
            continue  # Validated the exact official host/path above, separately from the CDN contract.
        require(isinstance(key, str) and bool(re.fullmatch(r'(?:img_w[0-9a-f]+|gal_item_\d+(?:_img)?)', key)), 'OFFICIAL_IMAGE_CARD_ID_CHANGED')
        uri = urlparse(url or '')
        require(uri.scheme == 'https' and uri.netloc == 'cdn.imweb.me' and uri.path.startswith('/thumbnail/')
                and not uri.query and not uri.fragment, 'UNAPPROVED_IMAGE_HOST_OR_PATH')
    return dict(pairs)


def policy(brand, markup):
    current = inventory(brand, markup)
    index = json.loads(INDEX.read_text(encoding='utf-8'))
    require(index.get('schemaVersion') == 1, 'REVIEWED_IMAGE_SCHEMA_CHANGED')
    sources = [s for s in index['sources'] if s['brand'] == brand['name'] and s['sourceUrl'] == brand['sourceUrl']]
    require(len(sources) == 1, 'REVIEWED_IMAGE_POLICY_MISSING')
    items = sources[0]['items']
    require(len(items) == len(current) and len({item['id'] for item in items}) == len(items), 'OFFICIAL_IMAGE_INVENTORY_CHANGED')
    require(current == {item['id']: item['url'] for item in items}, 'OFFICIAL_IMAGE_INVENTORY_CHANGED')
    for item in items:
        require(bool(re.fullmatch('[a-f0-9]{64}', item['sha256'])), 'INVALID_REVIEWED_IMAGE_HASH')
        require(isinstance(item['names'], list) and all(isinstance(n, str) and 1 <= len(n) <= 160
                and not any(ord(c) < 32 for c in n) for n in item['names']), 'INVALID_REVIEWED_IMAGE_LABEL')
    return items


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def fetch_image(url):
    # URL has passed the scoped official HTTPS host/path contract above. No credentials,
    # cookie handling, TLS exceptions or redirect following.
    request = urllib.request.Request(url, headers={'User-Agent': 'HealthcareMenuAudit/1.0 (official image labels)'})
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=8) as response:
        require(response.status == 200 and response.url == url, 'OFFICIAL_IMAGE_RESPONSE_CHANGED')
        data = response.read(MAX_IMAGE_BYTES + 1)
        require(0 < len(data) <= MAX_IMAGE_BYTES, 'OFFICIAL_IMAGE_SIZE_LIMIT')
        return data


def enrich(brand, markup, fetcher=None):
    items = policy(brand, markup)
    get = fetcher or fetch_image
    hashes = {}
    for item in items:
        data = get(item['url'])
        require(isinstance(data, bytes) and 0 < len(data) <= MAX_IMAGE_BYTES, 'OFFICIAL_IMAGE_SIZE_LIMIT')
        digest = hashlib.sha256(data).hexdigest()
        require(digest == item['sha256'], 'OFFICIAL_IMAGE_CHANGED_REVIEW_REQUIRED')
        hashes[item['id']] = digest
    return markup + '<script id="recorded-reviewed-image-hashes" type="application/json">' + json.dumps(hashes) + '</script>'


def rows(brand, markup):
    items = policy(brand, markup)
    proof = html.fromstring(markup).xpath('//script[@id="recorded-reviewed-image-hashes"][@type="application/json"]/text()')
    require(len(proof) == 1, 'ACTUAL_OFFICIAL_IMAGE_HASHES_REQUIRED')
    require(json.loads(proof[0]) == {item['id']: item['sha256'] for item in items}, 'OFFICIAL_IMAGE_HASH_PROOF_MISMATCH')
    result = []
    for item in items:
        for name in item['names']:
            # The widget/gallery ID is not a product/variant ID. Keep the
            # existing name/category identity path instead of inventing one.
            result.append((name, SCOPES[(brand['name'], brand['sourceUrl'])], brand['sourceUrl'], ''))
    return list(dict.fromkeys(result))
