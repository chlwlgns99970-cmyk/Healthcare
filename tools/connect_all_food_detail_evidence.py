"""Publish verified runtime display aliases and separate public recipe text, retaining official values."""
import csv,hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];AS=ROOT/'app/src/main/assets/fooddata';OUT=ROOT/'data-source/all-food-detail-audit'
def rows(p):
    with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
def write(p,data):
    with p.open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(data[0]));writer.writeheader();writer.writerows(data)
def run():
    names={r['foodId']:r for line in (OUT/'runtime-display-names.jsonl').read_text(encoding='utf-8-sig').splitlines() if line for r in [json.loads(line)]}
    assert len(names)==67356
    aliases=[];semanticAliases=[]
    seafoodNames={
        'kfind-d703-200000000-0001':'해물 스파게티','kfind-d503-200000000-0001':'해물 스파게티','kfind-d403-200000000-0001':'해물 스파게티',
        'kfind-d603-199500000-0001':'치즈 스파게티_해물','kfind-d503-199500000-0001':'치즈 스파게티_해물','kfind-d403-199500000-0001':'치즈 스파게티_해물',
        'kfind-d303-161505000-0001':'스파게티_해물_토마토소스','kfind-d303-161504800-0001':'스파게티_해물_크림소스'}
    for filename in ('food_items.csv','product_items.csv','franchise_official_items.csv'):
        foods=rows(OUT/('baseline-'+filename))
        for food in foods:
            display=names[food['id']];query=display['displayQuery']
            if query and query not in food['normalizedName'] and query not in food['aliases']:
                original=food['aliases'];food['aliases']=original.rstrip('|')+'|'+query+'|'
                aliases.append(dict(foodId=food['id'],originalName=food['name'],displayName=display['displayName'],normalizedAlias=query,sourceType=food['sourceType'],sourceFoodCode=food['sourceFoodCode'],source='FoodSearchPolicy.displayName from Samsung production-common runtime',checkedAt='2026-10-05'))
            if food['id'] in seafoodNames:
                assert food['name']==seafoodNames[food['id']] and food['sourceType']=='K-FIND' and not food['brand']
                alias='해산물스파게티'
                if alias not in food['aliases']:food['aliases']=food['aliases'].rstrip('|')+'|'+alias+'|'
                semanticAliases.append(dict(foodId=food['id'],originalName=food['name'],normalizedAlias=alias,
                    sourceType=food['sourceType'],sourceFoodCode=food['sourceFoodCode'],matchingMethod='REVIEWED_EXACT_SOURCE_TITLE_SEAFOOD_SYNONYM_NO_ID_MERGE',
                    sourceFile='baseline-'+filename,sourceFileSha256=hashlib.sha256((OUT/('baseline-'+filename)).read_bytes()).hexdigest(),checkedAt='2026-10-06'))
        write(AS/filename,foods)
    metadata=rows(OUT/'baseline-food_metadata.csv');mi={r['foodItemId']:r for r in metadata}
    fields=['referenceRecipeName','referenceIngredientText','referenceRecipeBasis','referenceRecipeUrl','referenceRecipeHash']
    for m in metadata:
        for key in fields:m.setdefault(key,'')
    publications=[]
    candidates=json.loads((OUT/'foodnuri/exact-reference-source-candidates.json').read_text(encoding='utf-8'))
    imageReviews=json.loads((OUT/'foodnuri/reviewed-image-transcriptions.json').read_text(encoding='utf-8'))
    for candidate in candidates:
        review=next((r for r in imageReviews if r['key']==candidate['key']),None)
        if review:
            assert hashlib.sha256((ROOT/review['imageFile']).read_bytes()).hexdigest()==review['imageSha256']
            candidate.update(status='CAPTURED_IMAGE',rawIngredientText=review['ingredientText'],rawBasis=review['basis'])
    for candidate in candidates:
        if candidate['status'] not in ('CAPTURED','CAPTURED_IMAGE'):continue
        assert hashlib.sha256((ROOT/candidate['rawFile']).read_bytes()).hexdigest()==candidate['sha256']
        assert '1인분' in candidate['rawIngredientText']
        for fid in candidate['foodIds']:
            m=mi[fid]
            assert not m['referenceIngredientText'], 'One whole public recipe per identity, never mixed'
            m.update(referenceRecipeName=candidate['title'],referenceIngredientText=candidate['rawIngredientText'],referenceRecipeBasis=candidate['rawBasis'],referenceRecipeUrl=candidate['url'],referenceRecipeHash=candidate['sha256'])
            publications.append(dict(foodId=fid,referenceName=candidate['title'],sourceInstitution='농식품정보누리',sourceUrl=candidate['url'],sourceHash=candidate['sha256'],rawIngredientText=candidate['rawIngredientText'],matchingMethod='EXACT_QUALIFIED_TITLE_OR_REVIEWED_TOMATO_SAUCE_SPAGHETTI',referenceComplete=False,ingredientNutritionEstimated=False,officialFoodNutritionChanged=False,checkedAt='2026-10-05'))
    write(AS/'food_metadata.csv',metadata)
    manifest=(OUT/'baseline-food_data_manifest.properties').read_text(encoding='utf-8')
    digest=lambda name:hashlib.sha256((AS/name).read_bytes()).hexdigest().upper()
    bundle=hashlib.sha256(b''.join((AS/name).read_bytes() for name in ('food_items.csv','product_items.csv','franchise_official_items.csv','food_metadata.csv','official_recipe_reference_estimates.csv'))).hexdigest().upper()[:12]
    for key,value in [('bundleId','FOOD-DETAIL-2026-10-05-'+bundle),('productAssetSha256',digest('product_items.csv')),('officialFranchiseAssetSha256',digest('franchise_official_items.csv')),('metadataAssetSha256',digest('food_metadata.csv'))]:
        manifest=re.sub(r'(?m)^'+key+r'=.*$',key+'='+value,manifest)
    (AS/'food_data_manifest.properties').write_text(manifest,encoding='utf-8')
    for name,data in [('new-verified-display-aliases.json',aliases),('new-verified-seafood-aliases.json',semanticAliases),('new-public-recipe-text-links.json',publications)]:
        (OUT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(verifiedDisplayAliases=len(aliases),publicRecipeTextFoodLinks=len(publications),newFood=0,officialNutritionChanges=0),ensure_ascii=False))
if __name__=='__main__':run()
