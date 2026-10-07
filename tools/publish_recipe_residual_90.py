"""Publish one whole institutional recipe and its separately reported serving nutrition."""
import csv,hashlib,json,io,re
from pathlib import Path
from openpyxl import load_workbook
from import_kfind_foods import FOOD_HEADERS,normalize_name
from generate_food_metadata import base_metadata
from publish_recipe_residual_93_foods import append
from relink_recipe_strategy import legacy_index
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-residual-90';AS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def save(n,v):(OUT/n).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def run():
 approval=OUT/'verified-quantity-unit.json'
 if not approval.exists():
  raise SystemExit('Publication withheld: official quantity-unit evidence has not been verified. No assets changed.')
 verified=load(approval)
 if verified.get('recipeId')!='MFDS-302' or verified.get('unit')!='g' or verified.get('verified') is not True:
  raise SystemExit('Publication withheld: incomplete recipe-specific quantity-unit evidence. No assets changed.')
 evidence=ROOT/verified['evidenceFile']
 if hashlib.sha256(evidence.read_bytes()).hexdigest()!=verified.get('evidenceSha256') or not verified.get('explicitUnitQuotation'):
  raise SystemExit('Publication withheld: quantity-unit evidence hash/quotation missing. No assets changed.')
 fid='public-recipe-goyang-19039-pumpkin-sandwich';rid='GOYANG-19039-PUMPKIN-SANDWICH';name='단호박샌드위치'
 c=next(x for x in load(OUT/'source-captures.json') if x['url'].endswith('fileNum=19039'))
 data=(ROOT/c['rawFile']).read_bytes();assert hashlib.sha256(data).hexdigest()==c['sha256']
 wb=load_workbook(io.BytesIO(data),data_only=True);rs=list(wb['2주'].values);start=next(i for i,r in enumerate(rs) if re.sub(r'\s+','',str(r[2]))==name)
 ingredients=[dict(ingredient=str(r[3]).strip(),amountGrams=float(r[5]),worksheetRow=start+i+1) for i,r in enumerate(rs[start:start+6])]
 assert [i['amountGrams'] for i in ingredients]==[30,15,5,5,1.5,.5]
 assert rs[start+6][2]=='우유' # separate meal beverage is not part of sandwich
 reported=next(r for r in wb['영양소 분석표'].values if r[2]==name)
 assert [float(v) for v in reported[3:7]]==[134.69,19,2.99,5.35]
 legacy=legacy_index();rda={n['code']:n for n in load(ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json')}
 ids=['MFDS-LEGACY-241','F1980040000a','F041000B090a','MFDS-LEGACY-4499','C0110020009a','MFDS-LEGACY-4515']
 reasons=['원문 빵, 식빵과 같은 식약처 일반 식빵 최신2017 단일 항목; 버터·우유 식빵 추정 없음','원문 호박, 단호박의 조리 전 투입량; 삶기 전 생것; 조리 손실 계산 없음','당근을 썰어 넣는 비가열 단계; 생것','원문 난황 마요네즈와 동일 식약처 일반 항목; 브랜드 추정 없음','명시 백설탕 동일 국가표준 항목','품목 특정 없는 소금의 식약처 일반 식용 항목; 정제염·천일염으로 바꾸지 않음']
 for i,nid,reason in zip(ingredients,ids,reasons):
  n=legacy[nid] if nid in legacy else rda[nid];i.update(nutrient=n,canonicalIngredient=n['name'],unit='g',conversion=None,matchingMethod=reason,calculatedKcal=i['amountGrams']*n['energyKcal']/100)
 baseline=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(OUT/('baseline-'+n))]
 duplicates=[f['id'] for f in baseline if normalize_name(name) in {normalize_name(f['name']),normalize_name(f['normalizedName']),*(normalize_name(a) for a in f['aliases'].split('|') if a)}]
 assert all(f['sourceType']=='K-FIND-PRODUCT' and f['brand'] for f in baseline if f['id'] in duplicates)
 productNamesakes=duplicates;duplicates=[]
 displayName=name+' (고양시 유아급식)'
 identity='MFDS-302의 찐 단호박을 으깨 식빵 사이에 바르는 샌드위치와 주재료·비가열 샌드위치 형태·제공 방식 동일. 고양시 배합의 당근/설탕/난황마요네즈를 독립 참고 배합으로 사용하며 원본 달걀·오이·양파·건포도·머스터드를 섞지 않음. 3~5세 유아 1인분 영양134.69kcal는 기관 영양표 값; 원본4인분의 공식 영양으로 주장하지 않음.'
 f=dict.fromkeys(FOOD_HEADERS,'')|dict(id=fid,sourceType='PUBLIC-RECIPE-NUTRITION',sourceFoodCode='GOYANG-19039-20260212',name=displayName,normalizedName=normalize_name(displayName),category='샌드위치류',referenceAmount='1',unit='인분',energyKcal='134.69',carbohydrateGrams='19',proteinGrams='2.99',fatGrams='5.35',sodiumMilligrams='346.46',servingDescription='고양시 2026년2월 유치원형 · 3~5세 유아 공식 1인분 영양표 기준 (성인 1인분/조리 후 g 환산 없음)',dataVersion='2026-10-06',createdAt='1791244800000',updatedAt='1791244800000')
 m=base_metadata(f);m.update(sourceReference=c['url'],sourceName=c['institution'],sourceHash=c['sha256'],sourceVersion='2026년2월 유치원형 XLSX',sourceDate='2026-02',checkedAt='2026-10-06',rawClassification='3~5세 유아 오후간식',identityEvidence=identity)
 current=rows(AS/'food_items.csv');existing=[x for x in current if x['id']==fid]
 if existing:assert existing==[f]
 else:append(AS/'food_items.csv',[f]);append(AS/'food_metadata.csv',[m])
 fields=next(csv.reader((AS/'official_recipe_reference_estimates.csv').open(encoding='utf-8-sig')))
 text=', '.join(f"{i['ingredient']} {i['amountGrams']:g}g" for i in ingredients)
 published=[]
 for i in ingredients:
  n=i['nutrient'];published.append(dict.fromkeys(fields,'')|dict(foodId=fid,recipeId=rid,recipeName=name,recipeBasis='고양시 유치원형3~5세 유아1인분 · 별도 공식 참고 구성',ingredientText=text,ingredientName=i['ingredient'],amountGrams=f"{i['amountGrams']:.15g}",nutrientFoodId='official-reference-'+n['code'],nutrientName=n['name'],kcalPer100g=f"{n['energyKcal']:.15g}",recipeUrl=c['url'],nutrientUrl=n['sourceUrl'],recipeSha256=c['sha256'],checkedAt='2026-10-06',recipeComplete='true',foodReferenceKcal=f['energyKcal'],foodReferenceAmount='1',foodReferenceUnit='인분',compositionKind='REFERENCE_RECIPE',sourceInstitution=c['institution']))
 prior=[x for x in rows(AS/'official_recipe_reference_estimates.csv') if x['foodId']==fid]
 if prior:assert prior==published
 else:append(AS/'official_recipe_reference_estimates.csv',published)
 states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json');s=next(r for r in states if r['recipeId']=='MFDS-302');s.update(state='REFERENCE_COMPLETE',referenceId=rid,referencePublished=True,referenceComplete=True,targetFoodIds=[fid],appCompleteAvailable=True)
 (ROOT/'data-source/recipe-final-residual/recipe-final-states.json').write_text(json.dumps(states,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 props=dict(line.split('=',1) for line in (AS/'food_data_manifest.properties').read_text(encoding='utf-8').splitlines() if '=' in line)
 props.update(foodCount=str(len(rows(AS/'food_items.csv'))),totalFoodCount=str(len(baseline)+1),metadataCount=str(len(rows(AS/'food_metadata.csv'))),metadataAssetSha256=hashlib.sha256((AS/'food_metadata.csv').read_bytes()).hexdigest().upper())
 material=b''.join((AS/n).read_bytes() for n in ('food_items.csv','product_items.csv','franchise_official_items.csv','food_metadata.csv'));props['bundleId']='FOOD-QUALITY-2026-10-06-'+hashlib.sha256(material).hexdigest()[:12].upper()
 (AS/'food_data_manifest.properties').write_text(''.join(k+'='+v+'\n' for k,v in props.items()),encoding='utf-8')
 save('new-app-complete-foods.json',[dict(originalRecipeId='MFDS-302',originalName=name,name=displayName,foodId=fid,referenceId=rid,sourceUrl=c['url'],rawFile=c['rawFile'],sourceSha256=c['sha256'],reportedNutritionHeader=dict(energyKcal=134.69,carbs=19,protein=2.99,fat=5.35,sodium=346.46,basisAmount=1,basisUnit='인분',population='3~5세 유아',worksheet='영양소 분석표',worksheetRow=81),identityEvidence=identity,duplicates=duplicates,brandedNamesakesExcluded=[f for f in baseline if f['id'] in productNamesakes],inputs=ingredients,publishedRows=6,officialNutritionFromInstitutionHeader=True,ingredientSumUsedAsOfficialNutrition=False,checkedAt='2026-10-06')])
 print(json.dumps(dict(newFood=fid,rows=6,complete=sum(r['appCompleteAvailable'] for r in states)),ensure_ascii=False))
if __name__=='__main__':run()
