"""Verify the remaining manufacturer's original payloads without rewriting foods."""
from pathlib import Path
import json,csv,re,hashlib
from import_franchise_quality import text
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication'
def main():
    path=OUT/'nutrition-basis-audit.json';audit=json.loads(path.read_text(encoding='utf-8'))
    entries=json.loads((ROOT/'data-source/catalog-retail/binggrae-products.json').read_text(encoding='utf-8'))
    proofs={}
    for e in entries:
        c=e['nutritionSource'];raw=ROOT/c['path'];assert hashlib.sha256(raw.read_bytes()).hexdigest()==c['sourceHash']
        original=json.loads(raw.read_text(encoding='utf-8'));info=original.get('info',{})
        assert info==e['nutrition']['info']
        proofs['official-retail-binggrae-pack-'+str(e['volume']['PROD_VAL_IDX'])]=dict(originalBasis=str(info['TOT_CONT_AMT'])+info['TOT_CONT_UNIT'],sourceFile=c['path'],sourceHash=c['sourceHash'],sourceUrl=c['url'])
    pages=json.loads((ROOT/'data-source/franchise/raw/delivery-chain-pages.json').read_text(encoding='utf-8'))
    for r in audit['suspicious']:
        if r['status']!='UNRESOLVED':continue
        proof=proofs.get(r['foodId'])
        if proof:
            def parsed(s):
                m=re.fullmatch(r'(\d+(?:\.\d+)?)(g|ml)',s,re.I);return float(m[1]),m[2].lower()
            assert parsed(proof['originalBasis'])==parsed(r['basis'])
            r.update(proof,status='SOURCE_CONFIRMED',reason='Exact manufacturer variant ID and hash-verified original nutrition JSON confirm the stored volume basis. Ice cream volume basis remains unchanged.')
        elif r['brand']=='파리바게뜨':
            urls=r['sourceReference'].split('|');p=next(p for p in pages if p['sourceUrl'] in urls);plain=text(p['text'])
            m=re.search(r'총 내용량\s*:\s*(\d+(?:\.\d+)?)\s*(g|ml)',plain,re.I)
            assert m and float(m[1])==float(re.match(r'[\d.]+',r['basis'])[0]) and r['basis'].endswith(m[2])
            r.update(status='SOURCE_CONFIRMED',reason='Original individual manufacturer page explicitly gives total content in grams; beverage mass basis is legitimate and unchanged.',originalBasis=m[0],sourceHash=p['originalSha256'],sourceUrl=p['sourceUrl'])
    audit['sourceConfirmed']=sum(r['status']=='SOURCE_CONFIRMED' for r in audit['suspicious']);audit['unresolved']=sum(r['status']=='UNRESOLVED' for r in audit['suspicious'])+len(audit['sourceConflicts'])
    path.write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
    print('basis suspicious',audit['suspiciousFound'],'confirmed',audit['sourceConfirmed'],'unresolved',audit['unresolved'])
if __name__=='__main__':main()
