"""Collect new public recipe books; reuse verified captures before networking."""
import hashlib
import json
import re
from pathlib import Path
import collect_recipe_strategy_sources as prior
from pypdf import PdfReader
from index_reference_finalization_documents import html_text

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recipe-residual-89'
HOSTS = ('daegufood.go.kr', 'lge.co.kr', 'mhc.cbe.go.kr', 'mpool.cbe.go.kr',
         'media3.bosch-home.com', 'lotteshopping.com', 'ydp.go.kr',
         'foodnuri.go.kr', 'foodsafetykorea.go.kr/upload', 'cuckoo.co.kr',
         'rda.go.kr/download_file', 'korea.kr/goNews/resources',
         'images.samsung.com', 'cuisinart.co.kr', 'archives.gyeongnam.go.kr',
         'pn.co.kr/recipe/recipe_detail', 'pn.co.kr/file/e_book',
         'tfs.ourhome.co.kr/menu/recommend/detail', 'sbook.allabout.co.kr/magazine/seoulmc',
         'goe.go.kr/resource/', 'noodleplanet.co.kr', 'semie.cooking/recipe-lab',
         'dspace.hansung.ac.kr/bitstream', 'text.cnu.ac.kr/IMG',
         'oldlib.jejunu.ac.kr/bitstream', 'samsunghospital.com/home/healthInfo',
         'ksn.or.kr/upload/general/ebook', 'easdl.or.kr/file/download',
         'kass.mafra.go.kr/newkass/cmm/fms/FileDown', 'opengov.seoul.go.kr/og/com/download',
         'atfis.or.kr/home/pdf/file', 'bomnae.chuncheon.go.kr/upload/pastfile',
         'council.goryeong.go.kr/record/appendixDownload', 'tongblog.sdm.go.kr/2459')


def run():
    queue = json.loads((OUT / 'scope-89.json').read_text(encoding='utf-8'))
    urls = set()
    for path in OUT.glob('*search-*.json'):
        result = json.loads(path.read_text(encoding='utf-8')).get('result','')
        urls.update(url for url in re.findall(r'\((https?://[^\s)]+)\)', str(result))
                    if any(host in url for host in HOSTS))
    manifest = OUT / 'new-document-index.json'
    existing = json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else []
    indexed = {r['url']: r for r in existing}
    verified_cache = {}
    for old_manifest in (ROOT / 'data-source').rglob('*source-captures.json'):
        try:
            entries = json.loads(old_manifest.read_text(encoding='utf-8-sig'))
        except (ValueError, OSError):
            continue
        if not isinstance(entries, list):
            continue
        for entry in entries:
            if not isinstance(entry, dict) or entry.get('status') != 'CAPTURED' or not entry.get('rawFile'):
                continue
            rawpath = ROOT / entry['rawFile']
            if rawpath.exists() and hashlib.sha256(rawpath.read_bytes()).hexdigest() == entry.get('sha256'):
                verified_cache[entry['url']] = entry
    prior.RAW = OUT / 'raw'
    prior.RAW.mkdir(exist_ok=True)
    for url in sorted(urls):
        if url in indexed:
            continue
        capture = (verified_cache[url] | dict(cacheReused=True)
                   if url in verified_cache else prior.capture(url))
        record = capture | dict(matches=[])
        if capture['status'] == 'CAPTURED':
            path = ROOT / capture['rawFile']
            raw = path.read_bytes()
            assert hashlib.sha256(raw).hexdigest() == capture['sha256']
            try:
                pages = ([p.extract_text() or '' for p in PdfReader(path).pages]
                         if raw.startswith(b'%PDF') else [html_text(raw)])
                textpath = OUT / (path.name + '.txt')
                textpath.write_text('\n\n'.join('PAGE '+str(n+1)+'\n'+s for n,s in enumerate(pages)), encoding='utf-8')
                record.update(pageCount=len(pages), textFile=textpath.relative_to(ROOT).as_posix())
                for food in queue:
                    hits = [n+1 for n,s in enumerate(pages) if any(
                        re.sub(r'[^\w가-힣]', '', key).replace('_','') in
                        re.sub(r'[^\w가-힣]', '', s).replace('_','')
                        for key in food['retrievalKeys'])]
                    if hits:
                        record['matches'].append(dict(recipeId=food['recipeId'], name=food['name'], pages=hits))
            except Exception as error:
                record['parseError'] = str(error)
        indexed[url] = record
        manifest.write_text(json.dumps(list(indexed.values()),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(dict(url=url,status=record['status'], matches=record['matches']),ensure_ascii=False),flush=True)


if __name__ == '__main__':
    run()
