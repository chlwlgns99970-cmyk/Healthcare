"""Shared read-only capture cache for the full residual/reference request."""
import json
import sys
import collect_recipe_amount_sources as collector

collector.OUT=collector.prior.ROOT/'data-source/recipe-full-reference'


def run(urls):
    target=collector.OUT/'source-captures.json'
    known=json.loads(target.read_text(encoding='utf-8')) if target.exists() else []
    indexed={r['url']:r for r in known}
    old=collector.prior.ROOT/'data-source/recipe-amount-priority/source-captures.json'
    for row in json.loads(old.read_text(encoding='utf-8')):
        if row['url'] in urls and row.get('rawFile'):
            indexed.setdefault(row['url'],row|dict(cacheReused=True))
    target.write_text(json.dumps(list(indexed.values()),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    collector.run(urls)


if __name__=='__main__':run(sys.argv[1:])
