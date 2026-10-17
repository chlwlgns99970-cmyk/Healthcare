"""Official nutrition refresh, separate from menu identity and legacy app fields.

Only reviewed exact-ID/name contracts can fill values. Missing sources are never
evidence that a brand does not publish nutrition. Existing facts survive errors.
"""
import concurrent.futures
import copy
import threading
import hashlib
import io
import functools
import json
import math
import re
import unicodedata
import urllib.request
import urllib.error
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from lxml import html

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / 'data-source/franchise-sync/nutrition-sources.json'
REVIEWED = ROOT / 'data-source/franchise-sync/verified-nutrition-snapshot.json'
STATUSES = {'NUTRITION_COMPLETE', 'NUTRITION_PARTIAL', 'NUTRITION_NOT_PUBLISHED',
            'NUTRITION_SOURCE_FOUND_UNMATCHED', 'NUTRITION_SOURCE_MISSING'}
FIELDS = ('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams', 'sodiumMilligrams')
OPTIONAL_FIELDS = ('sugarsGrams', 'saturatedFatGrams')
REVIEWED_INDEX = ROOT / 'data-source/franchise-sync/reviewed-nutrition-index.json'
REVIEWED_PATCH_PATHS = (
    'data-source/franchise-nutrition-batch-20261014/linked-menus.json',
    'data-source/franchise-nutrition-batch-2-20261014/linked-menus.json',
    'data-source/franchise-nutrition-stability-20261014/linked-menus.json',
    'data-source/franchise-nutrition-core-20261016/linked-menus.json',
    'data-source/franchise-nutrition-followup-20261016/linked-menus.json',
    'data-source/franchise-nutrition-remaining-six-20261016/linked-menus.json',
    'data-source/franchise-nutrition-five-20261017/linked-menus.json',
    'data-source/franchise-nutrition-alternatives-20261017/linked-menus.json',
)


def _reviewed_require(condition, message):
    if not condition:
        raise ValueError('Invalid reviewed nutrition: ' + message)


def _reviewed_json(payload):
    # JSON duplicate keys must not silently change the pinned contract.
    def unique(pairs):
        result = {}
        for key, value in pairs:
            _reviewed_require(key not in result, 'duplicate JSON key')
            result[key] = value
        return result
    return json.loads(payload, object_pairs_hook=unique)


def _reviewed_manifest_paths(manifest):
    _reviewed_require(isinstance(manifest, dict) and set(manifest) == {'version', 'patches'}
                      and type(manifest['version']) is int and manifest['version'] == 1,
                      'manifest shape/version')
    entries = manifest['patches']
    _reviewed_require(isinstance(entries, list) and 2 <= len(entries) <= len(REVIEWED_PATCH_PATHS), 'bounded reviewed patches required')
    paths = []
    for entry in entries:
        _reviewed_require(isinstance(entry, dict) and set(entry) == {'path', 'sha256'}, 'patch entry')
        path, sha = entry['path'], entry['sha256']
        _reviewed_require(isinstance(path, str) and path in REVIEWED_PATCH_PATHS and path not in paths,
                          'forbidden or duplicate path')
        _reviewed_require(isinstance(sha, str) and re.fullmatch('[a-f0-9]{64}', sha), 'pinned hash')
        paths.append(path)
    _reviewed_require(set(REVIEWED_PATCH_PATHS[:2]) <= set(paths), 'original patches required')
    return paths


def validate_reviewed_manifest(manifest, payloads):
    """Pure version-1 path/hash contract; payloads maps exact paths to bytes.

    Manifest: {version: 1, patches: [{path: ..., sha256: ...}, ...]}.
    Patch: {baseCatalogSha256: historical SHA, menus: [menu, ...]}.
    Historical SHA is provenance, never compared to the current catalog.
    """
    paths = _reviewed_manifest_paths(manifest)
    _reviewed_require(isinstance(payloads, dict) and set(payloads) == set(paths), 'payload paths')
    batches = []
    for entry in manifest['patches']:
        payload = payloads[entry['path']]
        _reviewed_require(isinstance(payload, bytes) and hashlib.sha256(payload).hexdigest() == entry['sha256'],
                          'payload hash')
        batches.append(_reviewed_json(payload))
    return batches


def _validate_reviewed_renderer(evidence, allowed_hosts, capture_checked_at):
    """Validate renderer provenance without replacing supplied capture metadata."""
    _reviewed_require(isinstance(evidence, dict) and
                      set(evidence) <= {'sourceUrl', 'sourceSha256', 'checkedAt', 'labels'},
                      'renderer evidence shape')
    if 'labels' in evidence:
        _reviewed_require(evidence['labels'] == DUNKIN_LABELS, 'renderer labels')
    # Existing renderer captures omit a timestamp; validate against their
    # enclosing capture only in that case, without adding it to stored evidence.
    validation = dict(evidence)
    validation.setdefault('checkedAt', capture_checked_at)
    validate_nutrition(validation, allowed_hosts)


def apply_reviewed_nutrition(menus, batches, registry):
    """Validate every row first, then fill NULLs on deep copies; no file IO.

    Accept compact and version-1 reviewed envelopes. Resolve brand policy by
    exact registry name (or supplied compact ID with a nameless policy), then
    validate incremental overlaps before applying rows
    in source order. Unknown catalog IDs and changed catalog identities are
    audited skips, never new/renamed menus.
    """
    identity = ('id', 'brandId', 'brand', 'name', 'externalId', 'sourceUrl')
    _reviewed_require(isinstance(batches, list) and 2 <= len(batches) <= len(REVIEWED_PATCH_PATHS), 'bounded reviewed batches required')
    rows, previous = [], {}
    for batch in batches:
        compact = {'baseCatalogSha256', 'menus'}
        reviewed = compact | {'schemaVersion', 'environment'}
        _reviewed_require(isinstance(batch, dict) and set(batch) in (compact, reviewed), 'batch shape')
        if set(batch) == reviewed:
            _reviewed_require(type(batch['schemaVersion']) is int and batch['schemaVersion'] == 1
                              and batch['environment'] == 'DEVELOPMENT_ONLY_NOT_PUBLISHED', 'batch version/environment')
        _reviewed_require(isinstance(batch['baseCatalogSha256'], str) and
                          re.fullmatch('[a-f0-9]{64}', batch['baseCatalogSha256']), 'historical hash')
        _reviewed_require(isinstance(batch['menus'], list), 'menu rows')
        seen = set()
        for raw_row in batch['menus']:
            _reviewed_require(isinstance(raw_row, dict) and
                              set(raw_row) <= set(identity) | {'officialNutrition', 'addedFields'}, 'row shape')
            row = dict(raw_row)
            _reviewed_require(all(not ('brand' in s and 'name' in s) or s['brand'] == s['name']
                                  for s in registry), 'contradictory brand policy')
            bindings = [s for s in registry if s.get('brand', s.get('name')) == row.get('brand')]
            # The original compact contract supplies brandId and permits a
            # nameless registry policy. This never derives an ID: missing IDs
            # and versioned rows still require the exact named binding.
            if set(batch) == compact and 'brandId' in row and not bindings:
                bindings = [s for s in registry if s.get('brandId') == row['brandId']
                            and 'name' not in s and 'brand' not in s]
            _reviewed_require(len(bindings) == 1 and isinstance(bindings[0].get('brandId'), str)
                              and bindings[0]['brandId'], 'unique exact brand policy')
            source = bindings[0]
            _reviewed_require(sum(s.get('brandId') == source['brandId'] for s in registry) == 1,
                              'unique brand ID policy')
            _reviewed_require('brandId' not in row or row['brandId'] == source['brandId'], 'brand ID binding')
            row.setdefault('brandId', source['brandId'])
            _reviewed_require(all(isinstance(row.get(k), str) and (row[k] or k == 'externalId') for k in identity), 'row identity')
            _reviewed_require(row['id'] not in seen, 'duplicate menu row')
            seen.add(row['id'])
            if 'addedFields' in row:
                fields = row['addedFields']
                _reviewed_require(isinstance(fields, list) and all(isinstance(k, str) and k in FIELDS + OPTIONAL_FIELDS + ('servingAmount', 'servingUnit') for k in fields)
                                  and len(fields) == len(set(fields)), 'added fields')
            fact = row.get('officialNutrition')
            _reviewed_require(isinstance(fact, dict) and fact.get('officialProductId') == row['externalId'], 'official product ID')
            fact_keys = set(FIELDS + OPTIONAL_FIELDS + ('servingAmount', 'servingUnit',
                'servingDescription', 'officialProductId', 'sourceUrl', 'checkedAt', 'sourceSha256',
                'sourceType', 'sourcePage', 'matchedBy', 'nutritionProductId', 'nutritionGroup',
                'retainedFieldEvidence', 'optionalFieldEvidence',
                'listSourceURL', 'listSourceSha256', 'officialDetailFullName', 'listingDisplayName', 'groupCrosswalkEvidence'))
            _reviewed_require(set(fact) <= fact_keys, 'unknown fact fields')
            if row['brand'] == '던킨' and not row['externalId'].startswith('products:'):
                _reviewed_require('nutritionProductId' in fact and 'groupCrosswalkEvidence' in fact,
                                  'group nutrition identity and proof required')
            if 'groupCrosswalkEvidence' in fact:
                _reviewed_require(source.get('adapter') == 'dunkin-nutrition-pages', 'Dunkin crosswalk policy')
                try:
                    validate_dunkin_crosswalk(row, fact, source.get('allowedHosts', []))
                except (AssertionError, KeyError, TypeError, AttributeError, ValueError) as error:
                    raise ValueError('Invalid reviewed Dunkin crosswalk') from error
            oozy_image_name = (row['brand']=='우지커피' and source.get('adapter')=='oozy-reviewed-image-nutrition'
                and fact.get('matchedBy')=='EXACT_REVIEWED_IMAGE_NAME_SINGLE_SERVING' and not row['externalId'])
            if oozy_image_name:
                from franchise_nutrition_oozy import validate_replay
                try:validate_replay(row, fact)
                except (AssertionError, KeyError, TypeError, ValueError) as error:
                    raise ValueError('Invalid reviewed Oozy image-name/serving proof') from error
            _reviewed_require(bool(row['externalId']) or 'groupCrosswalkEvidence' in fact or oozy_image_name, 'empty product ID')
            crosswalk_keys = TLJ_CROSSWALK_KEYS - {'matchedBy'}
            if crosswalk_keys & set(fact) or fact.get('matchedBy') == 'tlj-listing-display-id-alt':
                _reviewed_require(TLJ_CROSSWALK_KEYS <= set(fact) and
                    fact['matchedBy'] == 'tlj-listing-display-id-alt' and row['brand'] == '뚜레쥬르'
                    and source.get('adapter') == 'tlj-official-nutrition', 'crosswalk shape and source')
                _reviewed_require(all(isinstance(fact[k], str) and fact[k].strip() for k in TLJ_CROSSWALK_KEYS), 'crosswalk types')
                _reviewed_require(fact['listSourceURL'] == TLJ_LIST_URL and 'www.tlj.co.kr' in source.get('allowedHosts', [])
                    and re.fullmatch('[a-f0-9]{64}', fact['listSourceSha256']), 'crosswalk listing provenance')
                _reviewed_require(norm(fact['listingDisplayName']) == norm(row['name'])
                    and norm(fact['officialDetailFullName']) != norm(row['name']), 'crosswalk names')
                detail = urlparse(fact.get('sourceUrl', ''))
                _reviewed_require(detail.scheme == 'https' and detail.netloc == 'www.tlj.co.kr'
                    and detail.path == '/product/detail.asp' and not detail.fragment
                    and parse_qs(detail.query, keep_blank_values=True).get('prod_num') == [row['externalId']], 'crosswalk detail identity')
            _reviewed_require('servingDescription' not in fact or isinstance(fact['servingDescription'], str),
                              'serving description')
            _reviewed_require('sourceType' not in fact or fact['sourceType'] in ('HTML', 'JSON', 'PDF'), 'source type')
            _reviewed_require('sourcePage' not in fact or type(fact['sourcePage']) is int and fact['sourcePage'] > 0,
                              'source page')
            for key in ('retainedFieldEvidence', 'optionalFieldEvidence'):
                _reviewed_require(key not in fact or isinstance(fact[key], dict), 'evidence mapping')
            _reviewed_require(source is not None and isinstance(source.get('allowedHosts'), list)
                              and source['allowedHosts'], 'official host policy')
            try:
                validate_nutrition(fact, source['allowedHosts'])
                uri = urlparse(row['sourceUrl'])
                _reviewed_require(uri.scheme in ('http', 'https') and not uri.username and not uri.password
                                  and uri.hostname in source['allowedHosts'], 'menu official URL')
                _reviewed_require(any(fact.get(k) is not None for k in FIELDS + OPTIONAL_FIELDS), 'empty facts')
                # Evidence attached to a retained field is subject to the same
                # provenance contract as the main capture.
                for key, evidence in fact.get('retainedFieldEvidence', {}).items():
                    _reviewed_require(key in FIELDS + OPTIONAL_FIELDS + ('servingAmount', 'servingUnit')
                                      and fact.get(key) is not None and isinstance(evidence, dict), 'retained evidence')
                    _reviewed_require(set(evidence) <= {'sourceUrl', 'checkedAt', 'sourceSha256', 'renderer'},
                                      'retained evidence shape')
                    validate_nutrition(evidence, source['allowedHosts'])
                    if 'renderer' in evidence:
                        _reviewed_require(key in OPTIONAL_FIELDS and isinstance(evidence['renderer'], dict)
                                          and 'labels' in evidence['renderer'], 'retained renderer structure')
                        _validate_reviewed_renderer(evidence['renderer'], source['allowedHosts'], evidence['checkedAt'])
                for key, evidence in fact.get('optionalFieldEvidence', {}).items():
                    _reviewed_require(key in OPTIONAL_FIELDS and fact.get(key) is not None
                                      and isinstance(evidence, dict), 'optional evidence')
                    _validate_reviewed_renderer(evidence, source['allowedHosts'], fact['checkedAt'])
            except (AssertionError, KeyError, TypeError, AttributeError, ValueError) as error:
                raise ValueError('Invalid reviewed nutrition row: ' + row['id']) from error
            if row['id'] in previous:
                # Accumulate known values across all batches so a later patch
                # cannot conflict with enrichment absent from the first patch.
                prior_identity, known = previous[row['id']]
                _reviewed_require(all(row[k] == prior_identity[k] for k in identity), 'cross-batch identity conflict')
                _reviewed_require(all(known.get(k) is None or fact.get(k) is None or known[k] == fact[k]
                                      for k in FIELDS + OPTIONAL_FIELDS + ('servingAmount', 'servingUnit')),
                                  'cross-batch fact conflict')
                for key in FIELDS + OPTIONAL_FIELDS + ('servingAmount', 'servingUnit'):
                    if known.get(key) is None and fact.get(key) is not None:
                        known[key] = fact[key]
            else:
                previous[row['id']] = (row, copy.deepcopy(fact))
            rows.append(row)
    _reviewed_require(len({m['id'] for m in menus}) == len(menus), 'duplicate catalog ID')
    result = copy.deepcopy(menus)
    by_id = {m['id']: m for m in result}
    audit = []
    for row in rows:
        menu = by_id.get(row['id'])
        if (menu is None or any(menu.get(k) != row[k] for k in identity)
                or (menu.get('officialNutrition') or {}).get('officialProductId', row['externalId']) != row['externalId']):
            audit.append(dict(menuId=row['id'], decision='REVIEWED_IDENTITY_SKIP'))
            continue
        old = menu.get('officialNutrition') or {}
        candidate = copy.deepcopy(row['officialNutrition'])
        _, conflicts = retain_verified_on_conflict(menu, candidate)
        if conflicts:
            audit.extend(conflicts)
            continue
        if not old:
            menu['officialNutrition'] = candidate
        else:
            # Keep old capture metadata and known values even when equal. Each
            # newly acquired field carries the actual reviewed capture evidence.
            merged = copy.deepcopy(old)
            evidence = copy.deepcopy(old.get('retainedFieldEvidence', {}))
            for key in FIELDS + OPTIONAL_FIELDS + ('servingAmount', 'servingUnit'):
                if old.get(key) is None and candidate.get(key) is not None:
                    merged[key] = candidate[key]
                    evidence[key] = copy.deepcopy(candidate.get('retainedFieldEvidence', {}).get(key,
                        {p: candidate[p] for p in ('sourceUrl', 'checkedAt', 'sourceSha256')}))
                    if key in candidate.get('optionalFieldEvidence', {}):
                        merged.setdefault('optionalFieldEvidence', {})[key] = copy.deepcopy(candidate['optionalFieldEvidence'][key])
            if evidence:
                merged['retainedFieldEvidence'] = evidence
            menu['officialNutrition'] = merged
        if candidate.get('groupCrosswalkEvidence'):
            menu['officialNutrition']['groupCrosswalkEvidence'] = copy.deepcopy(candidate['groupCrosswalkEvidence'])
            menu['officialNutrition']['nutritionProductId'] = candidate['nutritionProductId']
        menu['nutritionStatus'] = status(menu['officialNutrition'])
        replay_evidence = dict(menuId=row['id'], decision='REVIEWED_REPLAY')
        if candidate.get('groupCrosswalkEvidence'):
            replay_evidence['groupCrosswalkEvidence'] = copy.deepcopy(candidate['groupCrosswalkEvidence'])
            replay_evidence['nutritionProductId'] = candidate['nutritionProductId']
        audit.append(replay_evidence)
    return result, audit


def load_reviewed_nutrition(menus, registry, index_path=REVIEWED_INDEX):
    """Missing index is compatible; every other loader error aborts refresh."""
    try:
        payload = index_path.read_bytes()
    except FileNotFoundError:
        return copy.deepcopy(menus), []
    manifest = _reviewed_json(payload)
    # Validate paths before reading any referenced file.
    for path in _reviewed_manifest_paths(manifest):
        _reviewed_require((ROOT / path).resolve() == ROOT.joinpath(*path.split('/')),
                          'redirected patch path')
    payloads = {entry['path']: (ROOT / entry['path']).read_bytes() for entry in manifest['patches']}
    return apply_reviewed_nutrition(menus, validate_reviewed_manifest(manifest, payloads), registry)


def normalized_nutrition_name(value):
    """Typography only: retain size, temperature, flavor and composition tokens."""
    return re.sub(r'[\s®™]', '', unicodedata.normalize('NFKC', value).casefold())


def retain_verified_on_conflict(menu, candidate):
    """Never silently replace a verified value or combine distinct serving bases."""
    old = menu.get('officialNutrition')
    if not old:
        return candidate, []
    # A missing basis can only be completed by independent, same-source
    # corroboration of every known numeric fact. Preserve the old capture.
    missing_basis = old.get('servingAmount') is None and old.get('servingUnit') is None
    valid_basis = (type(candidate.get('servingAmount')) in (int, float)
                   and math.isfinite(candidate['servingAmount'])
                   and 0 < candidate['servingAmount'] <= 10000
                   and candidate.get('servingUnit') in ('g', 'ml', '개', '인분', '잔', 'oz'))
    corroborated = (missing_basis and valid_basis
        and isinstance(old.get('officialProductId'), str)
        and old['officialProductId'] == candidate.get('officialProductId')
        and isinstance(old.get('sourceUrl'), str) and bool(old['sourceUrl'])
        and old['sourceUrl'] == candidate.get('sourceUrl')
        and any(old.get(k) is not None for k in FIELDS + OPTIONAL_FIELDS)
        and all(old.get(k) is None or (type(candidate.get(k)) in (int, float)
                and math.isfinite(candidate[k]) and candidate[k] == old[k]) for k in FIELDS + OPTIONAL_FIELDS))
    if corroborated:
        merged = copy.deepcopy(old)
        for k in ('servingAmount', 'servingUnit'):
            merged[k] = candidate[k]
            merged.setdefault('retainedFieldEvidence', {})[k] = {
                p: candidate.get(p) for p in ('sourceUrl', 'checkedAt', 'sourceSha256')}
        return merged, []
    keys = FIELDS + OPTIONAL_FIELDS + ('servingAmount', 'servingUnit')
    differences = [dict(field=k, oldValue=old[k], newValue=candidate[k])
                   for k in keys if old.get(k) is not None and candidate.get(k) is not None
                   and old[k] != candidate[k]]
    if (any(old.get(k) is not None for k in FIELDS + OPTIONAL_FIELDS)
            and valid_basis and (old.get('servingAmount') is None or old.get('servingUnit') is None)):
        differences.append(dict(field='servingAmount', oldValue=old.get('servingAmount'),
                                newValue=candidate.get('servingAmount')))
    # Optional nutrients may only be combined with existing facts on the same
    # explicit serving basis. Missing basis is not evidence of equivalence.
    if (any(old.get(k) is not None for k in FIELDS + OPTIONAL_FIELDS)
            and any(candidate.get(k) is not None for k in FIELDS + OPTIONAL_FIELDS)
            and any(old.get(k) is not None or candidate.get(k) is not None for k in OPTIONAL_FIELDS)):
        for k in ('servingAmount', 'servingUnit'):
            if (old.get(k) is None or candidate.get(k) is None) and not any(d['field'] == k for d in differences):
                differences.append(dict(field=k, oldValue=old.get(k), newValue=candidate.get(k)))
    if differences:
        return dict(old), [dict(menuId=menu['id'], differences=differences,
            sourceA=old.get('sourceUrl'), sourceB=candidate.get('sourceUrl'),
            basisA=[old.get('servingAmount'), old.get('servingUnit')],
            basisB=[candidate.get('servingAmount'), candidate.get('servingUnit')],
            decision='RETAIN_VERIFIED_PENDING_REVIEW')]
    # Retain known fields if a current page omits them; every retained field
    # carries its original capture provenance instead of a fake fresh timestamp.
    merged = dict(candidate)
    retained = [k for k in keys if old.get(k) is not None and candidate.get(k) is None]
    for k in retained:
        merged[k] = old[k]
    if retained:
        merged['retainedFieldEvidence'] = {k: old.get('retainedFieldEvidence', {}).get(k,
            dict({p: old.get(p) for p in ('sourceUrl', 'checkedAt', 'sourceSha256')},
                 **({'renderer': old['optionalFieldEvidence'][k]}
                    if k in old.get('optionalFieldEvidence', {}) else {}))) for k in retained}
    return merged, []


def number(value, suffix):
    match = re.fullmatch(r'\s*(\d+(?:\.\d+)?|\.\d+)\s*' + re.escape(suffix) + r'\s*', str(value))
    return float(match[1]) if match else None


def parse_starbucks(menu, markup):
    assert menu['brand'] == '스타벅스'
    code = parse_qs(urlparse(menu['sourceUrl']).query)['product_cd'][0]
    assert menu['externalId'] in ('drink:' + code, 'food:' + code)
    products = []
    text = markup.decode('utf-8') if isinstance(markup, bytes) else markup
    for raw in html.fromstring(text).xpath('//script[@type="application/ld+json"]/text()'):
        document = json.loads(raw)
        products.extend(p for p in document.get('@graph', []) if p.get('@type') == 'Product')
    matches = [p for p in products if str(p.get('sku')) == code and p.get('name', '').strip() == menu['name'].strip()]
    assert len(matches) == 1, 'Official product ID/name did not match'
    product = matches[0]
    props = {}
    for prop in product.get('additionalProperty', []):
        key = prop['name']
        assert key not in props, 'Conflicting official nutrition properties'
        props[key] = prop['value']
    result = {field: number(props[label], unit) if label in props else None for field, label, unit in (
        ('energyKcal', '칼로리', 'kcal'), ('carbohydrateGrams', '탄수화물', 'g'),
        ('proteinGrams', '단백질', 'g'), ('fatGrams', '지방', 'g'), ('sodiumMilligrams', '나트륨', 'mg'))}
    # Saturated fat is not total fat; sugar is not carbohydrate.
    amount = re.fullmatch(r'\s*(\d+(?:\.\d+)?)\s*(g|ml)\s*', str(props.get('용량', '')))
    result.update(servingAmount=float(amount[1]) if amount else None,
                  servingUnit=amount[2] if amount else None, servingDescription=props.get('사이즈', ''))
    # The same official page embeds the literal used by its nutrition renderer.
    # Decode JSON only; never execute a script or use the alternate _L basis.
    text = markup.decode('utf-8') if isinstance(markup, bytes) else markup
    match = re.search(r'view:\s*remapView\(', text)
    if match:
        raw, _ = json.JSONDecoder().raw_decode(text[match.end():])
        assert str(raw['PRODUCT_CD']) == code and raw['PRODUCT_NM'].strip() == menu['name'].strip()
        for field, key, unit in (('energyKcal','KCAL','kcal'), ('carbohydrateGrams','CHABO','g'),
                                  ('proteinGrams','PROTEIN','g'), ('fatGrams','FAT','g'), ('sodiumMilligrams','SODIUM','mg')):
            value = number(str(raw.get(key, '')) + unit, unit)
            if result[field] is not None and value is not None:
                assert result[field] == value, 'Conflicting official nutrition basis'
            if result[field] is None:
                result[field] = value
        raw_amount = number(str(raw.get('STANDARD', '')) + str(raw.get('UNIT', '')), str(raw.get('UNIT', ''))) if raw.get('UNIT') in ('g','ml') else None
        if raw_amount is not None and raw_amount > 0:
            assert result['servingAmount'] is None or result['servingAmount'] == raw_amount
            result.update(servingAmount=raw_amount, servingUnit=raw['UNIT'])
    return result


def parse_hansot(menu, markup):
    assert menu['brand'] == '한솥'
    assert urlparse(menu['sourceUrl']).path == '/menu/menu_view/' + menu['externalId']
    root = html.fromstring(markup.decode('utf-8') if isinstance(markup, bytes) else markup)
    # Only this product's menu title and calorie block; no unrelated promotion.
    titles = root.xpath('//section[contains(@class,"menu_view")]//h3[@class="he_tit"]')
    all_titles = [' '.join(t.xpath('.//span[@class="dp2"]//text()')).strip() for t in titles]
    assert menu['name'] in all_titles, 'Official Hansot detail title did not match'
    values = root.xpath('//div[contains(@class,"menu_info") and contains(@class,"quantity")]/p/span/text()')
    assert len(values) == 1
    kcal = number(values[0] + 'kcal', 'kcal')
    assert kcal is not None
    # The published detail has kcal but no explicit amount: never invent 1 serving.
    return dict(energyKcal=kcal, carbohydrateGrams=None, proteinGrams=None, fatGrams=None,
                sodiumMilligrams=None, servingAmount=None, servingUnit=None, servingDescription='')


def parse_mcdonalds(menu, payload):
    assert menu['brand'] == '맥도날드'
    data = json.loads(payload)
    assert data['resultCode'] == 100
    rows = data['resultObject']['list']
    assert len(rows) == data['resultObject']['totalCount']
    normalize = normalized_nutrition_name
    matches = [r for r in rows if normalize(r['menuName']) == normalize(menu['name'])]
    assert len(matches) == 1, 'Nutrition name/variant not uniquely matched'
    row = matches[0]
    values = dict(part.split(';', 1) for part in row['nutritionFacts'].split(',') if ';' in part)
    def value(label, suffix):
        # Percent daily values follow the actual amount. Less-than/ranges remain unknown.
        return number(values.get(label, '').split('(')[0], suffix)
    weight = re.fullmatch(r'(\d+(?:\.\d+)?)(g|ml)', values.get('중량', ''))
    return dict(energyKcal=value('열량', 'Kcal') if '열량' in values else number(str(row['calorie'])+'kcal','kcal'),
                carbohydrateGrams=value('탄수화물','g'), proteinGrams=value('단백질','g'), fatGrams=value('지방','g'),
                sodiumMilligrams=value('나트륨','mg'), servingAmount=float(weight[1]) if weight else None,
                servingUnit=weight[2] if weight else None, servingDescription='',
                nutritionProductId=row['plu'], matchedBy='EXACT_NAME_AND_VARIANT')


def parse_mcdonalds_product(menu,payload):
    """Use the official product ID when the separate nutrition name differs."""
    assert menu['brand']=='맥도날드' and menu['externalId'].isdigit()
    data=json.loads(payload);assert data['resultCode']==100
    rows=data['resultObject']['list']
    matches=[r for r in rows if str(r['seq'])==menu['externalId'] and
             normalized_nutrition_name(r['korName'])==normalized_nutrition_name(menu['name'])]
    assert len(matches)==1,'Official product ID and full size/variant name changed'
    row=matches[0]
    # Only actual numeric fields, never ranges, percentages, sugar or saturated fat.
    result={field:number(str(row.get(key,''))+unit,unit) for field,key,unit in (
        ('energyKcal','calorie','kcal'),('carbohydrateGrams','carbohydrate','g'),
        ('proteinGrams','protein','g'),('fatGrams','fat','g'),('sodiumMilligrams','sodium','mg'))}
    # A bare weight/volume field without a published unit is insufficient basis.
    result.update(servingAmount=None,servingUnit=None,servingDescription='',matchedBy='EXACT_OFFICIAL_PRODUCT_ID_AND_NAME')
    assert any(result[k] is not None for k in FIELDS),'Product list publishes no exact numeric nutrition'
    return result


PARSERS = {'starbucks-product-jsonld': parse_starbucks, 'hansot-detail-kcal': parse_hansot,
           'mc-nutrition-json': parse_mcdonalds}


DUNKIN_LABELS = {'NUTRITION_SERVING': '1회 제공량(g)',
                 'NUTRITION_SUGARS': '당류(g)', 'NUTRITION_FAT': '포화지방(g)'}


def dunkin_renderer_url(root):
    # Follow only one explicitly linked same-host View module, never imports.
    links = root.xpath('//link[@rel="modulepreload"]/@href')
    urls = set('https://www.dunkindonuts.co.kr' + link if link.startswith('/') else link
               for link in links)
    urls = [u for u in urls if re.fullmatch(
        r'https://www\.dunkindonuts\.co\.kr/build/assets/View-[a-f0-9]+\.js', u)]
    return urls[0] if len(urls) == 1 else None


def dunkin_renderer_evidence(url, payload):
    if not isinstance(payload, bytes) or not 0 < len(payload) <= 262144:
        return None
    text = payload.decode('utf-8')
    # Literal adjacent column bindings only; do not execute published JavaScript.
    pairs = re.findall(r'\{accessorKey:"([A-Z_]+)",header:"([^"\\]*)",cell:', text)
    labels = {key: label for key, label in pairs if key in DUNKIN_LABELS}
    if labels != DUNKIN_LABELS or any(sum(k == key for k, _ in pairs) != 1 for key in DUNKIN_LABELS):
        return None
    return dict(labels=labels, sourceUrl=url, sourceSha256=hashlib.sha256(payload).hexdigest())


def dunkin_pages(url, get):
    records = []
    expected_total = None
    last_page = 1
    page = 1
    evidence = None
    renderer_url = None
    while page <= last_page:
        page_url = url if page == 1 else url + '?page=' + str(page)
        payload = get(page_url)
        root = html.fromstring(payload.decode('utf-8'))
        linked_renderer = dunkin_renderer_url(root)
        if page == 1 and linked_renderer:
            renderer_url = linked_renderer
            try:
                evidence = dunkin_renderer_evidence(renderer_url, get(renderer_url))
            except Exception:
                # Optional evidence failure must not discard core published facts.
                evidence = None
        data = json.loads(root.xpath('//*[@id="app"]/@data-page')[0])['props']['productsNutrition']
        meta = data['meta']
        assert meta['current_page'] == page and meta['path'] == url and 1 <= meta['last_page'] <= 64
        if expected_total is None:expected_total = meta['total'];last_page = meta['last_page']
        assert expected_total == meta['total'] and last_page == meta['last_page']
        for row in data['data']:
            records.append(dict(row, _sourceUrl=page_url, _sourceSha256=hashlib.sha256(payload).hexdigest(),
                                _optionalEvidence=evidence if linked_renderer == renderer_url else None))
        page += 1
    assert len(records) == expected_total and len({r['id'] for r in records}) == expected_total
    return json.dumps(records, ensure_ascii=False).encode()


DUNKIN_HOST = 'www.dunkindonuts.co.kr'


def dunkin_group_query(url):
    uri = urlparse(url)
    assert uri.scheme == 'https' and uri.netloc == DUNKIN_HOST
    assert uri.path == '/menu/viewProductCat' and not uri.fragment
    assert re.fullmatch(r'(?:cat|sub|id)=[1-9][0-9]*(?:&(?:cat|sub|id)=[1-9][0-9]*){2}', uri.query)
    query = parse_qs(uri.query, keep_blank_values=True)
    assert set(query) == {'cat', 'sub', 'id'}
    assert all(len(v) == 1 and re.fullmatch(r'[1-9][0-9]*', v[0]) for v in query.values())
    return {k: int(v[0]) for k, v in query.items()}


def dunkin_page_data(payload, component):
    assert isinstance(payload, bytes) and 0 < len(payload) <= 2000000
    root = html.fromstring(payload.decode('utf-8'))
    pages = root.xpath('//*[@id="app"]/@data-page')
    assert len(pages) == 1
    data = json.loads(pages[0])
    assert data['component'] == component
    return data['props']


def validate_dunkin_crosswalk(menu, fact, allowed_hosts):
    proof = fact.get('groupCrosswalkEvidence')
    assert menu['brand'] == '던킨' and DUNKIN_HOST in allowed_hosts
    assert isinstance(proof, dict) and set(proof) == {
        'sourceUrl', 'sourceSha256', 'checkedAt', 'groupId', 'productId', 'fullTitle', 'basis'}
    assert all(isinstance(proof[k], str) for k in ('sourceUrl', 'sourceSha256', 'checkedAt', 'fullTitle'))
    query = dunkin_group_query(proof['sourceUrl'])
    assert type(proof['groupId']) is int and proof['groupId'] > 0 and query['id'] == proof['groupId']
    assert type(proof['productId']) is int and proof['productId'] > 0
    assert proof['fullTitle'] == menu['name']
    assert fact.get('nutritionProductId') == 'products:' + str(proof['productId'])
    assert fact.get('officialProductId') == menu['externalId']
    if menu['externalId']:
        assert menu['externalId'] == 'productCats:' + str(proof['groupId'])
    else:
        assert menu['sourceUrl'] == proof['sourceUrl']
    basis = proof['basis']
    assert isinstance(basis, dict) and set(basis) == {'servingAmount', 'servingUnit'}
    assert type(basis['servingAmount']) in (int, float) and basis['servingUnit'] == 'g'
    assert math.isfinite(basis['servingAmount']) and 0 < basis['servingAmount'] <= 10000
    assert all(basis[k] == fact.get(k) for k in basis)
    validate_nutrition(dict(proof, **basis), allowed_hosts)


def parse_dunkin(menu, payload, crosswalk=None):
    assert menu['brand'] == '던킨'
    rows = json.loads(payload)
    product_id = menu['externalId']
    proof = None
    if not product_id.startswith('products:'):
        assert isinstance(crosswalk, dict) and set(crosswalk) == {'sourceUrl', 'payload', 'checkedAt'}, 'Dunkin group crosswalk required'
        url = crosswalk['sourceUrl']
        query = dunkin_group_query(url)
        assert (product_id == 'productCats:' + str(query['id'])
                or product_id == '' and menu['sourceUrl'] == url), 'Group namespace mismatch'
        group = dunkin_page_data(crosswalk['payload'], 'Client/Menu/ViewGroup')['productCategory']['data']
        assert type(group['id']) is int and group['id'] == query['id']
        assert group['TITLE'] == menu['name']
        for field, key in (('dd_product_cat1_id', 'cat'), ('dd_product_cat2_id', 'sub')):
            if field in group:
                assert type(group[field]) is int and group[field] == query[key]
        children = group['PRODUCTS']
        assert isinstance(children, list)
        children = [c for c in children if c.get('TITLE') == menu['name']]
        assert len(children) == 1, 'No unique exact PRODUCTS child'
        child = children[0]
        assert type(child['id']) is int and child['id'] > 0
        match = [r for r in rows if type(r['id']) is int and r['id'] == child['id'] and r['TITLE'] == menu['name']]
        assert len(match) == 1, 'Exact child nutrition ID/title mismatch'
        for key in ('NUTRITION_SERVING', 'NUTRITION_KCAL', 'NUTRITION_PROTEIN', 'NUTRITION_NATRIUM'):
            values = [number(str(record.get(key, '')) + 'g', 'g') for record in (child, match[0])]
            assert all(v is not None and math.isfinite(v) and 0 <= v <= 100000 for v in values)
            assert values[0] == values[1], 'Group child nutrition/basis mismatch'
        proof = dict(sourceUrl=url, sourceSha256=hashlib.sha256(crosswalk['payload']).hexdigest(),
                     checkedAt=crosswalk['checkedAt'], groupId=group['id'], productId=child['id'],
                     fullTitle=group['TITLE'], basis=dict(servingAmount=None, servingUnit='g'))
        product_id = 'products:' + str(child['id'])
    else:
        match = [r for r in rows if 'products:'+str(r['id']) == product_id and r['TITLE'].strip() == menu['name'].strip()]
    assert len(match) == 1, 'Dunkin official product ID/name not matched'
    row = match[0]
    amount = number(str(row.get('NUTRITION_SERVING',''))+'g','g')
    evidence = row.get('_optionalEvidence')
    mapped = (isinstance(evidence, dict) and evidence.get('labels') == DUNKIN_LABELS
              and re.fullmatch(r'https://www\.dunkindonuts\.co\.kr/build/assets/View-[a-f0-9]+\.js', str(evidence.get('sourceUrl')))
              and re.fullmatch('[a-f0-9]{64}', str(evidence.get('sourceSha256'))))
    basis = amount is not None and math.isfinite(amount) and 0 < amount <= 10000
    def optional(key):
        value = number(str(row.get(key, '')) + 'g', 'g') if mapped and basis else None
        return value if value is not None and math.isfinite(value) and value <= 100000 else None
    result = dict(energyKcal=number(str(row.get('NUTRITION_KCAL',''))+'kcal','kcal'),
                # Published sugar/saturated columns are never carbohydrate/total fat.
                carbohydrateGrams=None, fatGrams=None,
                sugarsGrams=optional('NUTRITION_SUGARS'), saturatedFatGrams=optional('NUTRITION_FAT'),
                proteinGrams=number(str(row.get('NUTRITION_PROTEIN',''))+'g','g'),
                sodiumMilligrams=number(str(row.get('NUTRITION_NATRIUM',''))+'mg','mg'),
                servingAmount=amount if amount and amount > 0 else None, servingUnit='g' if amount and amount > 0 else None,
                servingDescription='공식 1회 제공량', sourceUrl=row['_sourceUrl'], sourceSha256=row['_sourceSha256'])
    if any(result[k] is not None for k in OPTIONAL_FIELDS):
        result['optionalFieldEvidence'] = {k: dict(evidence) for k in OPTIONAL_FIELDS if result[k] is not None}
    if proof:
        proof['basis']['servingAmount'] = result['servingAmount']
        result.update(nutritionProductId=product_id, groupCrosswalkEvidence=proof)
        validate_dunkin_crosswalk(menu, dict(result, officialProductId=menu['externalId']), [DUNKIN_HOST])
    return result


PARSERS['dunkin-nutrition-pages'] = parse_dunkin


@functools.lru_cache(maxsize=2)
def salady_pdf_records(payload):
    from pypdf import PdfReader
    reader=PdfReader(io.BytesIO(payload))
    assert 1 <= len(reader.pages) <= 64
    records=[]
    for index,page in enumerate(reader.pages):
        text=page.extract_text()
        assert all(label in text for label in ('제공량(g)','열량(Kcal)','탄수화물(g)','단백질(g)','지방(g)','나트륨(mg)'))
        brand='샐러디&샌드위치' if 'SALADY&SANDWICH' in text else '샐러디'
        page_rows=[]
        for line in text.splitlines():
            match=re.fullmatch(r'(.+?)\s+((?:\d+(?:\.\d+)?\s+){7}\d+(?:\.\d+)?)',line.strip())
            if match:
                values=[float(x) for x in match[2].split()]
                assert len(values)==8
                page_rows.append(dict(brand=brand,name=match[1],values=values,page=index+1))
        assert page_rows,'Nutrition PDF table structure changed'
        records.extend(page_rows)
    return records


def parse_salady(menu,payload):
    assert menu['brand'] in ('샐러디','샐러디&샌드위치')
    normalize=lambda name:re.sub(r'\s','',name)
    assert normalize(menu['name'])!='아메리카노','Official PDF explicitly specifies HOT; menu variant unspecified'
    matches=[r for r in salady_pdf_records(payload) if r['brand']==menu['brand'] and normalize(r['name'])==normalize(menu['name'])]
    assert len(matches)==1,'PDF brand/name/variant did not uniquely match'
    row=matches[0];amount,kcal,carbs,sugar,protein,fat,saturated,sodium=row['values']
    return dict(energyKcal=kcal,carbohydrateGrams=carbs,proteinGrams=protein,fatGrams=fat,sodiumMilligrams=sodium,
                servingAmount=amount,servingUnit='g',servingDescription='공식 기본 구성 · 추가 토핑·음료는 별도 · 베이스 변경 시 영양값이 달라집니다.',
                sourcePage=row['page'],matchedBy='EXACT_BRAND_NAME_AND_VARIANT')


PARSERS['salady-nutrition-pdf']=parse_salady


def parse_ediya(menu, payload):
    assert menu['brand']=='이디야'
    assert re.fullmatch(r'[0-9]+', menu['externalId']), 'Official Ediya product ID must be numeric'
    root=html.fragment_fromstring(payload.decode('utf-8'),create_parent='div')
    matches=root.xpath('//div[@class="pro_detail"][@id="nutri_'+menu['externalId']+'"]')
    assert len(matches)==1,'Ediya product ID not uniquely matched'
    product=matches[0]
    names=product.xpath('./div[@class="detail_con"]/h2')
    assert len(names)==1 and ' '.join(' '.join(names[0].xpath('./text()')).split())==' '.join(menu['name'].split()),'Ediya product name/variant changed'
    values={}
    for block in product.xpath('.//div[@class="pro_nutri"]/dl'):
        label=''.join(block.xpath('./dt//text()')).strip()
        assert label not in values
        values[label]=''.join(block.xpath('./dd//text()')).strip().strip('()')
    # Cup capacity is not the published amount of liquid. Only explicit weight
    # qualifies as a serving basis; sugars/saturated fat are separate nutrients.
    sizes=product.xpath('.//div[@class="pro_size"]//text()')
    weight=re.fullmatch(r'\s*중량\s*:\s*(\d+(?:\.\d+)?)\s*g\s*',' '.join(sizes))
    return dict(energyKcal=number(values.get('칼로리',''),'kcal'),
                carbohydrateGrams=None,fatGrams=None,
                proteinGrams=number(values.get('단백질',''),'g'),
                sodiumMilligrams=number(values.get('나트륨',''),'mg'),
                servingAmount=float(weight[1]) if weight else None,
                servingUnit='g' if weight else None,servingDescription='공식 제품 중량' if weight else '')


PARSERS['ediya-product-nutrition']=parse_ediya
from franchise_nutrition_oozy import parse as parse_oozy_image, IMAGE_URL as OOZY_IMAGE_URL
PARSERS['oozy-reviewed-image-nutrition']=parse_oozy_image
from franchise_nutrition_youngman import parse as parse_youngman_detail
PARSERS['youngman-detail-nutrition']=parse_youngman_detail


def resolve_ediya_page(menu, pages, allowed_hosts):
    """Pagination is a location, not product identity. Never match by name alone.

    Search only already fetched official menu pages, retaining the immutable
    menu ID/source while recording the actual current nutrition page as proof.
    Multiple copies are acceptable only when all parsed facts agree exactly.
    """
    matches = []
    for url, payload in sorted(pages.items()):
        uri = urlparse(url)
        if (uri.scheme != 'https' or uri.hostname not in allowed_hosts or uri.port not in (None, 443)
                or uri.username or uri.password or uri.fragment or not isinstance(payload, bytes)):
            continue
        if uri.path not in ('/contents/drink.html', '/contents/bakery.html', '/inc/ajax_brand.php'):
            continue
        if uri.path == '/inc/ajax_brand.php' and parse_qs(uri.query).get('gubun') != ['menu_more']:
            continue
        try:
            fact = parse_ediya(menu, payload)
        except AssertionError:
            continue
        matches.append((url, payload, fact))
    assert matches, 'Exact Ediya product ID/full variant absent from fetched official pages'
    assert all(fact == matches[0][2] for _, _, fact in matches), 'Conflicting Ediya product facts across official pages'
    return matches[0]


def parse_pokeallday(menu,payload):
    assert menu['brand']=='포케올데이'
    text=payload.decode('utf-8')
    assert all(header in text for header in ('원재료 용량(g)','열량(kcal)','나트륨(mg)','탄수화물(g)','단백질(g)','지방(g)'))
    root=html.fromstring(text)
    normalize=lambda value:normalized_nutrition_name(html.fragment_fromstring(value,create_parent='div').text_content())
    matches=[]
    # Only complete sold-menu groups. Topping/protein/ingredient rows (1–5)
    # must never be assigned to a finished menu of the same name.
    for group in (6,7,8,9,10,11):
        scripts=root.xpath('//div[contains(concat(" ",@class," ")," wrap'+str(group)+' ")]/script/text()')
        assert len(scripts)==1
        raw=re.search(r'let itemInfoArr = \[(.*?)\]\s*let itemTable',scripts[0],re.S)
        assert raw
        from franchise_literal_data import parse_object_literal
        body=re.sub(r'(?m)^\s*//[^\n]*','',raw[1])
        records=parse_object_literal('{rows:['+body+']}')['rows']
        assert records and all(set(r)=={'name','value'} for r in records)
        pairs=[(r['name'],r['value']) for r in records]
        matches.extend((group,name,value) for name,value in pairs if normalize(name)==normalize(menu['name']))
    assert len(matches)==1,'Poke finished menu name/variant not uniquely matched'
    group,name,raw=matches[0];values=raw.split('|');assert len(values)==10
    amount=re.fullmatch(r'(\d+(?:\.\d+)?)(ml|ea|oz)?',values[0])
    assert amount,'Unexpected official basis'
    numbers=[number(v+'g','g') for v in values[1:]];assert all(v is not None for v in numbers)
    kcal,sodium,carbs,sugar,protein,fat,cholesterol,saturated,trans=numbers
    return dict(energyKcal=kcal,carbohydrateGrams=carbs,proteinGrams=protein,fatGrams=fat,sodiumMilligrams=sodium,
                servingAmount=float(amount[1]),servingUnit={'ml':'ml','ea':'개','oz':'oz',None:'g'}[amount[2]],
                servingDescription='공식 완성 메뉴 기준 · 추가 재료는 별도',matchedBy='EXACT_FINISHED_MENU_NAME_AND_VARIANT',nutritionGroup=group)


PARSERS['poke-finished-menu-nutrition']=parse_pokeallday

from franchise_nutrition_cafes import PARSERS as CAFE_PARSERS
from franchise_nutrition_bakery import parse_paris, parse_tlj, norm, TLJ_LIST_URL, TLJ_CROSSWALK_KEYS, TljListingRequired
from franchise_nutrition_meals import parse_burgerking, parse_isaac_not_published, parse_slowcali, parse_kyochon, parse_mammoth_food, mammoth_food_detail_url
PARSERS.update(CAFE_PARSERS)
PARSERS.update({'paris-official-nutrition':parse_paris, 'tlj-official-nutrition':parse_tlj})
PARSERS['burgerking-official-nutrition']=parse_burgerking
PARSERS['kyochon-official-nutrition']=parse_kyochon
PARSERS['mammoth-food-official-nutrition']=parse_mammoth_food
PARSERS['isaac-official-not-published']=parse_isaac_not_published
PARSERS['slowcali-finished-nutrition']=parse_slowcali


def validate_nutrition(fact, allowed_hosts):
    uri = urlparse(fact['sourceUrl'])
    assert uri.scheme in ('https', 'http') and not uri.username and not uri.password
    assert uri.hostname in allowed_hosts
    assert datetime.fromisoformat(fact['checkedAt'].replace('Z', '+00:00')).utcoffset() is not None
    assert re.fullmatch('[a-f0-9]{64}', fact['sourceSha256'])
    for key in FIELDS + OPTIONAL_FIELDS:
        value = fact.get(key)
        assert value is None or isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and 0 <= value <= 100000
    amount = fact.get('servingAmount')
    assert amount is None and fact.get('servingUnit') is None or isinstance(amount, (int, float)) and not isinstance(amount, bool) and math.isfinite(amount) and 0 < amount <= 10000 and fact.get('servingUnit') in ('g', 'ml', '개', '인분', '잔', 'oz')
    if any(fact.get(k) is not None for k in OPTIONAL_FIELDS):
        assert amount is not None, 'Optional nutrition requires an explicit serving basis'


def status(fact):
    if fact.get('notPublishedConfirmed') and not any(fact.get(k) is not None for k in FIELDS):
        return 'NUTRITION_NOT_PUBLISHED'
    if fact.get('energyKcal') is not None and fact.get('servingAmount') is not None:
        return 'NUTRITION_COMPLETE'
    return 'NUTRITION_PARTIAL' if any(fact.get(k) is not None for k in FIELDS) else 'NUTRITION_SOURCE_FOUND_UNMATCHED'


def fetch_official_nutrition(url):
    post = urlparse(url).hostname=='www.ediya.com' and urlparse(url).path=='/inc/ajax_brand.php'
    body=b'' if post else None
    headers={'User-Agent': 'HealthcareMenuAudit/1.0 (official nutrition refresh)'}
    if url=='https://web-prd.burgerking.co.kr/burgerking/BKR0347.json':
        from urllib.parse import urlencode
        message=dict(header=dict(result=True,error_code='',error_text='',info_text='',message_version='',login_session_id='',trcode='BKR0347'),body={})
        body=urlencode(dict(message=json.dumps(message,separators=(',',':')))).encode()
        headers['Content-Type']='application/x-www-form-urlencoded; charset=UTF-8'
    if urlparse(url).hostname=='www.pascucci.co.kr' and urlparse(url).path=='/product/ajax/productDetail.asp':
        from urllib.parse import urlencode
        params=parse_qs(urlparse(url).query)
        assert set(params)=={'productSeq'} and len(params['productSeq'])==1 and params['productSeq'][0].isdigit()
        body=urlencode(dict(productSeq=params['productSeq'][0])).encode()
        headers['Content-Type']='application/x-www-form-urlencoded'
    with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers), timeout=12) as response:
        assert response.status == 200 and urlparse(response.url).hostname == urlparse(url).hostname
        limit = 6_000_000 if url == OOZY_IMAGE_URL else 2_000_000
        payload = response.read(limit + 1)
        assert 0 < len(payload) <= limit
        return payload


def refresh(menus, registry=None, fetcher=None):
    discover_live = registry is None and fetcher is None
    registry = registry if registry is not None else json.loads(REGISTRY.read_text(encoding='utf-8'))
    original_menus = menus
    menus, reviewed_audit = load_reviewed_nutrition(menus, registry)
    sources = {b['brandId']: b for b in registry}
    checked = datetime.now(timezone.utc).isoformat()
    audit = []
    snapshots = {m['id']:m for m in json.loads(REVIEWED.read_text(encoding='utf-8'))} if REVIEWED.exists() else {}

    get = fetcher or fetch_official_nutrition
    discovery=[]
    if discover_live:
        from franchise_daily_sync import CONFIG
        from discover_franchise_nutrition_sources import discover
        brands=json.loads(CONFIG.read_text(encoding='utf-8'))
        unresolved=[b for b in brands if sources.get(b['brandId'],{}).get('adapter') not in PARSERS]
        discovery=discover(unresolved,out=None)
        for row in discovery:
            found=[]
            for link in row['links']:
                try:
                    payload=get(link['url']);text=payload.decode('utf-8',errors='replace')
                    # A link alone is not proof that usable nutrition exists.
                    if re.search(r'kcal|열량|단백질|영양성분',text,re.I):found.append(link['url'])
                except Exception:pass
            row['confirmedNutritionSources']=found
            if found:
                source=sources.get(row['brandId'])
                if source is not None:source['evidenceStatus']='SOURCE_FOUND';source['discoveredSources']=found
    shared = {}
    dunkin_cache = {}
    dunkin_lock = threading.Lock()

    def dunkin_cached(url, allowed_hosts):
        uri = urlparse(url)
        assert uri.scheme == 'https' and uri.netloc == DUNKIN_HOST and DUNKIN_HOST in allowed_hosts
        with dunkin_lock:
            if url not in dunkin_cache:
                try:
                    data = get(url)
                    assert isinstance(data, bytes) and 0 < len(data) <= 2000000
                    dunkin_cache[url] = data
                except Exception as error:
                    dunkin_cache[url] = error
            value = dunkin_cache[url]
        if isinstance(value, Exception):
            raise value
        return value

    def dunkin_crosswalk(menu, source):
        external = menu['externalId']
        if external.startswith('products:'):
            return None
        if external:
            assert re.fullmatch(r'productCats:[1-9][0-9]*', external)
            list_url = menu['sourceUrl']
            uri = urlparse(list_url)
            assert uri.path == '/menu' and not uri.fragment
            query = parse_qs(uri.query, keep_blank_values=True)
            assert 'cat' in query and set(query) <= {'cat', 'page', 'sub'}
            assert all(len(v) == 1 and re.fullmatch(r'[1-9][0-9]*', v[0]) for v in query.values())
            props = dunkin_page_data(dunkin_cached(list_url, source['allowedHosts']), 'Client/Menu/List')
            group_id = int(external.split(':')[1])
            records = props['productCats']['data']
            matches = [r for r in records if type(r.get('id')) is int and r['id'] == group_id]
            assert len(matches) == 1 and matches[0]['TITLE'] == menu['name']
            group = matches[0]
            ids = [group.get(k) for k in ('dd_product_cat1_id', 'dd_product_cat2_id', 'id')]
            assert all(type(v) is int and v > 0 for v in ids)
            assert ids[0] == int(query['cat'][0])
            assert 'sub' not in query or ids[1] == int(query['sub'][0])
            url = 'https://' + DUNKIN_HOST + '/menu/viewProductCat?cat=%d&sub=%d&id=%d' % tuple(ids)
        else:
            url = menu['sourceUrl']
            dunkin_group_query(url)
        return dict(sourceUrl=url, payload=dunkin_cached(url, source['allowedHosts']), checkedAt=checked)
    # A paginated page contains multiple products. Read each official page once.
    page_urls={m['sourceUrl'] for m in menus if sources.get(m['brandId'],{}).get('adapter') in PARSERS
               and not sources.get(m['brandId'],{}).get('url')}
    page_urls.update(m['sourceUrl'] for m in menus if sources.get(m['brandId'],{}).get('adapter')=='mc-nutrition-json')
    def page(url):
        try:return url,get(url)
        except Exception as error:return url,error
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        shared.update(pool.map(page,sorted(page_urls)))
    for source in registry:
        if source.get('adapter') in PARSERS and source.get('url') and any(m['brandId']==source['brandId'] for m in menus):
            try: shared[source['url']] = dunkin_pages(source['url'], get) if source['adapter']=='dunkin-nutrition-pages' else get(source['url'])
            except Exception as error: shared[source['url']] = error

    listing_payload = None
    listing_error = None
    listing_attempted = False
    listing_lock = threading.Lock()
    oozy_images = {}
    oozy_lock = threading.Lock()

    def oozy_image(url):
        assert url == OOZY_IMAGE_URL
        with oozy_lock:
            if url not in oozy_images:
                try:oozy_images[url] = get(url)
                except Exception as error:oozy_images[url] = error
            value = oozy_images[url]
        if isinstance(value, Exception):raise value
        return value

    def tlj_listing():
        nonlocal listing_payload, listing_error, listing_attempted
        # Fetch lazily only after canonical/title scope checks require a bridge.
        # Concurrent selected menus share both success and failure, exactly once.
        with listing_lock:
            if not listing_attempted:
                listing_attempted = True
                try:
                    listing_payload = get(TLJ_LIST_URL)
                    assert isinstance(listing_payload, bytes) and listing_payload, 'Official listing bytes required'
                except Exception as error:
                    listing_error = type(error).__name__ + ': ' + str(error)[:300]
                    listing_payload = None
            return listing_payload

    def one(original):
        menu = dict(original)
        source = sources.get(menu['brandId'])
        adapter = source.get('adapter') if source else None
        evidence = dict(id=menu['id'], brandId=menu['brandId'], brand=menu['brand'], name=menu['name'])
        if adapter in PARSERS:
            try:
                url = source.get('url') or menu['sourceUrl']
                if adapter=='pascucci-official-nutrition':
                    assert menu['externalId'].isdigit()
                    url='https://www.pascucci.co.kr/product/ajax/productDetail.asp?productSeq='+menu['externalId']
                assert urlparse(url).hostname in source['allowedHosts']
                payload = shared[url] if url in shared else get(url)
                if isinstance(payload, Exception) and adapter != 'ediya-product-nutrition':raise payload
                if adapter=='mammoth-food-official-nutrition':
                    url=mammoth_food_detail_url(menu,payload)
                    assert urlparse(url).hostname in source['allowedHosts']
                    payload=get(url)
                if adapter=='mc-nutrition-json':
                    try:
                        fact=PARSERS[adapter](menu,payload)
                        assert any(fact.get(k) is not None for k in FIELDS)
                    except AssertionError:
                        url=menu['sourceUrl'];payload=shared[url]
                        if isinstance(payload,Exception):raise payload
                        fact=parse_mcdonalds_product(menu,payload)
                elif adapter == 'tlj-official-nutrition':
                    try:
                        fact = parse_tlj(menu, payload)
                    except TljListingRequired:
                        listing = tlj_listing()
                        if listing_error:
                            evidence['listingFailureReason'] = listing_error
                        fact = parse_tlj(menu, payload, listing_payload=listing)
                elif adapter == 'dunkin-nutrition-pages':
                    fact = parse_dunkin(menu, payload, crosswalk=dunkin_crosswalk(menu, source))
                elif adapter == 'ediya-product-nutrition':
                    try:
                        if isinstance(payload, Exception):raise AssertionError('Previous Ediya page unavailable')
                        fact = parse_ediya(menu, payload)
                    except AssertionError:
                        url, payload, fact = resolve_ediya_page(menu, shared, source['allowedHosts'])
                elif adapter == 'oozy-reviewed-image-nutrition':
                    fact = parse_oozy_image(menu, payload, oozy_image, checked)
                else:
                    fact = PARSERS[adapter](menu, payload)
                fact.update(sourceUrl=fact.get('sourceUrl',url), checkedAt=checked,
                            sourceSha256=fact.get('sourceSha256',hashlib.sha256(payload).hexdigest()), officialProductId=menu['externalId'])
                fact['sourceType']='PDF' if adapter=='salady-nutrition-pdf' else 'JSON' if adapter in ('mc-nutrition-json','burgerking-official-nutrition') else 'HTML'
                validate_nutrition(fact, source['allowedHosts'])
                fact, conflicts = retain_verified_on_conflict(menu, fact)
                menu['officialNutrition'] = fact
                menu['nutritionStatus'] = status(fact)
                evidence.update(result='CONFLICT_RETAINED' if conflicts else 'MATCHED',
                                conflicts=conflicts, nutritionStatus=menu['nutritionStatus'], sourceUrl=fact['sourceUrl'])
                if fact.get('optionalFieldEvidence'):
                    evidence['optionalFieldEvidence'] = fact['optionalFieldEvidence']
                if fact.get('groupCrosswalkEvidence'):
                    evidence['groupCrosswalkEvidence'] = copy.deepcopy(fact['groupCrosswalkEvidence'])
                    evidence['nutritionProductId'] = fact['nutritionProductId']
            except Exception as error:
                # Never erase previously verified official values on fetch/schema errors.
                captured=snapshots.get(menu['id'])
                if not menu.get('officialNutrition') and isinstance(error,(urllib.error.URLError,TimeoutError)) and captured:
                    assert all(captured[k]==menu[k] for k in ('brandId','brand','name','externalId','sourceUrl')),'Reviewed snapshot identity changed'
                    fact=captured['officialNutrition']
                    assert fact['officialProductId']==menu['externalId']
                    validate_nutrition(fact,source['allowedHosts'])
                    # Keep the actual capture timestamp. A failed cloud fetch is
                    # never represented as fresh verification or a successful fetch.
                    menu['officialNutrition']=dict(fact)
                    evidence['reviewedSnapshotRetained']=True
                menu['nutritionStatus'] = status(menu['officialNutrition']) if menu.get('officialNutrition') else 'NUTRITION_SOURCE_FOUND_UNMATCHED'
                evidence.update(result='FAILED_RETAINED', nutritionStatus=menu['nutritionStatus'], error=type(error).__name__, reason=str(error)[:300])
                retained = menu.get('officialNutrition') or {}
                if retained.get('groupCrosswalkEvidence'):
                    validate_dunkin_crosswalk(menu, retained, source['allowedHosts'])
                    evidence['groupCrosswalkEvidence'] = copy.deepcopy(retained['groupCrosswalkEvidence'])
                    evidence['nutritionProductId'] = retained['nutritionProductId']
        else:
            menu['nutritionStatus'] = status(menu['officialNutrition']) if menu.get('officialNutrition') else 'NUTRITION_SOURCE_FOUND_UNMATCHED' if source and source.get('evidenceStatus')=='SOURCE_FOUND' else 'NUTRITION_SOURCE_MISSING'
            evidence.update(result='SOURCE_MISSING', nutritionStatus=menu['nutritionStatus'])
        return menu, evidence

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(one, menus))
    refreshed = [m for m, _ in rows]
    audit = [a for _, a in rows]
    counts = Counter(m['nutritionStatus'] for m in refreshed)
    assert all(m['nutritionStatus'] in STATUSES for m in refreshed)
    known = {k: sum(m.get('officialNutrition', {}).get(k) is not None for m in refreshed) for k in FIELDS + ('servingAmount',)}
    old={m['id']:m.get('officialNutrition',{}) for m in original_menus}
    linked=lambda m:any(m.get(k) is not None for k in FIELDS)
    newly=sum(linked(m.get('officialNutrition',{})) and not linked(old[m['id']]) for m in refreshed)
    changed=sum(linked(old[m['id']]) and any(old[m['id']].get(k)!=m.get('officialNutrition',{}).get(k) for k in FIELDS+('servingAmount','servingUnit')) for m in refreshed)
    return refreshed, dict(total=len(refreshed), states=dict(counts), known=known,
                           sourceDiscovery=discovery,
                           reviewedReplay=reviewed_audit,
                           conflicts=[c for c in reviewed_audit if 'differences' in c] + [c for a in audit for c in a.get('conflicts', [])],
                           nutritionLinkedMenus=sum(linked(m.get('officialNutrition',{})) for m in refreshed),
                           newlyVerifiedMenus=newly, nutritionChangedMenus=changed,
                           reviewedSnapshotsRetained=sum(a.get('reviewedSnapshotRetained',False) for a in audit),
                           recordable=sum(m.get('officialNutrition', {}).get('energyKcal') is not None and m.get('officialNutrition', {}).get('servingAmount') is not None for m in refreshed),
                           matched=sum(a['result']=='MATCHED' for a in audit), failed=sum(a['result']=='FAILED_RETAINED' for a in audit), menus=audit)
