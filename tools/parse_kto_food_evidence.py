"""Read verified public KTO snapshots; only explicitly named synonyms are joined."""
from collect_public_recipe_evidence import *

def aliases(name):
    return {canonical(n) for n in [name]+re.split('[(),]',name) if n.strip()}

def index():
    templates=read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')
    foods={r['id']:r for r in read_csv(ROOT/'app/src/main/assets/fooddata/food_items.csv')}
    links={r['mealTemplateId']:r['foodItemId'] for r in read_csv(ROOT/'app/src/main/assets/fooddata/meal_template_ingredients.csv')}
    rows=[];matches=[]
    for page in range(1,11):
        obj=json.loads((CACHE/f'kto-index-{page}.json').read_text(encoding='utf-8'))
        assert obj['data']['pagination']['totalCount']==970
        for row in obj['data']['contents']:
            m=re.search(r'\(([가-힣].*?)\s*/',row['contsTtl'])
            if not m:continue
            rows.append(dict(sourceId=str(row['vcontsId']),name=m.group(1).strip(),title=row['contsTtl'],
                sourceDate=row['regDt'],sourceUrl='https://english.visitkorea.or.kr/svc/sp/food/ext/special_view.do?menuSn=913&vcontsId='+str(row['vcontsId'])))
    for row in rows:
        ts=[t for t in templates if not foods[links[t['id']]]['brand'] and aliases(t['name'])&aliases(row['name'])]
        if ts:matches.append(dict(row,templateIds=[t['id'] for t in ts]))
    (CACHE/'kto-detail-requests.json').write_text(json.dumps(matches,ensure_ascii=False,indent=2),encoding='utf-8')
    write_csv(OUT/'official-kto-food-index.csv',rows,list(rows[0]))
    return matches

def parse():
    output=[]
    for row in index():
        path=CACHE/('kto-food-'+row['sourceId']+'.html')
        if not path.exists():continue
        raw=path.read_bytes();text=raw.decode('utf-8-sig','replace')
        block=re.search(r'<div class="condetail">(.*?)</div>',text,re.S)
        if not block:continue
        output.append(dict(row,description=plain(block.group(1)),sourceSha256=hashlib.sha256(raw).hexdigest(),
                           checkedAt=CHECKED,parserVersion='kto-definition-v1'))
    (CACHE/'kto-food-parsed.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
    covered={r['stableTemplateId'] for r in read_csv(OUT/'verified-food-groups.csv')}
    for row in output:
        if not set(row['templateIds'])<=covered:print(row['sourceId'],row['name'],row['templateIds'],row['description'])

if __name__=='__main__':parse()
