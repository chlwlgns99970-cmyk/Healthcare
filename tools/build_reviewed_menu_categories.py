"""Exact source-description reviews for ambiguous individual menu identities."""
from pathlib import Path
import csv,json
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-menu-completion'
REVIEWED={
 'official-quality-eggdrop-1cc56c59a082':('샌드위치','샌드위치'),
 'official-quality-eggdrop-5446bd8a5a01':('샌드위치','샌드위치'),
 'official-quality-eggdrop-8767c6076e82':('샌드위치','샌드위치'),
 'official-quality-eggdrop-92f88de75932':('샌드위치','샌드위치'),
 'official-quality-eggdrop-a67caa18d6a1':('샌드위치','샌드위치'),
 'official-quality-eggdrop-d01989c465ec':('샌드위치','샌드위치'),
 'official-quality-eggdrop-3e70869be4a6':('베이커리','식빵'),
 'official-bon-bf111-539834':('덮밥','덮밥'),
 'official-bon-bf111-539835':('덮밥','덮밥'),
 'official-bon-bf111-539771':('치킨','닭날개 튀김'),
 'official-bon-bf111-539820':('사이드','새우튀김'),
 'official-menu-delivery-6a19eb37c93182cc':('치킨','치킨부위'),
 'official-menu-delivery-8ae8aeb0595ad07f':('치킨','닭다리'),
}
def build():
    with (ROOT/'app/src/main/assets/fooddata/food_metadata.csv').open(encoding='utf-8-sig') as f:m={r['foodItemId']:r for r in csv.DictReader(f)}
    rows=[]
    for identity,(category,term) in REVIEWED.items():
        r=m[identity];assert term in r['ingredientText'] and 'http' in r['sourceReference']
        assert not any(t in r['name'] for t in ('세트','콤보','팩'))
        rows.append(dict(menuId=identity,name=r['name'],brand=r['brand'],category=category,
            evidence=r['ingredientText'],sourceUrl=r['sourceReference'],checkedAt=r['checkedAt'],sourceHash=r.get('sourceHash','')))
    final=ROOT/'data-source/full-adjudication/menu-final-audit.json'
    if final.exists():
        audit=json.loads(final.read_text(encoding='utf-8'))
        assert audit['unreviewed']==0 and len(audit['reviews'])==229
        for r in audit['reviews']:
            if r['status']=='CATEGORY_ASSIGNED':
                assert r['category'] and r['evidence'] and 'http' in r['sourceUrl']
                rows.append({k:r[k] for k in rows[0]})
    assert len(rows)==len({r['menuId'] for r in rows})
    with (OUT/'reviewed-menu-categories.csv').open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
    quote=lambda x:json.dumps(x,ensure_ascii=False)
    body='package com.example.healthcare.domain\n\n// Generated from exact official menu descriptions; no brand-category inference.\ninternal object ReviewedMenuCategorySources {\n    private val categories = mapOf(\n'
    body+=''.join('        '+quote(r['menuId'])+' to '+quote(r['category'])+',\n' for r in rows)
    body+='    )\n    fun categoryOf(id: String): String? = categories[id]\n}\n'
    (ROOT/'app/src/main/java/com/example/healthcare/domain/ReviewedMenuCategorySources.kt').write_text(body,encoding='utf-8')
    print('exact source description reviews',len(rows))
if __name__=='__main__':build()
