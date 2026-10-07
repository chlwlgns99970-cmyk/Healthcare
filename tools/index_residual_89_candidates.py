"""Reindex every captured composition for residual retrieval, never publication."""
import difflib
import json
import re
from pathlib import Path
from recipe_composition_validation import identity_key

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-residual-89'


def ingredient_terms(value):
    # Qualifier-aware names remain intact in evidence; tokenization is used
    # solely to avoid zero coverage when a DB says '호박, 애호박, 생것'.
    return set(re.findall(r'[가-힣]{2,}',value)) - {'생것','말린것','삶은것','개량','가루','국내산'}


def run():
    queue=json.loads((OUT/'scope-89.json').read_text(encoding='utf-8'))
    refs=json.loads((ROOT/'data-source/recipe-final-residual/validated-reference-compositions.json').read_text(encoding='utf-8'))
    output=[]
    for food in queue:
        original=set().union(*(ingredient_terms(r['ingredient']) for r in food['originalUnresolved']))
        proposals=[]
        for ref in refs:
            keys=[identity_key(k) for k in food['retrievalKeys']]
            reference_keys=[identity_key(ref['name']),identity_key(re.split(r'[（(]',ref['name'])[0])]
            similarity=max(difflib.SequenceMatcher(None,k,rk).ratio() for k in keys for rk in reference_keys)
            ingredients=set().union(*(ingredient_terms(r['ingredient']) for r in ref['inputs']))
            overlap=sorted(original & ingredients)
            score=similarity + 0.15*len(overlap)/max(1,len(original))
            if similarity<0.35:
                continue
            proposals.append(dict(recipeId=ref['recipeId'],name=ref['name'],complete=ref['complete'],
                score=score,titleSimilarity=similarity,retrievalIngredientOverlap=overlap,
                declaredIngredients=[x['ingredient'] for x in ref['inputs']],
                unresolvedInputs=[x['ingredient'] for x in ref['inputs'] if not x.get('nutrient') or not x.get('amountGrams')],
                compositionKind=ref['compositionKind'],sourceUrl=ref['sourceUrl'],sourceSha256=ref['sourceSha256'],
                identityDecision='REQUIRES_PRIMARY_SPECIES_STATE_METHOD_REVIEW',publicationAuthorized=False))
        proposals.sort(key=lambda p:(-p['score'],not p['complete'],p['recipeId']))
        output.append(dict(recipeId=food['recipeId'],name=food['name'],compositionsScanned=len(refs),
                           proposals=proposals[:15],automaticPublications=0))
    (OUT/'bulk-reindexed-candidates.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(foods=len(output),compositions=len(refs),automaticPublications=0)))


if __name__=='__main__':run()
