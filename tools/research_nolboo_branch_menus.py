"""Follow every public branch pagination page and its actual menu-list route."""
import json
from capture_full_adjudication_sources import run,ROOT,OUT

def main():
    sources=json.loads((OUT/'source-captures.json').read_text(encoding='utf-8'))
    branches=[];pages=[]
    for page in range(5):
        c=next(c for c in sources if c['url']==f'https://order.kakao.com/wapi/order/v1/brand/branches?brandCode=NOLBOO&page={page}')
        payload=json.loads((ROOT/c['rawFile']).read_text(encoding='utf-8'));assert payload['total_elements']==47
        branches+=payload['content'];pages.append(c)
    assert len({b['branchCode'] for b in branches})==47
    urls=[f"https://order.kakao.com/wapi/order/v1/brand/menu_groups?brandCode=NOLBOO&branchCode={b['branchCode']}" for b in branches]
    existing={c['url'] for c in sources if c.get('status')==200}
    run([url for url in urls if url not in existing])
    sources=json.loads((OUT/'source-captures.json').read_text(encoding='utf-8'));groups={};lists=[]
    for branch,url in zip(branches,urls):
        c=next(c for c in sources if c['url']==url);assert c.get('status')==200
        payload=json.loads((ROOT/c['rawFile']).read_text(encoding='utf-8'));assert isinstance(payload,list)
        wanted=[g for g in payload if g['category']['mainCode']=='0027']
        lists.append(dict(branch=branch,source=c,groupCodes=[g['code'] for g in wanted],allGroupCount=len(payload)))
        for g in wanted:
            entry=groups.setdefault(g['code'],dict(group=g,branches=[]))
            entry['branches'].append(branch['branchCode'])
    result=dict(branchCount=47,pages=pages,branchLists=lists,groups=list(groups.values()),fullBranchListsChecked=True,currentSalesVerified=False,reason='Public branch listings do not establish present order availability or a uniform menu at every store.')
    (OUT/'nolboo-branch-menu-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print('All branches',len(lists),'distinct Budae menu groups',len(groups))
    print([(g['group']['code'],g['group']['name'],len(g['branches'])) for g in groups.values()])
if __name__=='__main__':main()
