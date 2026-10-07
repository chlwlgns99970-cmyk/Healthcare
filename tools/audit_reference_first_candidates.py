"""Reference-first ranked candidates for every food in the 155-item request.

Scores propose reviews, never constitute authority to publish a foodId.
"""
import collections,csv,difflib,hashlib,json,re
from pathlib import Path
from maximize_recipe_evidence import context_for
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-reference-finalization';OUT.mkdir(exist_ok=True)
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def norm(s):
    for a,b in [('쇠고기','소고기'),('야채','채소'),('만둣국','만두국'),('마늘종','마늘쫑')]:s=s.replace(a,b)
    return re.sub(r'[\s_(),<>/·]','',s)
def method(s):
    s=re.split(r'[<(]',s)[0]
    for pattern,kind in [('비빔밥|볶음밥|밥$','밥'),('국$|탕$|육개장','국'),('찌개','찌개'),('볶음|불고기','볶음'),('튀김|까스|핫바','튀김'),('구이','구이'),('무침|생채|냉채|겉절이','무침'),('찜','찜'),('죽|스프|수프','죽/스프'),('샐러드','샐러드'),('주스|쥬스|쉐이크|스무디','음료'),('조림','조림'),('국수|수제비|칼국수','면'),('전$|오믈렛','전'),('샌드위치','샌드위치')]:
        if re.search(pattern,s):return kind
    return None
CORE_WORDS=['주꾸미','낙지','갑오징어','오징어','고등어','가자미','꽃게','아귀','바지락','백합','모시조개','미역','토란','우엉','풋고추','참나물','냉이','서리태','콩나물','두부','소고기','돼지고기','닭','감자','단호박','버섯','피망','김치','대구','연근','도라지','달래','쑥','멍게','전복','고사리','취나물','양상추','배추','봄동','수수','메밀','팥','매실','딸기','바나나','잣','도토리묵','송이']
def cores(s):return {w for w in CORE_WORDS if w in norm(s)}
def run():
    original=load(ROOT/'data-source/recipe-full-reference/recipe-final-audit.json')['audit']
    states={r['recipeId']:r for r in load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json')}
    before=load(OUT/'before-states.json') if (OUT/'before-states.json').exists() else list(states.values())
    previous=load(ROOT/'data-source/recipe-final-residual-followup/newly-reviewed-mappings.json')
    ids={r['recipeId'] for r in before if not r['appCompleteAvailable']}|{r['originalRecipeId'] for r in previous}
    assert len(ids)==155
    refs=load(ROOT/'data-source/recipe-final-residual/validated-reference-compositions.json')
    catalog=load(ROOT/'data-source/recipe-full-reference/raw/d043efeb8cadc2124156')['eumsikList']
    captured={r['recipeId'].removeprefix('MENUZEN-') for r in refs}
    foods=list(csv.DictReader((ROOT/'app/src/main/assets/fooddata/food_items.csv').open(encoding='utf-8-sig')))
    grouped=collections.defaultdict(list)
    for f in foods:
        if not f['brand'] and f['sourceType']=='K-FIND':grouped[f['name']].append(f)
    queue=[]
    for o in original:
        if o['recipeId'] not in ids:continue
        name=o['name'];s=states[o['recipeId']];raw=ROOT/'app/build/food-quality-qa/recipe-source'/(o['recipeId'].lower().replace('rda-diet-','nongsaro-diet-')+'.html')
        assert hashlib.sha256(raw.read_bytes()).hexdigest()==o['ingredients'][0]['sourceSha256']
        context=context_for(o,raw)
        leading=re.split(r'[<(]',name)[0]
        spacing=re.sub('('+'|'.join(sorted(CORE_WORDS+['초고추장','된장','고추장','들깨','콩나물','비빔밥','비빔국수','샐러드','볶음','무침','튀김','구이','스무디','주스','쥬스','쉐이크','수제비','칼국수','전병'],key=len,reverse=True))+')',r' \1 ',leading)
        regionalOrOldNames=[s.strip() for bracket in re.findall(r'\(([^)]+)\)',name) for s in bracket.split(',') if not re.search(r'방법|인분|\d',s)]
        variants=list(dict.fromkeys([name,norm(name),leading,re.sub(r'\s+',' ',spacing).strip(),name.replace('야채','채소'),name.replace('쇠고기','소고기'),name.replace('마늘쫑','마늘종'),name.replace('쉐이크','셰이크').replace('쥬스','주스').replace('스프','수프')]+regionalOrOldNames))
        def similarity(other):return max(difflib.SequenceMatcher(None,norm(v),norm(other)).ratio() for v in variants)
        ranked=[]
        for r in refs:
            accuracy=similarity(r['name'])
            if accuracy<.4:continue
            same=method(name)==method(r['name']) and method(name) is not None
            linked=sum(bool(i['nutrient']) for i in r['inputs'])
            sourceCore=cores(' '.join(i['ingredient'] for i in r['inputs']))
            targetCore=cores(name)
            primaryMatch=len(targetCore&sourceCore)/max(1,len(targetCore))
            public=r['sourceInstitution'].startswith(('농촌','질병','식품'))
            quantified=all(isinstance(i['amountGrams'],(int,float)) and i['amountGrams']>0 for i in r['inputs'])
            # Exactly the ten requested ordered criteria; names and ingredient
            # tokens are proposals, not proof of species/state/dish equivalence.
            score=[accuracy,int(same),primaryMatch,int(quantified),int(bool(r.get('basis'))),int(public),int(not public),int(bool(r['recipeId'])),int(bool(r['sourceUrl'] and r['sourceSha256'])),linked/max(1,len(r['inputs']))]
            ranked.append(dict(recipeId=r['recipeId'],name=r['name'],complete=r['complete'],compositionKind=r['compositionKind'],score=score,methodCompatible=same,primaryIngredientCoverage=primaryMatch,ingredients=[i['ingredient'] for i in r['inputs']],unresolvedIngredients=[i['ingredient'] for i in r['inputs'] if not i['nutrient']],sourceUrl=r['sourceUrl'],sourceSha256=r['sourceSha256']))
        ranked.sort(key=lambda r:r['score'],reverse=True)
        fresh=[dict(code=c['eumsikCode'],name=c['eumsikName'],nameAccuracy=similarity(c['eumsikName']),methodCompatible=method(name)==method(c['eumsikName'])) for c in catalog if c['eumsikCode'] not in captured and similarity(c['eumsikName'])>=.48]
        fresh.sort(key=lambda c:(c['nameAccuracy'],c['methodCompatible']),reverse=True)
        fn=sorted(grouped,key=lambda n:similarity(n),reverse=True)[:6]
        foodCandidates=[dict(name=n,score=similarity(n),categories=sorted({f['category'] for f in grouped[n]}),foodIds=[f['id'] for f in grouped[n]],bases=sorted({(f['referenceAmount'],f['unit']) for f in grouped[n]}),source='K-FIND',brand='') for n in fn]
        queue.append(dict(recipeId=o['recipeId'],name=name,originalComplete=o['complete'],appComplete=s['appCompleteAvailable'],originalIngredientText=o['ingredientText'],originalContext=context,originalSourceUrl=o['sourceUrl'],originalSourceSha256=o['ingredients'][0]['sourceSha256'],searchVariants=variants,queries=[v+' 레시피 재료 g 공공 공식' for v in variants],rankedExistingReferences=ranked[:8],uncapturedMenuzenCandidates=fresh[:4],foodCandidates=foodCandidates,identityAutomaticPublication=False))
    (OUT/'reference-first-work-queue.json').write_text(json.dumps(queue,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    priority=[q for q in queue if q['originalComplete']]
    (OUT/'unlinked-complete-nine.json').write_text(json.dumps(priority,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(total=len(queue),completeNoFood=len(priority),remaining=sum(not q['appComplete'] for q in queue),uncapturedCandidates=sum(len(q['uncapturedMenuzenCandidates']) for q in queue)),ensure_ascii=False))
if __name__=='__main__':run()
