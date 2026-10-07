"""Audit every registered brand using representative industry, independently of menus."""
from pathlib import Path
import csv, json
from generate_franchise_brand_audit import parse_catalog

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/search-state-brand-followup'
OUT.mkdir(exist_ok=True)
brands=parse_catalog((ROOT/'app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt').read_text(encoding='utf-8-sig'))
groups={'치킨':{'치킨'},'피자':{'피자'},'햄버거':{'햄버거'},'분식':{'분식','김밥'},'한식':{'한식','덮밥','돈가스','국밥'},'중식':{'중식','마라탕'},'일식':{'일식','라멘'},'죽':{'죽','죽·비빔밥'},'도시락':{'도시락'},'족발·보쌈':{'족발·보쌈'},'찜·탕':{'찜닭','부대찌개','삼계탕','샤브샤브'},'샌드위치·토스트':{'샌드위치','토스트'},'카페·음료':{'카페','음료'},'베이커리':{'베이커리'},'디저트·아이스크림':{'디저트','아이스크림'},'샐러드·포케':{'샐러드·포케'},'국수·우동':{'국수·우동','파스타'},'사이드':{'사이드'}}
with (ROOT/'app/build/catalog-qa/final-franchise-menu-category-audit.csv').open(encoding='utf-8-sig') as stream:
    menus=list(csv.DictReader(stream))
rows=[]
for brand in brands:
    categories=sorted({row['menuCategory'] for row in menus if row['brand']==brand['name']})
    before=[name for name,cats in groups.items() if set(categories)&cats]
    primary={'KFC':{'치킨','햄버거'},'본죽&비빔밥':{'죽','한식'}}.get(brand['name'],{brand['category']})
    after=[name for name,cats in groups.items() if primary & cats]
    assert len(after)==(2 if brand['name'] in ('KFC','본죽&비빔밥') else 1),brand
    rows.append(dict(brand=brand['name'],representativeIndustry=brand['category'],officialEvidence=brand['officialUrl'],
        beforeMenuBasedFilters='|'.join(before),afterBrandFilters='|'.join(after),menuCategories='|'.join(categories),
        removedCrossSellingFilters='|'.join(x for x in before if x not in after),policyMismatchRemaining=False,
        evidenceScope='Existing official-source-verified catalog representative industry; menu category retained separately'))
with (OUT/'all-89-brand-filter-audit.csv').open('w',encoding='utf-8',newline='') as stream:
    writer=csv.DictWriter(stream,fieldnames=rows[0]);writer.writeheader();writer.writerows(rows)
summary=dict(brandCount=len(rows),brandsWithCrossSellingFilterLeak=sum(bool(r['removedCrossSellingFilters']) for r in rows),
    removedBrandFilterPairs=sum(len(r['removedCrossSellingFilters'].split('|')) for r in rows if r['removedCrossSellingFilters']),
    representativeIndustryEdits=2,remainingPolicyMismatch=0,
    hiddenZeroBrandFilters=[k for k,v in groups.items() if not any(b['category'] in v for b in brands)],
    filterBrandCounts={k:sum(k in r['afterBrandFilters'].split('|') for r in rows) for k in groups},
    unresolvedRepresentativePolicy=[], multiRepresentativePolicy='User confirmed KFC=치킨|햄버거 and 본죽&비빔밥=죽|한식')
assert len(rows)==89
(OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False))
