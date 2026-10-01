"""Offline evidence only. Runtime uses the same existing Room template metadata."""
from pathlib import Path
import csv, math, json

root = Path(__file__).resolve().parents[2]
assets = root / 'app/src/main/assets/fooddata'
def read(name):
    with (assets / name).open(encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f))
foods = {r['id']: r for r in read('food_items.csv')}
ingredients = read('meal_template_ingredients.csv')
rows = []
for t in read('meal_templates.csv'):
    linked = [i for i in ingredients if i['mealTemplateId'] == t['id']]
    r = dict(stableId=t['id'], menuName=t['name'])
    for meal in ('breakfast','lunch','dinner','snack'):
        r[meal+'Eligible'] = '|'+meal.upper()+'|' in t['supportedMealTypes']
    def value(column):
        vals = [float(foods[i['foodItemId']][column]) * float(i['amount']) / float(foods[i['foodItemId']]['referenceAmount'])
                if foods[i['foodItemId']][column] else None for i in linked]
        return sum(vals) if vals and all(v is not None for v in vals) else None
    r['kcal'] = math.floor(value('energyKcal') + 0.5)
    r['carbs'], r['protein'], r['fat'] = [value(c) for c in ('carbohydrateGrams','proteinGrams','fatGrams')]
    complete = all(r[c] is not None and math.isfinite(r[c]) and r[c] >= 0 for c in ('carbs','protein','fat'))
    r['macroCompleteness'] = 'COMPLETE' if complete else 'PARTIAL_OR_UNKNOWN'
    r['foodGroup'] = '|'.join(sorted({foods[i['foodItemId']]['category'] for i in linked}))
    r['ingredientCompleteness'] = 'COMPLETE' if '|INGREDIENTS_COMPLETE|' in t['tags'] else 'UNKNOWN_OR_PARTIAL'
    r['allergenCompleteness'] = 'UNKNOWN' if '|UNKNOWN|' in t['allergens'] else 'DECLARED'
    r['portion'] = '|'.join(f"{i['amount']}{i['unit']}" for i in linked)
    c,p,f = [(r[k] or 0)*m for k,m in (('carbs',4),('protein',4),('fat',9))]
    total = c+p+f
    r['balancedEligible'] = complete and total > 0 and c > 0 and p > 0 and f > 0 and .45 <= c/total <= .65 and .10 <= p/total <= .35 and .20 <= f/total <= .35
    r['healthyEligible'] = complete and total > 0 and .40 <= c/total <= .70 and .08 <= p/total <= .40 and .15 <= f/total <= .40
    for theme in ('diet','bulk','cheat'): r[theme+'Eligible'] = r['kcal'] > 0
    r['slowAgingStyleEligible'] = False
    r['exclusionReason'] = 'SLOW_STYLE_UNSUPPORTED: dish categories do not verify grain/bean/vegetable ingredients'
    rows.append(r)
for theme in ('light','hearty'):
    for r in rows: r[theme+'Eligible'] = False
    for meal in ('breakfast','lunch','dinner','snack'):
        pool = [r for r in rows if r[meal+'Eligible'] and r['kcal'] > 0]
        kcal = sorted(r['kcal'] for r in pool)
        if len(kcal)<2 or kcal[0]==kcal[-1]: continue
        bound = kcal[(len(kcal)-1)//3] if theme=='light' else kcal[(2*(len(kcal)-1)+2)//3]
        for r in pool:
            if (r['kcal']<=bound and r['kcal']<kcal[-1]) if theme=='light' else (r['kcal']>=bound and r['kcal']>kcal[0]):
                r[theme+'Eligible'] = True
assert len(rows)==len({r['stableId'] for r in rows})==292
columns = ['stableId','menuName','breakfastEligible','lunchEligible','dinnerEligible','snackEligible','kcal','carbs','protein','fat','macroCompleteness','foodGroup','lightEligible','balancedEligible','heartyEligible','dietEligible','bulkEligible','healthyEligible','cheatEligible','slowAgingStyleEligible','exclusionReason','ingredientCompleteness','allergenCompleteness','portion']
out = root/'data-source/recommendation/theme-eligibility-audit.csv'
with out.open('w',encoding='utf-8',newline='') as f:
    writer = csv.DictWriter(f,fieldnames=columns)
    writer.writeheader()
    writer.writerows(rows)
counts = {'rows':len(rows),'kcalComplete':sum(r['kcal']>0 for r in rows),'macroComplete':sum(r['macroCompleteness']=='COMPLETE' for r in rows),
          'eligible':{t:sum(r[t+'Eligible'] for r in rows) for t in ('light','balanced','hearty','diet','bulk','healthy','cheat','slowAgingStyle')},
          'meals':{m:sum(r[m+'Eligible'] for r in rows) for m in ('breakfast','lunch','dinner','snack')},
          'ingredientComplete':sum(r['ingredientCompleteness']=='COMPLETE' for r in rows),
          'allergenUnknown':sum(r['allergenCompleteness']=='UNKNOWN' for r in rows)}
(root/'app/build/daily-plan-qa/audit-counts.json').write_text(json.dumps(counts,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(counts,ensure_ascii=False))
