"""Immutable 419 baseline and evidence-first, mutually exclusive residual audit.

Candidate names propose review. Only approved identities establish a food link.
Search aliases are never treated as ingredient-independent dish identities.
"""
import collections
import csv
import hashlib
import json
import re
from pathlib import Path
from finish_recipe_reference_mapping import keys, original_keys, REVIEWED_REFERENCES
from recipe_composition_validation import composition_problem
from recipe_context_identity import supported_title_keys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recipe-residual-97'
ASSETS = ROOT / 'app/src/main/assets/fooddata'
FINAL = ROOT / 'data-source/recipe-final-residual'


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def save(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run():
    OUT.mkdir(exist_ok=True)
    if not (OUT / 'baseline-states.json').exists():
        states = load(FINAL / 'recipe-final-states.json')
        assert len(states) == 516 and sum(s['appCompleteAvailable'] for s in states) == 419
        save('baseline-states.json', states)
        save('scope-97.json', load(ROOT / 'data-source/recipe-reference-finalization/remaining-97-foods.json'))
        for name in ('official_recipe_reference_estimates.csv', 'recipe_ingredient_estimates.csv'):
            (OUT / ('baseline-' + name)).write_bytes((ASSETS / name).read_bytes())
        protected = ['food_items.csv', 'product_items.csv', 'franchise_official_items.csv', 'recipe_ingredient_estimates.csv']
        save('protected-sha256.json', {name: digest(ASSETS / name) for name in protected})
        save('baseline-ingredient-progress.json', load(FINAL / 'original-ingredient-progress.json'))
    scope = load(OUT / 'scope-97.json')
    states = {s['recipeId']: s for s in load(OUT / 'baseline-states.json')}
    references = load(FINAL / 'validated-reference-compositions.json')
    originals = {s['recipeId']: s for s in load(ROOT / 'data-source/recipe-full-reference/recipe-final-audit.json')['audit']}
    contexts = {s['recipeId']: s for s in load(ROOT / 'data-source/recipe-reference-finalization/reference-first-work-queue.json')}
    foods = sum((rows(ASSETS / name) for name in ('food_items.csv', 'product_items.csv', 'franchise_official_items.csv')), [])
    assert len(foods) == 67354 and len({f['id'] for f in foods}) == 67354
    identity_keys = {item['recipeId']: original_keys(item['name']) | set().union(*(keys(k) for k in
                    supported_title_keys(item['name'], contexts[item['recipeId']]['originalContext']))) for item in scope}
    allowed_keys = set().union(*identity_keys.values())
    catalog_index = collections.defaultdict(list)
    for food in foods:
        name_keys = keys(food['name']) & allowed_keys
        normalized_keys = keys(food.get('normalizedName', '')) & allowed_keys
        alias_by_key = collections.defaultdict(list)
        for alias in food.get('aliases', '').split('|'):
            for key in keys(alias) & allowed_keys:
                alias_by_key[key].append(alias)
        for key in name_keys | normalized_keys | alias_by_key.keys():
            catalog_index[key].append((food, key in name_keys, key in normalized_keys, alias_by_key[key]))
    reference_rows = rows(OUT / 'baseline-official_recipe_reference_estimates.csv') + rows(OUT / 'baseline-recipe_ingredient_estimates.csv')
    groups = collections.defaultdict(list)
    for row in reference_rows:
        groups[row['foodId'], row['recipeId']].append(row)
    classifications = []
    for item in scope:
        state = states[item['recipeId']]
        allowed = identity_keys[item['recipeId']]
        candidates = [r for r in references if keys(r['name']) & allowed or
                      r['compositionKind'] == 'REFERENCE_RECIPE' and keys(r['name'].split('(', 1)[0]) & allowed]
        reviewed = REVIEWED_REFERENCES.get(item['name'])
        if reviewed:
            candidates += [r for r in references if r['recipeId'] == reviewed[0] and r not in candidates]
        complete = [r for r in candidates if r['complete'] and not composition_problem(r)]
        ids = sorted(set(state['targetFoodIds']) | {food['id'] for key in allowed
                     for food, canonical, _, _ in catalog_index[key]
                     if canonical and not food['brand'] and food['sourceType'] == 'K-FIND'})
        # Check physical groups, not a stale queue's publication flag.
        physical = [dict(foodId=fid, recipeId=rid, rows=len(group)) for (fid, rid), group in groups.items()
                    if fid in ids and all(r['recipeComplete'] == 'true' for r in group)
                    and (rid == item['recipeId'] or any(r['recipeId'] == rid for r in complete))]
        composition = originals[item['recipeId']]['complete'] or bool(complete)
        category = 'D' if physical else 'A' if composition and not ids else 'B' if ids else 'C'
        # Full catalog scan: raw/brand aliases remain review candidates only.
        exact = []
        seen = set()
        for food, hits, normalized_hit, alias_hits in sum((catalog_index[key] for key in allowed), []):
            if food['id'] not in seen:
                seen.add(food['id'])
                exact.append({k: food.get(k, '') for k in ('id','name','category','sourceType','sourceFoodCode','referenceAmount','unit','energyKcal','servingDescription','brand')} |
                             dict(canonicalMatch=bool(hits), normalizedMatch=bool(normalized_hit), aliasMatches=alias_hits,
                                  automaticPublication=False))
        classifications.append(dict(recipeId=item['recipeId'], name=item['name'], classification=category,
                                    originalComplete=originals[item['recipeId']]['complete'], verifiedFoodIds=ids,
                                    completeWholeReferenceIds=[r['recipeId'] for r in complete], physicalCompleteGroups=physical,
                                    rejectedReferences=[dict(recipeId=r['recipeId'], problem=composition_problem(r)) for r in candidates if composition_problem(r)],
                                    allCatalogExactCandidates=exact, catalogRowsScanned=len(foods)))
    assert len(classifications) == 97 and len({s['recipeId'] for s in classifications}) == 97
    save('initial-classification.json', classifications)
    summary = dict(total=97, categories=dict(collections.Counter(s['classification'] for s in classifications)),
                   allCatalogFoodCount=len(foods), completeSelfCompositeExcluded=True)
    save('initial-summary.json', summary)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    run()
