"""Official-channel-linked public order catalog, preserving date/sales limitations."""
from pathlib import Path
import json
from capture_full_adjudication_sources import run,ROOT,OUT
def main():
    manifest=json.loads((OUT/'source-captures.json').read_text(encoding='utf-8'))
    source=next(c for c in manifest if '/brand/hot_menu_groups?' in c['url'])
    groups=json.loads((ROOT/source['rawFile']).read_text(encoding='utf-8'))
    hot=[g for g in groups if g['category']['mainCode']=='0027'];assert len(hot)==16
    branches=json.loads((OUT/'nolboo-branch-menu-audit.json').read_text(encoding='utf-8'))
    bycode={g['code']:g for g in hot}
    bycode.update({g['group']['code']:g['group'] for g in branches['groups']})
    wanted=list(bycode.values());assert len(wanted)==17
    urls=['https://order.kakao.com/wapi/order/v1/menu_group?orderType=DELIVERY&brandCode=NOLBOO&menuGroupCode='+g['code'] for g in wanted]
    already={c['url'] for c in manifest if c.get('status')==200}
    run([url for url in urls if url not in already])
    manifest=json.loads((OUT/'source-captures.json').read_text(encoding='utf-8'))
    details=[]
    for g,url in zip(wanted,urls):
        c=next(c for c in manifest if c['url']==url);assert c.get('status')==200
        payload=json.loads((ROOT/c['rawFile']).read_text(encoding='utf-8'));detail=payload['menuGroup'];assert detail['code']==g['code']
        details.append(dict(group=g,detail=detail,source=c))
    home=next(c for c in manifest if '/brand/home?' in c['url']);homepayload=json.loads((ROOT/home['rawFile']).read_text(encoding='utf-8'))
    result=dict(checkedAt='2026-10-05',scope='BRAND_ORDER_SERVICE_CATALOG',officialChannel='https://pf.kakao.com/_Nxaxmxad',officialChannelOrderLink='https://order.kakao.com/brands/NOLBOO',brandCode='NOLBOO',brandWideGroups=len(details),storeSpecificMenus=7,brandHomeBranches=len(homepayload['branches']),paginatedBranchCount=47,allBranchMenuListsChecked=True,currentSalesVerified=False,reason='Official channel links this brand order catalog. Initial home branch results are empty, but all 47 paginated branch requests expose the same brand-level catalog with 17 Budae groups. Old update dates and not-currently-orderable listings do not verify present sales or complete current homepage catalog.',groups=details,excludedOtherBrandGroups=[g for g in groups if g['category']['mainCode']!='0027'],fullCurrentHomepageCatalogVerified=False)
    (OUT/'nolboo-order-catalog-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Nolboo official group details',len(details),'option variants',sum(len(x['detail'].get('menus',[])) for x in details))
if __name__=='__main__':main()
