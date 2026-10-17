"""Offline, fail-closed preparation of reviewable franchise publication files.

No network, Git write, collection, or publication commands are run here.
Both input snapshots and their hashes are retained beside the candidate.
"""
from pathlib import Path
import argparse
import copy
import hashlib
import json
from collections import Counter
from datetime import datetime, timezone

import franchise_daily_sync as daily

NUTRIENTS = ('energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams',
             'sodiumMilligrams', 'sugarsGrams', 'saturatedFatGrams')
FACT_FIELDS = NUTRIENTS + ('servingAmount', 'servingUnit')
IDENTITY = ('id', 'brandId', 'brand', 'name', 'externalId', 'category', 'sourceUrl')

def facts(menu):
    return menu.get('officialNutrition') or menu

def known_values_preserved(before, after):
    old, new = facts(before), facts(after)
    return all(old.get(k) is None or old[k] == new.get(k) for k in FACT_FIELDS)

def validate_catalog(document, brands):
    if type(document.get('schemaVersion')) is not int or document['schemaVersion'] != 1 or not isinstance(document.get('menus'), list) or len(document['menus'])>10000:
        raise ValueError('Invalid catalog schema')
    by_brand = {b['brandId']: b for b in brands}
    seen_ids, seen_names, seen_products = set(), set(), set()
    for row in document['menus']:
        daily.validate(row, by_brand[row['brandId']])
        if row.get('officialNutrition') is not None:
            if not isinstance(row['officialNutrition'],dict):raise ValueError('Invalid nutrition snapshot')
            if row['officialNutrition']['officialProductId'] != row['externalId']:
                raise ValueError('Nutrition product ID does not match menu: ' + row['id'])
        name = row['brandId'], daily.normalize(row['name'])
        product = row['brandId'], row['externalId']
        if row['id'] in seen_ids or name in seen_names or row['externalId'] and product in seen_products:
            raise ValueError('Duplicate menu identity requires review: ' + row['id'])
        seen_ids.add(row['id']); seen_names.add(name)
        if row['externalId']: seen_products.add(product)
    return {m['id']: m for m in document['menus']}

def load_snapshot(catalog_path, manifest_path, brands):
    payload = Path(catalog_path).read_bytes()
    manifest = json.loads(Path(manifest_path).read_bytes())
    if not (len(payload) <= daily.MAX_CATALOG_BYTES and manifest.get('schemaVersion') == 1
            and hashlib.sha256(payload).hexdigest() == manifest.get('sha256')):
        raise ValueError('Snapshot checksum/schema mismatch')
    document = json.loads(payload)
    validate_catalog(document, brands)
    if len(document['menus']) != manifest.get('menuCount'):
        raise ValueError('Snapshot count mismatch')
    return document, manifest, payload

def describe(row):
    return {k: row.get(k) for k in IDENTITY + ('checkedAt', 'sourceType', 'nutritionStatus')}

def compare_catalogs(local, published):
    a = {m['id']: m for m in local['menus']}; b = {m['id']: m for m in published['menus']}
    common = a.keys() & b.keys()
    differences, identity_conflicts, value_conflicts = [], [], []
    field_counts = Counter(); fills = Counter(); missing_local = Counter()
    for key in sorted(common):
        l, r = a[key], b[key]
        changed = [k for k in sorted(l.keys() | r.keys()) if l.get(k) != r.get(k) or (k in l) != (k in r)]
        fact_changes = {k: dict(published=facts(r).get(k), local=facts(l).get(k)) for k in FACT_FIELDS
                        if facts(r).get(k) != facts(l).get(k)}
        for k, values in fact_changes.items():
            if values['published'] is None: fills[k] += 1
            elif values['local'] is None: missing_local[k] += 1
            else: value_conflicts.append(dict(id=key, field=k, **values))
        identity_changes = [k for k in IDENTITY if l.get(k) != r.get(k)]
        if identity_changes:
            identity_conflicts.append(dict(id=key, fields=identity_changes,
                published={k:r.get(k) for k in identity_changes}, local={k:l.get(k) for k in identity_changes}))
        if changed:
            field_counts.update(changed)
            differences.append(dict(**describe(l), changedFields=changed, factChanges=fact_changes,
                publishedCheckedAt=r.get('checkedAt'), localCheckedAt=l.get('checkedAt'),
                publishedNutritionProof={k:facts(r).get(k) for k in ('sourceUrl','sourceSha256','checkedAt')},
                localNutritionProof={k:facts(l).get(k) for k in ('sourceUrl','sourceSha256','checkedAt')}))
    local_only = [describe(a[k]) for k in sorted(a.keys() - b.keys())]
    remote_only = [describe(b[k]) for k in sorted(b.keys() - a.keys())]
    return dict(localCount=len(a), publishedCount=len(b), commonCount=len(common),
        localOnlyCount=len(local_only), publishedOnlyCount=len(remote_only),
        exactCommonCount=sum(a[k] == b[k] for k in common), differentCommonCount=len(differences),
        effectiveNutritionDifferentCount=sum(bool(d['factChanges']) for d in differences),
        rawNutritionDifferentCount=field_counts['officialNutrition'], changedFieldCounts=dict(field_counts),
        localAdditionalVerifiedFields=dict(fills), missingLocalVerifiedFields=dict(missing_local),
        identityConflicts=identity_conflicts, knownValueConflicts=value_conflicts,
        localOnlyByBrand=dict(Counter(r['brand'] for r in local_only)),
        localOnly=local_only, publishedOnly=remote_only, changedCommon=differences,
        countMeaning='Local-only stable IDs are unpublished catalog entries, not newly collected products or proven newly launched products')

def merge_verified(local, published, brands):
    """Retain one complete, corroborated snapshot per ID; no value arithmetic."""
    a=validate_catalog(local, brands); b=validate_catalog(published, brands)
    rows=[]
    for key in sorted(a.keys() | b.keys()):
        l, r = a.get(key), b.get(key)
        if l is None or r is None:
            rows.append(copy.deepcopy(l if l is not None else r)); continue
        if any(l.get(k) != r.get(k) for k in IDENTITY):
            raise ValueError('Identity change requires review: ' + key)
        if known_values_preserved(r, l):
            chosen=copy.deepcopy(l)
        elif known_values_preserved(l, r):
            chosen=copy.deepcopy(r)
        else:
            raise ValueError('Conflicting/complementary verified facts require review: ' + key)
        # This is a recorded collection time, not a product creation date.
        # Keep the later actual observation without changing nutrition proof.
        latest=max((l['checkedAt'],r['checkedAt']), key=lambda value:datetime.fromisoformat(value.replace('Z','+00:00')))
        chosen['checkedAt']=latest
        if not known_values_preserved(l,chosen) or not known_values_preserved(r,chosen):
            raise ValueError('Known nutrition loss: ' + key)
        rows.append(chosen)
    candidate=dict(schemaVersion=1,menus=[daily.published_menu(r) for r in rows])
    validate_catalog(candidate, brands)
    return candidate

def prepare(local_path, local_manifest, published_dir, out, brands=None):
    brands=brands or json.loads(daily.CONFIG.read_text(encoding='utf-8'))
    published_dir=Path(published_dir); out=Path(out)
    published_manifest=json.loads((published_dir/'latest.json').read_bytes())
    digest=published_manifest.get('sha256','')
    if not isinstance(digest,str) or not daily.re.fullmatch('[a-f0-9]{64}',digest) or published_manifest.get('file') != 'catalog-'+digest+'.json' or published_manifest.get('version')!=digest:
        raise ValueError('Invalid published manifest filename')
    local, lm, lp=load_snapshot(local_path,local_manifest,brands)
    remote, rm, rp=load_snapshot(published_dir/published_manifest['file'],published_dir/'latest.json',brands)
    diff=compare_catalogs(local,remote)
    candidate=merge_verified(local,remote,brands)
    payload=json.dumps(candidate,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
    if len(payload)>daily.MAX_CATALOG_BYTES:raise ValueError('Candidate exceeds Android download limit')
    checksum=hashlib.sha256(payload).hexdigest(); filename='catalog-'+checksum+'.json'
    # A prepared snapshot cannot change the supplied input directories/files.
    resolved=out.resolve()
    inputs=(published_dir.resolve(),Path(local_path).resolve().parent,Path(local_manifest).resolve().parent)
    if any(resolved==root or root in resolved.parents for root in inputs):
        raise ValueError('Output must be isolated from source snapshots')
    if out.exists() and any(out.iterdir()):
        plan=json.loads((out/'preparation.json').read_bytes())
        existing=json.loads((out/'latest.json').read_bytes())
        if (plan['localInputSha256']!=lm['sha256'] or plan['expectedPublishedSha256']!=rm['sha256'] or
                plan['candidateSha256']!=checksum or plan['status']!='READY_FOR_REVIEW_NOT_PUBLISHED' or
                existing.get('schemaVersion')!=1 or existing.get('file')!=filename or existing.get('version')!=checksum or
                existing.get('menuCount')!=len(candidate['menus']) or existing.get('publicationState')!='PREPARED_NOT_PUBLISHED' or
                existing['sha256']!=checksum or (out/filename).read_bytes()!=payload or
                json.loads((out/'comparison.json').read_bytes())!=diff or
                (out/'rollback'/rm['file']).read_bytes()!=rp or
                json.loads((out/'rollback/latest.json').read_bytes())!=rm):
            raise ValueError('Existing candidate differs; choose a new output directory')
        return dict(status='UNCHANGED_PREPARED_CANDIDATE',sha256=checksum,menuCount=len(candidate['menus']))
    out.mkdir(parents=True,exist_ok=True)
    prepared=datetime.now(timezone.utc).isoformat()
    manifest=dict(schemaVersion=1,file=filename,sha256=checksum,version=checksum,menuCount=len(candidate['menus']),
        runKey='prepared-'+checksum[:16],preparedAt=prepared,publicationState='PREPARED_NOT_PUBLISHED')
    plan=dict(status='READY_FOR_REVIEW_NOT_PUBLISHED',preparedAt=prepared,
        localInputSha256=lm['sha256'],expectedPublishedSha256=rm['sha256'],candidateSha256=checksum,
        localMenuCount=len(local['menus']),publishedMenuCount=len(remote['menus']),candidateMenuCount=len(candidate['menus']),
        knownNutritionConflicts=len(diff['knownValueConflicts']),identityConflicts=len(diff['identityConflicts']),
        preservedLocalIds=len(local['menus']),preservedPublishedIds=len(remote['menus']),
        deferredBrandsPreserved=sorted(daily.DEFERRED_BRANDS),
        publishFiles=['latest.json',filename],rollbackFiles=['rollback/latest.json','rollback/'+rm['file']],
        publishRequires=['Explicit user approval for franchise-data update',
            'Recheck live manifest SHA equals expectedPublishedSha256 before publishing',
            'Review/deploy only franchise-related source changes separately; exclude unrelated dirty files',
            'Validate remote bytes and app receipt after approved publication'],
        operationalPublicationPerformed=False)
    files={filename:payload,'latest.json':json.dumps(manifest,sort_keys=True).encode(),
        'comparison.json':json.dumps(diff,ensure_ascii=False,indent=2).encode(),
        'preparation.json':json.dumps(plan,ensure_ascii=False,indent=2).encode(),
        'rollback/latest.json':(published_dir/'latest.json').read_bytes(),'rollback/'+rm['file']:rp}
    for name,data in files.items():
        target=out/name;target.parent.mkdir(parents=True,exist_ok=True);daily.atomic_write(target,data)
    return dict(status=plan['status'],sha256=checksum,menuCount=len(candidate['menus']),
        localOnly=diff['localOnlyCount'],common=diff['commonCount'],changed=diff['differentCommonCount'],
        nutritionChanged=diff['effectiveNutritionDifferentCount'])

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--local-catalog',type=Path,default=daily.ROOT/'app/src/main/assets/franchise/catalog.json')
    parser.add_argument('--local-manifest',type=Path,default=daily.ROOT/'app/src/main/assets/franchise/catalog-manifest.json')
    parser.add_argument('--published-dir',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    print(json.dumps(prepare(args.local_catalog,args.local_manifest,args.published_dir,args.output),ensure_ascii=False))
