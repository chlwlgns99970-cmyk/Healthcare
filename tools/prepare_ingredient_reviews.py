from pathlib import Path
import csv,json,re,collections
from lxml import html
from recipe_ingredient_parser import parse
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication'
recipes={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv') for r in csv.DictReader(p.open(encoding='utf-8-sig'))}
doc=html.fromstring((OUT/'raw/261a710c67dd5302').read_bytes().decode('utf-8'))
codes=doc.xpath('//input[@name="foodCode"]/@value');names=doc.xpath('//input[@name="fdNm"]/@value')
assert len(codes)==len(names)==3366
catalog=[dict(code=c,name=n) for c,n in zip(codes,names)]
(OUT/'rda-10.4-nutrition-identities.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding='utf-8')
rows=[dict(recipeId=r['recipeId'],recipeName=r['name'],sourceUrl=r['sourceUrl'],**i) for r in recipes.values() for i in parse(', '.join(filter(None,[r['mainIngredientText'],r['additionalIngredientText']])))]
(OUT/'ingredient-spans.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
counts=collections.Counter(i['ingredient'] for i in rows)
output=[]
for term,count in counts.most_common():
    key=re.sub(r'\s+','',term)
    search=re.sub(r'^(?:다진|채썬|통)','',key)
    candidates=[n for n in catalog if search and search in re.sub(r'\s+','',n['name'])]
    output.append(dict(ingredient=term,count=count,candidates=candidates))
(OUT/'identity-review-input.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
print('recipes',len(recipes),'spans',len(rows),'terms',len(counts))
for row in output:print(row['count'],row['ingredient'],'::',' | '.join(c['code']+' '+c['name'] for c in row['candidates'][:8]))
