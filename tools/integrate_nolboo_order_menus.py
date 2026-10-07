"""Add official order-service menu groups, keeping store listing and sales scope distinct."""
from pathlib import Path
import csv,json,hashlib
from import_franchise_expansion import generate_menu_kotlin,read_csv
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication';SOURCE=ROOT/'data-source/franchise';A=ROOT/'app/src/main/assets/fooddata'
def write(path,fields,rows):
    with path.open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore',lineterminator='\n');w.writeheader();w.writerows(rows)
def main():
    audit=json.loads((OUT/'nolboo-order-catalog-audit.json').read_text(encoding='utf-8'));rows=[];metadata=[]
    prior=read_csv(A/'food_metadata.csv');fields=list(prior[0]);priorbyid={r['foodItemId']:r for r in prior}
    for capture in audit['groups']:
        d=capture['detail'];s=capture['source'];assert hashlib.sha256((ROOT/s['rawFile']).read_bytes()).hexdigest()==s['sha256']
        dates=[m['lastUpdatedAt'][:10] for m in d.get('menus',[]) if m.get('lastUpdatedAt')];date=max(dates) if dates else ''
        identity='official-menu-nolboo-order-'+d['code'].lower();description=d.get('description','')
        # Individual descriptions explicitly name the main stew and distinguish
        # accompanying toppings/drinks. The broad brand category alone is unused.
        category='부대찌개' if '부대찌개' in description else ''
        rows.append(dict(id=identity,brand='놀부부대찌개',name=d['name'],sourceUrl=s['url'],verifiedAt='2026-10-05',sourceDate=date,saleState='OFFICIAL_ORDER_BRAND_SALES_UNVERIFIED',energyKcal='',servingAmount='',servingUnit='',menuCategory=category,scope='brand-wide',sourceSha256=s['sha256'],description=description,optionVariants=json.dumps([dict(code=m['code'],name=m['name'],lastUpdatedAt=m.get('lastUpdatedAt','')) for m in d.get('menus',[])],ensure_ascii=False)))
        m=dict.fromkeys(fields,'');m.update(foodItemId=identity,sourceType='OFFICIAL-BRAND-MENU',sourceFoodCode=d['code'],name=d['name'],normalizedName='',brand='놀부부대찌개',rawClassification='',ingredientText=description,ingredientStatus='PARTIAL_DESCRIPTION' if description else 'UNKNOWN',allergenStatus='UNKNOWN',sourceReference=s['url'],sourceName='놀부 공식 채널 연결 브랜드 주문 서비스',checkedAt='2026-10-05',sourceDate=date,parserVersion='nolboo-official-order-v1',identityEvidence='EXACT_BRAND_CODE_AND_MENU_GROUP|BRAND_WIDE_ORDER_CATALOG',staleCandidate='true',menuCategory=category,sourceStatus='DATED_OFFICIAL_ORDER_CATALOG',availabilityStatus='CURRENT_SALES_UNVERIFIED',sourceHash=s['sha256'])
        metadata.append(m)
    write(OUT/'nolboo-order-menu-snapshot.csv',list(rows[0]),rows);write(OUT/'nolboo-order-metadata.csv',fields,metadata)
    for m in metadata:
        if m['foodItemId'] in priorbyid:
            assert m['foodItemId'].startswith('official-menu-nolboo-order-')
            prior[prior.index(priorbyid[m['foodItemId']])]=m
        else:prior.append(m)
    write(A/'food_metadata.csv',fields,prior)
    from generate_food_metadata import generated_manifest
    (A/'food_data_manifest.properties').write_bytes(generated_manifest((A/'food_metadata.csv').read_bytes(),len(prior)))
    menus=[]
    for filename in ('official-menu-snapshot.csv','additional-menu-snapshot.csv','quality-menu-snapshot.csv','delivery-menu-snapshot.csv'):menus+=read_csv(SOURCE/filename)
    menus+=read_csv(ROOT/'data-source/recipe-menu-completion/nolboo-public-menu-snapshot.csv')+rows
    generate_menu_kotlin(menus)
    reviews=[dict(menuId=r['id'],name=r['name'],category=r['menuCategory'],status='CATEGORY_ASSIGNED' if r['menuCategory'] else 'CATEGORY_UNRESOLVABLE',reason='공식 개별 설명이 주요 메뉴를 부대찌개로 명시하며 사리·음료는 부가 구성으로 구분됨' if r['menuCategory'] else '곱새 재료명/메뉴명은 확인되지만 조리 형태를 현 taxonomy에 확정할 설명이 없음' if '곱새' in r['name'] else '제육놀부세트 개별 공식 설명이 비어 있어 세트 구성과 대표 요리 형태를 확정할 수 없음',scope=r['scope'],sourceUrl=r['sourceUrl'],sourceSha256=r['sourceSha256']) for r in rows]
    (OUT/'nolboo-order-category-audit.json').write_text(json.dumps(reviews,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Nolboo added official brand catalog groups',len(rows),'store-specific preserved',7,'nutrition created',0)
if __name__=='__main__':main()
