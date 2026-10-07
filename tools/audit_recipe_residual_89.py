"""Freeze the current app baseline and enumerate the exact residual scope.

Candidate search keys are for retrieval only; they never authorize publication.
"""
import collections
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recipe-residual-89'
FINAL = ROOT / 'data-source/recipe-final-residual'
ASSETS = ROOT / 'app/src/main/assets/fooddata'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def freeze(name, content):
    path = OUT / name
    if path.exists():
        assert path.read_bytes() == content, f'Baseline changed: {name}'
    else:
        path.write_bytes(content)


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def search_keys(name):
    # Punctuation and vocabulary variants improve recall, not food identity.
    clean = re.sub(r'[^\w가-힣]', '', name).replace('_', '')
    values = [name, clean, re.split(r'[（(]', name)[0]]
    for original, replacement in [('야채', '채소'), ('달걀', '계란'),
                                  ('주꾸미', '쭈꾸미'), ('쥬스', '주스')]:
        if original in clean:
            values.append(clean.replace(original, replacement))
    return list(dict.fromkeys(v for v in values if v))


def run():
    OUT.mkdir(parents=True, exist_ok=True)
    ledger = read(ROOT / 'data-source/recipe-residual-97/all-97-final-evidence-ledger.json')
    states = read(FINAL / 'recipe-final-states.json')
    by_id = {r['recipeId']: r for r in states}
    assert len(states) == len(by_id) == 516
    assert sum(r['appCompleteAvailable'] for r in states) == 421
    queue = []
    questions = []
    for previous in ledger:
        current = by_id[previous['recipeId']]
        if previous['finalDisposition'] == 'QUESTION_REQUIRED_NEW_FOOD':
            assert not current['appCompleteAvailable']
            questions.append(previous)
        elif not current['appCompleteAvailable']:
            assert current['state'] in ('PARTIAL', 'UNRESOLVED')
            queue.append(previous | dict(baselineState=current,
                         retrievalKeys=search_keys(previous['name']),
                         newReviewStatus='PENDING', newEvidence=[],
                         publicationAuthorized=False))
    assert len(queue) == 89 and len(questions) == 6
    assert collections.Counter(r['state'] for r in queue) == {'PARTIAL': 83, 'UNRESOLVED': 6}
    freeze('baseline-states.json', encode(states))
    freeze('scope-89.json', encode(queue))
    freeze('question-required-six-baseline.json', encode(questions))
    protected = {}
    for name in ('official_recipe_reference_estimates.csv', 'recipe_ingredient_estimates.csv',
                 'food_items.csv', 'product_items.csv', 'franchise_official_items.csv'):
        data = (ASSETS / name).read_bytes()
        protected[name] = hashlib.sha256(data).hexdigest()
        if 'estimates' in name:
            freeze('baseline-' + name, data)
    freeze('protected-asset-sha256.json', encode(protected))
    freeze('baseline-original-progress.json', (FINAL / 'original-ingredient-progress.json').read_bytes())
    summary = dict(total=516, appComplete=421, remaining=95, residual=89,
                   states=dict(collections.Counter(r['state'] for r in states)),
                   questionRequired=6, productionWrites=0)
    freeze('baseline-summary.json', encode(summary))
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    run()
