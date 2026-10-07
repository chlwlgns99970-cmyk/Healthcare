"""New school/public-catering strategy, avoiding previous receipt queries."""
import json
import re
from audit_recipe_residual_97 import ROOT, OUT, load, save


def run():
    queue = {r['recipeId']: r for r in load(ROOT / 'data-source/recipe-reference-finalization/reference-first-work-queue.json')}
    searches = []
    for item in load(OUT / 'scope-97.json'):
        q = queue[item['recipeId']]
        variants = q['searchVariants']
        variant = next((v for v in reversed(variants) if '(' not in v and '/' not in v), variants[0])
        variant = variant.replace('머위대', '머윗대').replace('쥬스', '주스').replace('스프', '수프')
        # Ingredients and cooking method, with public catering/school institutions
        # as a new search strategy; quantities from snippets never enter assets.
        query = '"' + variant + '" (레시피 OR 조리 OR 재료) (site:go.kr OR site:re.kr OR site:edu OR site:school.kr)'
        searches.append(dict(recipeId=item['recipeId'], name=item['name'], query=query,
                             strategy='NAME_VARIANT_PUBLIC_CATERING_EDUCATION', snippetsAreNotNumericEvidence=True))
    assert len(searches) == 97 and len({r['query'] for r in searches}) >= 95
    save('new-search-plan.json', searches)
    print(json.dumps(searches, ensure_ascii=False))


if __name__ == '__main__':
    run()
