# Recommendation source completion

Preserves the 292 template identities and their original nutrition and ingredient
amount assets. New source composition is partial evidence, never a complete
ingredient/allergen declaration or a universal recipe.

Offline replay:

```powershell
python tools/collect_catalog_recommendation_sources.py
python tools/build_catalog_recommendation_groups.py
python tools/build_recommendation_ingredient_evidence.py
python tools/generate_verified_ingredient_groups.py
python tools/build_catalog_recommendation_groups.py --audit
python tools/build_catalog_recommendation_portions.py
python -m unittest discover -s tools -p test_catalog_recommendation.py
```

Use the configured bundled Python executable when `python` is not on PATH. Online
collection is explicitly enabled by `--collect`. Nine exact public records are
the finite collection scope. Raw snapshots are in
`app/build/catalog-recommendation/raw`; their hashes and source URLs are preserved
in `source-request-audit.json`. No key or authenticated service is used.

`reviewed-group-evidence.csv` contains seven reviewed source joins, including
orthographic/synonymous dish names and two explicitly stated alternate recipes
with measured main ingredients. The CJ official 2022-06-03 release describes the
exact historical 뚜레쥬르 베지텐더 밸런스랩. Its adjacent named product's falafel,
chickpeas and frying are deliberately not transferred. Current-product recipe
equivalence and full ingredient/allergy information remain unresolved.

`metadata-evidence.csv` exports literal ingredients and provenance for exact ID,
source-food-code, original-name and brand matching. Integrators may already get
the same composition from `verified-food-groups.csv`; only the literal source
description needs a separate field connection.

`portion-rejudgement.csv` records all ten foods exceeding their original one-meal
reference in sixteen of the previous fifty-two actual-plan rows. Eight exact
g-only foods use fixed measured references through `portion-evidence.csv`.
오곡밥/기장밥 retain their existing 210g bowl conversion and 0.5/1/1.5 bowl choices.
The official reference is not a health maximum; no blanket cap is introduced.
Earlier 어탕 150g and 멸치볶음 50g evidence stays in its existing sidecar.

Unaccepted references: a specific manufacturer's 기피편 product description is
not transferred to the generic dish. MFDS's 2014 holiday-food PDF has nutrition
and portion data but no ingredient declaration for 기피편; it cannot support a
new bean group by itself. General 연포탕 is not silently joined to a qualified
낙지연포탕 recipe without preserved identity support.
