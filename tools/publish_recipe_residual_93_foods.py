"""Append approved same-dish Foods using institution-reported nutrition, not our sums."""
import csv,hashlib,json
from datetime import datetime,timezone
from pathlib import Path
from import_kfind_foods import FOOD_HEADERS,normalize_name,compact_number,token_field
from generate_food_metadata import base_metadata
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-residual-93';AS=ROOT/'app/src/main/assets/fooddata'
SPECS=[('MFDS-186','두릅산적','D093044','두릅적'),('MFDS-238','도라지양념구이','D083004','도라지구이(고추장)')]
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def rows(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def append(p,items):
 with p.open(encoding='utf-8-sig',newline='') as s:fields=next(csv.reader(s))
 with p.open('a',encoding='utf-8',newline='') as s:csv.DictWriter(s,fieldnames=fields,lineterminator='\n').writerows(items)
def run():
 allfoods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)]
 baseline=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(OUT/('baseline-'+n))]
 captures=load(ROOT/'data-source/recipe-full-reference/source-captures.json')
 new=[];metadata=[];evidence=[]
 for original,name,code,officialname in SPECS:
  c=next(r for r in captures if r['url'].endswith('fdCode='+code));p=ROOT/c['rawFile']
  assert hashlib.sha256(p.read_bytes()).hexdigest()==c['sha256'];raw=load(p);h=raw['foodDetailHeader'];assert h['fdNm']==officialname
  names={normalize_name(name),normalize_name(officialname)}
  # Canonical/normalized/whole alias checks are independently repeated on every run.
  duplicate=[f for f in baseline if names & {normalize_name(f['name']),normalize_name(f['normalizedName']),*(normalize_name(a) for a in f['aliases'].split('|') if a)}]
  assert not duplicate,duplicate
  source='RDA-MENUZEN';fid=source.lower()+'-'+code.lower()
  existing=[f for f in allfoods if f['id']==fid or (f['sourceType'],f['sourceFoodCode'])==(source,code)]
  timestamp=str(int(datetime(2026,10,5,tzinfo=timezone.utc).timestamp()*1000))
  row=dict.fromkeys(FOOD_HEADERS,'')|dict(id=fid,sourceType=source,sourceFoodCode=code,name=name,normalizedName=normalize_name(name),aliases=token_field([officialname]),category=h['lclasNm'],referenceAmount=compact_number(float(h['totalFoodWgh'])),unit='g',energyKcal=compact_number(float(h['totalEnergy'])),carbohydrateGrams=compact_number(float(h['totalCarbohydrate'])),proteinGrams=compact_number(float(h['totalProtein'])),fatGrams=compact_number(float(h['totalFat'])),servingDescription=f"농촌진흥청 메뉴젠 제시 구성 {h['totalFoodWgh']}g 기준 · 기관 제공 영양 (조리 후 중량 아님)",dataVersion='2026-10-05',createdAt=timestamp,updatedAt=timestamp)
  if existing:assert existing==[row]
  else:new.append(row)
  m=base_metadata(row);m.update(sourceReference=c['url'],sourceName='농촌진흥청 국립식량과학원 메뉴젠',sourceHash=c['sha256'],sourceVersion=code,sourceDate='2026-10-05',checkedAt='2026-10-05',rawClassification=h['lclasNm'],identityEvidence=original+' 동일 주재료·조리법 '+code)
  if not existing:metadata.append(m)
  evidence.append(dict(originalRecipeId=original,name=name,foodId=fid,foodCreated=not bool(existing),sourceType=source,sourceFoodCode=code,referenceId='MENUZEN-'+code,sourceUrl=c['url'],rawFile=c['rawFile'],sourceSha256=c['sha256'],reportedNutritionHeader=h,normalization='기관 totalEnergy/totalCarbohydrate/totalProtein/totalFat/totalFoodWgh 그대로. 자체 ingredient kcal 합계 사용 0',duplicateRowsScanned=len(baseline),sameExistingFoodCandidates=duplicate,nutritionUnknown=['sodiumMilligrams'],checkedAt='2026-10-05'))
 if new:append(AS/'food_items.csv',new);append(AS/'food_metadata.csv',metadata)
 assert len({f['id'] for f in allfoods+new})==len(allfoods+new)
 props={}
 for line in (AS/'food_data_manifest.properties').read_text(encoding='utf-8').splitlines():
  if '=' in line:k,v=line.split('=',1);props[k]=v
 props.update(foodCount=str(len(rows(AS/'food_items.csv'))),totalFoodCount=str(len(allfoods+new)),metadataCount=str(len(rows(AS/'food_metadata.csv'))),metadataAssetSha256=hashlib.sha256((AS/'food_metadata.csv').read_bytes()).hexdigest().upper())
 material=b''.join((AS/n).read_bytes() for n in ('food_items.csv','product_items.csv','franchise_official_items.csv','food_metadata.csv'))
 props['bundleId']='FOOD-QUALITY-2026-10-05-'+hashlib.sha256(material).hexdigest()[:12].upper()
 (AS/'food_data_manifest.properties').write_text(''.join(k+'='+v+'\n' for k,v in props.items()),encoding='utf-8')
 (OUT/'approved-new-food-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print(json.dumps(dict(newFoods=len(new),totalFoods=len(allfoods+new)),ensure_ascii=False))
if __name__=='__main__':run()
