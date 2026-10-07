"""Collect same-form public cookbook g/measure pairs, retaining conflicts."""
import collections
import json
import re
from fractions import Fraction
from index_recipe_amount_references import OUT, load, save
from adjudicate_recipe_inputs import norm, ROOT, read

FRACTIONS = {'½': .5, '⅓': 1/3, '⅔': 2/3, '¼': .25, '¾': .75, '⅛': .125}


def quantity(text):
    text = text.strip()
    if text and text[-1] in FRACTIONS:
        return (float(text[:-1]) if text[:-1] else 0) + FRACTIONS[text[-1]]
    return float(Fraction(text))


def build():
    originals = load(OUT / 'baseline-ingredient-decisions.json')
    rows = [d for d in originals if d['status'] not in ('LINKED', 'EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM')
        and d['amountGrams'] is None and d['quantity'] is not None and not d['quantityRange']]
    pairs = {(norm(d['ingredient']), d['unit']) for d in rows if d['unit']}
    evidence = collections.defaultdict(list)
    documents = [(path.name, load(path)) for path in sorted(OUT.glob('*pages.json')) if 'kdca' not in path.name]
    facts = [r for path in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv') for r in read(path)]
    documents += [('original-recipe-facts', [dict(page=r['recipeId'], text=r['mainIngredientText']+', '+r['additionalIngredientText']) for r in facts])]
    for document, pages in documents:
        for page in pages:
            # Keep whitespace as an ingredient boundary: 간장 != 저염간장.
            text = re.sub(r'[\r\n]+', '\n', page['text'])
            for ingredient, unit in pairs:
                name = r'\s*'.join(map(re.escape, ingredient))
                pattern = r'(?<![가-힣])' + name + r'\s*(\d+(?:\.\d+)?)\s*g\s*\(\s*([\d./½⅓⅔¼¾⅛]+)\s*' + re.escape(unit) + r'\s*\)'
                for match in re.finditer(pattern, text):
                    count = quantity(match[2])
                    if count <= 0:
                        continue
                    evidence[ingredient, unit].append(dict(file=document, page=page['page'],
                        originalSpan=match[0], grams=float(match[1]), measureQuantity=count,
                        gramsPerUnit=float(match[1])/count,
                        previousSource=document in ('mfds-book-1-pages.json', 'original-recipe-facts')))
    result = []
    for d in rows:
        refs = evidence[norm(d['ingredient']), d['unit']]
        values = sorted({round(r['gramsPerUnit'], 6) for r in refs})
        result.append(dict(recipeId=d['recipeId'], ingredientIndex=d['ingredientIndex'],
            ingredient=d['ingredient'], originalSpan=d['originalSpan'], unit=d['unit'],
            identityStatus=d['identityStatus'], references=refs, gramsPerUnitCandidates=values,
            result='CONFLICTING_OFFICIAL_PORTIONS' if len(values)>1 else
                'SINGLE_REFERENCE_REQUIRES_FORM_REVIEW' if values else 'NO_EXACT_FORM_PORTION',
            originalAmountOverridden=False, checkedAt='2026-10-05'))
    save('official-book-portion-candidates.json', result)
    print(json.dumps(dict(rows=len(result), results=dict(collections.Counter(r['result'] for r in result))), ensure_ascii=False))
    return result


if __name__ == '__main__':
    build()
