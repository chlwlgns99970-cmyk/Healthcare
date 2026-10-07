"""Finite additional official evidence for the preserved 292 recommendations.

Collect only the published records below. Failures are cached as audit entries;
offline replay never retries a failed URL or requires an authentication key.
No recipe nutrition, serving size or health claim is imported.
"""
from pathlib import Path
import argparse
import hashlib
import json
import urllib.parse
import urllib.request
from collect_public_recipe_evidence import ROOT, CHECKED

OUT = ROOT / 'data-source/catalog-recommendation'
RAW = ROOT / 'app/build/catalog-recommendation/raw'
VERSION = 'catalog-recommendation-official-sources-v1'
SOURCES = {
    'mfds-2014.pdf': 'https://www.foodsafetykorea.go.kr/upload/mkisna/2014.pdf',
    'rda-91474.html': 'https://www.nics.go.kr/food/kfi/tfSrch08/view?menuId=PS03520&tfcode=91474',
    'rda-90903.html': 'https://www.nics.go.kr/food/kfi/tfSrch08/view?menuId=PS03520&tfcode=90903',
    'rda-92100.html': 'https://www.nics.go.kr/food/kfi/tfSrch08/view?menuId=PS03520&tfcode=92100',
    'kto-181470.html': 'https://english.visitkorea.or.kr/svc/sp/food/ext/special_view.do?menuSn=913&vcontsId=181470',
    'kto-178283.html': 'https://english.visitkorea.or.kr/svc/sp/food/ext/special_view.do?menuSn=913&vcontsId=178283',
    'kto-179699.html': 'https://english.visitkorea.or.kr/svc/sp/food/ext/special_view.do?menuSn=913&vcontsId=179699',
    'kto-231600.html': 'https://english.visitkorea.or.kr/svc/sp/food/ext/special_view.do?menuSn=913&vcontsId=231600',
    'cj-vegi-wrap.html': 'https://cjnews.cj.net/' + urllib.parse.quote('뚜레쥬르-환경의-날-맞아-캠페인-열고-대체육-신') + '/',
}

def collect(online=False):
    RAW.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / 'source-request-audit.json'
    prior = {r['cacheFile']: r for r in json.loads(target.read_text(encoding='utf-8'))['requests']} if target.exists() else {}
    records = []
    for key, url in sorted(SOURCES.items()):
        path = RAW / key
        item = dict(cacheFile=key, sourceUrl=url, checkedAt=CHECKED, parserVersion=VERSION)
        if not path.exists():
            if key in prior and 'error' in prior[key]:
                records.append(prior[key]); continue
            if not online:
                raise FileNotFoundError(path)
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'}), timeout=25) as response:
                    path.write_bytes(response.read())
            except Exception as error:
                item['error'] = str(error)
                records.append(item); print(key, item['error'], flush=True); continue
        raw = path.read_bytes()
        item.update(sha256=hashlib.sha256(raw).hexdigest(), bytes=len(raw))
        records.append(item)
        print(key, len(raw), flush=True)
    report = dict(parserVersion=VERSION, checkedAt=CHECKED, finitePublishedRecords=len(SOURCES),
                  authenticationUsed=False, apiKeyUsed=False, nutritionImported=False,
                  requests=records)
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--collect', action='store_true')
    collect(parser.parse_args().collect)
