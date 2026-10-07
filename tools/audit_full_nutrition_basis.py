"""Whole bundled catalog basis audit. Source-confirmed unusual units are immutable."""
from pathlib import Path
import csv,json,re,hashlib
from collections import Counter,defaultdict
import openpyxl
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication';A=ROOT/'app/src/main/assets/fooddata'
def read(p):
    with p.open(encoding='utf-8-sig') as f:return list(csv.DictReader(f))
def amount(s):
    m=re.fullmatch(r'\s*(\d+(?:\.\d+)?)\s*(g|ml)\s*',str(s or ''),re.I)
    return (float(m[1]),m[2].lower()) if m else None
def main():
    paths=[A/x for x in ('food_items.csv','product_items.csv','franchise_official_items.csv')]
    hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    foods=[r for p in paths for r in read(p)];meta={r['foodItemId']:r for r in read(A/'food_metadata.csv')}
    bycode=defaultdict(list)
    for r in foods:
        if r['sourceType'].startswith('K-FIND'):bycode[r['sourceFoodCode']].append(r)
    proofs={};sourceaudit=[];mismatches=[]
    for filename,expected in [('kfind-food-db-2026-08-28.xlsx','1EF3551F9A1D0EE87891D6306FA22BBD6A7FFCC90F2C70A4A184DBFE3FCE6EA6'),('kfind-processed-food-db-2026-08-28.xlsx','B074D98E75D2D087DC1B193F0056AFFBF9AFD14524C9CCB556CB2B20978504F7')]:
        path=ROOT/'data-source/kfind'/filename;sha=hashlib.sha256(path.read_bytes()).hexdigest();assert sha.upper()==expected
        wb=openpyxl.load_workbook(path,read_only=True,data_only=True);it=wb.active.iter_rows(values_only=True);headers=[str(x or '').strip() for x in next(it)];index={x:i for i,x in enumerate(headers)};seen=0;matched=0
        for rowno,values in enumerate(it,2):
            seen+=1;code=str(values[index['식품코드']] or '').strip()
            if code not in bycode:continue
            basis=str(values[index['영양성분함량기준량']] or '').strip();parsed=amount(basis)
            assert parsed is not None,(code,basis)
            raw={key:str(values[index[key]] or '').strip() for key in ('식품명','영양성분함량기준량','식품중량','섭취량 참고사항') if key in index}
            for f in bycode[code]:
                equal=(float(f['referenceAmount']),f['unit'].lower())==parsed
                if not equal:mismatches.append(dict(foodId=f['id'],assetBasis=f['referenceAmount']+f['unit'],original=raw))
                proofs[f['id']]=dict(sourceFile=str(path.relative_to(ROOT)),sourceHash=sha,sourceRow=rowno,original=raw,basisEqual=equal)
                matched+=1
        wb.close();sourceaudit.append(dict(file=filename,sha256=sha,sourceRows=seen,matchedAssetRows=matched))
        print(filename,seen,matched,flush=True)
    assert not mismatches,'Proven parser mismatches require correction before finalization'
    suspicious=[];groups=defaultdict(list)
    liquid=re.compile('음료|주스|우유|두유|유제품|발효유|액상|커피|차류|주류|술|식혜|라떼')
    moist=re.compile('국및탕|국 및 탕|찌개|스프|수프|죽|장류|소스|드레싱|기름|유지|시럽|액상|국수|국밥|전골|면류')
    for f in foods:
        m=meta.get(f['id'],{});raw=m.get('rawClassification',f['category']);unit=f['unit'].lower();tags=[]
        drink=bool(liquid.search(raw))
        if unit=='ml' and not drink and not moist.search(raw+' '+f['name']):tags.append('SOLID_WITH_VOLUME_BASIS')
        if unit=='g' and drink:tags.append('BEVERAGE_WITH_MASS_BASIS')
        if m.get('householdUnit') and m.get('basisUnit') and m['basisUnit'].lower()!=unit:tags.append('HOUSEHOLD_BASIS_DIMENSION_DIFFERS')
        size=amount(m.get('packageSize'))
        if size and size[1]==unit and float(f['referenceAmount'])>size[0]:tags.append('REFERENCE_LARGER_THAN_PACKAGE')
        if f.get('barcode'):groups['barcode:'+f['barcode']].append(f)
        if m.get('productReportNumber'):groups['report:'+m['productReportNumber']].append(f)
        if tags:suspicious.append(dict(foodId=f['id'],name=f['name'],brand=f['brand'],rawClassification=raw,basis=f['referenceAmount']+unit,packageSize=m.get('packageSize',''),householdUnit=m.get('householdUnit',''),householdBasisAmount=m.get('basisAmountPerUnit',''),householdBasisUnit=m.get('basisUnit',''),suspicions=tags,**proofs.get(f['id'],{}),sourceReference=m.get('sourceReference',''),status='SOURCE_CONFIRMED' if f['id'] in proofs else 'UNRESOLVED',reason='Exact food code and original workbook row confirm the stored basis; original remains unchanged.' if f['id'] in proofs else 'Original manufacturer basis cannot be independently verified from available captured payload; no safe correction.'))
    conflicts=[]
    for key,rows in groups.items():
        if len({(r['referenceAmount'],r['unit']) for r in rows})>1:
            conflicts.append(dict(identityKey=key,foodIds=[r['id'] for r in rows],bases=[r['referenceAmount']+r['unit'] for r in rows],status='UNRESOLVED',reason='Same product identifier has distinct source reference bases; these may be legitimate different portions. No values merged.'))
    result=dict(totalCatalogRows=len(foods),allCatalogRowsScanned=True,kfindRowsCompared=len(proofs),sourceWorkbooks=sourceaudit,suspiciousFound=len(suspicious)+len(conflicts),sourceConfirmed=sum(r['status']=='SOURCE_CONFIRMED' for r in suspicious),parserCorrected=0,unresolved=sum(r['status']=='UNRESOLVED' for r in suspicious)+len(conflicts),countsBySuspicion=dict(Counter(t for r in suspicious for t in r['suspicions'])),suspicious=suspicious,sourceConflicts=conflicts,parserMismatches=mismatches,assetHashesBefore=hashes,assetHashesAfter={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in paths})
    assert result['assetHashesBefore']==result['assetHashesAfter']
    (OUT/'nutrition-basis-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('suspicious','sourceConflicts')},ensure_ascii=False))
if __name__=='__main__':main()
