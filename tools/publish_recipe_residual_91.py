"""Publish reviewed same-dish reference, preserving all previously shipped rows."""
import csv,hashlib,json,math
from datetime import datetime,timezone
from pathlib import Path
from import_kfind_foods import FOOD_HEADERS,normalize_name,compact_number,token_field
from generate_food_metadata import base_metadata
from publish_recipe_residual_93_foods import append
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-residual-91';AS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def save(n,v):(OUT/n).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def run():
 original='MFDS-96';code='D051300';name='애호박새우젓국';alias='애호박젓국';fid='rda-menuzen-d051300'
 allfoods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)]
 captures=load(ROOT/'data-source/recipe-full-reference/source-captures.json')
 c=next(r for r in captures if r['url'].endswith('fdCode='+code));rawfile=ROOT/c['rawFile']
 assert hashlib.sha256(rawfile.read_bytes()).hexdigest()==c['sha256']
 raw=load(rawfile);h=raw['foodDetailHeader'];assert h['fdNm']==name
 ref=next(r for r in load(ROOT/'data-source/recipe-final-residual/validated-reference-compositions.json') if r['recipeId']=='MENUZEN-'+code)
 assert ref['complete'] and ref['sourceSha256']==c['sha256']
 assert all(i['amountGrams']==i['originalRecord']['foodWgh'] for i in ref['inputs'])
 assert math.isclose(sum(i['amountGrams'] for i in ref['inputs']),float(h['totalFoodWgh']))
 baseline=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(OUT/('baseline-'+n))]
 names={normalize_name(name),normalize_name(alias)}
 duplicates=[f['id'] for f in baseline if names & {normalize_name(f['name']),normalize_name(f['normalizedName']),*(normalize_name(a) for a in f['aliases'].split('|') if a)}]
 assert not duplicates
 reason='MFDS-96 원문 재료의 호박은 조리문에서 애호박으로 명시되고 새우젓 40g을 사용한 국. 메뉴젠 D051300은 동일 애호박·새우젓 국의 독립 참고 배합. 원본 된장·양파 배합을 메뉴젠의 두부·들기름·건새우 배합과 혼합하지 않음. 기관 고유 참고명 유지; 된장국·찜·새우 생살 국에 연결하지 않음.'
 timestamp=str(int(datetime(2026,10,6,tzinfo=timezone.utc).timestamp()*1000))
 f=dict.fromkeys(FOOD_HEADERS,'')|dict(id=fid,sourceType='RDA-MENUZEN',sourceFoodCode=code,name=name,normalizedName=normalize_name(name),aliases=token_field([alias]),category=h['lclasNm'],referenceAmount=compact_number(float(h['totalFoodWgh'])),unit='g',energyKcal=h['totalEnergy'],carbohydrateGrams=h['totalCarbohydrate'],proteinGrams=h['totalProtein'],fatGrams=h['totalFat'],servingDescription=f"농촌진흥청 메뉴젠 제시 구성 {h['totalFoodWgh']}g 기준 · 기관 제공 영양 (조리 후 중량 아님)",dataVersion='2026-10-06',createdAt=timestamp,updatedAt=timestamp)
 existing=[x for x in allfoods if x['id']==fid]
 if existing:assert existing==[f]
 else:
  m=base_metadata(f);m.update(sourceReference=c['url'],sourceName=ref['sourceInstitution'],sourceHash=c['sha256'],sourceVersion=code,sourceDate=c['checkedAt'],checkedAt='2026-10-06',rawClassification=h['lclasNm'],identityEvidence=reason)
  append(AS/'food_items.csv',[f]);append(AS/'food_metadata.csv',[m])
 fields=next(csv.reader((AS/'official_recipe_reference_estimates.csv').open(encoding='utf-8-sig')))
 text=', '.join(f"{i['ingredient']} {i['amountGrams']:g}g" for i in ref['inputs'])
 published=[]
 for i in ref['inputs']:
  n=i['nutrient'];published.append(dict.fromkeys(fields,'')|dict(foodId=fid,recipeId=ref['recipeId'],recipeName=name,recipeBasis=ref['basis'],ingredientText=text,ingredientName=i['ingredient'],amountGrams=f"{i['amountGrams']:.15g}",nutrientFoodId='official-reference-'+n['code'],nutrientName=n['name'],kcalPer100g=f"{n['energyKcal']:.15g}",recipeUrl=ref['sourceUrl'],nutrientUrl=n['sourceUrl'],recipeSha256=ref['sourceSha256'],checkedAt='2026-10-06',recipeComplete='true',foodReferenceKcal=f['energyKcal'],foodReferenceAmount=f['referenceAmount'],foodReferenceUnit=f['unit'],compositionKind='REFERENCE_RECIPE',sourceInstitution=ref['sourceInstitution']))
 prior=[r for r in rows(AS/'official_recipe_reference_estimates.csv') if r['foodId']==fid]
 if prior:assert prior==published
 else:append(AS/'official_recipe_reference_estimates.csv',published)
 states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json');s=next(r for r in states if r['recipeId']==original)
 s.update(state='REFERENCE_COMPLETE',referenceId=ref['recipeId'],referencePublished=True,referenceComplete=True,targetFoodIds=[fid],appCompleteAvailable=True)
 (ROOT/'data-source/recipe-final-residual/recipe-final-states.json').write_text(json.dumps(states,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 props=dict(line.split('=',1) for line in (AS/'food_data_manifest.properties').read_text(encoding='utf-8').splitlines() if '=' in line)
 props.update(foodCount=str(len(rows(AS/'food_items.csv'))),totalFoodCount=str(len(baseline)+1),metadataCount=str(len(rows(AS/'food_metadata.csv'))),metadataAssetSha256=hashlib.sha256((AS/'food_metadata.csv').read_bytes()).hexdigest().upper())
 material=b''.join((AS/n).read_bytes() for n in ('food_items.csv','product_items.csv','franchise_official_items.csv','food_metadata.csv'))
 props['bundleId']='FOOD-QUALITY-2026-10-06-'+hashlib.sha256(material).hexdigest()[:12].upper()
 (AS/'food_data_manifest.properties').write_text(''.join(k+'='+v+'\n' for k,v in props.items()),encoding='utf-8')
 evidenceInputs=[dict(i,calculatedKcal=i['amountGrams']*i['nutrient']['energyKcal']/100,unit='g',compositionKind='REFERENCE_RECIPE',matchingMethod='exact official Menuzen ingredient code and original foodWgh') for i in ref['inputs']]
 save('new-app-complete-foods.json',[dict(originalRecipeId=original,originalName=alias,originalComplete=False,name=name,foodId=fid,referenceId=ref['recipeId'],sourceUrl=c['url'],rawFile=c['rawFile'],sourceSha256=c['sha256'],reportedNutritionHeader=h,identityEvidence=reason,duplicateRowsScanned=len(baseline),duplicates=duplicates,inputs=evidenceInputs,publishedRows=len(published),officialNutritionFromInstitutionHeader=True,ingredientSumUsedAsOfficialNutrition=False,checkedAt='2026-10-06')])
 print(json.dumps(dict(newFood=fid,rows=len(published),appComplete=sum(r['appCompleteAvailable'] for r in states)),ensure_ascii=False))
if __name__=='__main__':run()
