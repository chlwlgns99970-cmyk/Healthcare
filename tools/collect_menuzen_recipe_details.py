"""Read-only public recipe compositions; never edits a MenuGen diet/session."""
import concurrent.futures
import json
from pathlib import Path
import collect_recipe_strategy_sources as capture

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recipe-full-reference'
BASE = 'https://www.nics.go.kr/food/kfi/mgnNewmenumkFoodSelectNew/selectFoodDetail.json?fdCode='

def run():
    capture.RAW = OUT / 'raw'
    manifest = OUT / 'source-captures.json'
    known = {r['url']: r for r in json.loads(manifest.read_text(encoding='utf-8'))}
    recipes = json.loads((OUT / 'menuzen-matched-recipes.json').read_text(encoding='utf-8'))
    urls = [BASE + r['eumsikCode'] for r in recipes]
    pending = [u for u in urls if known.get(u, {}).get('status') != 'CAPTURED']
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for row in pool.map(capture.capture, pending):
            known[row['url']] = row
            manifest.write_text(json.dumps(list(known.values()), ensure_ascii=False, indent=2), encoding='utf-8')
            print(row['status'], row['url'], flush=True)
    print('Captured', sum(known[u]['status'] == 'CAPTURED' for u in urls), '/', len(urls))

if __name__ == '__main__':
    run()
