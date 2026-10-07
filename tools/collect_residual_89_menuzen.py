"""Capture uncached, manually relevant menuzen candidates without publishing."""
import hashlib,json
from pathlib import Path
import collect_recipe_strategy_sources as capture

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-residual-89'

def run():
    capture.RAW=OUT/'raw'
    path=OUT/'new-menuzen-source-captures.json'
    records=json.loads(path.read_text(encoding='utf-8')) if path.exists() else []
    known={r['url'] for r in records}
    for code in ('D093012','D132016'):
        url='https://www.nics.go.kr/food/kfi/mgnNewmenumkFoodSelectNew/selectFoodDetail.json?fdCode='+code
        if url in known:continue
        row=capture.capture(url);records.append(row)
        path.write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        if row['status']=='CAPTURED':
            raw=ROOT/row['rawFile'];assert hashlib.sha256(raw.read_bytes()).hexdigest()==row['sha256']
            d=json.loads(raw.read_text(encoding='utf-8-sig'))
            print(json.dumps(dict(code=code,header=d.get('foodDetailHeader'),inputs=d.get('foodDetailList')),ensure_ascii=False),flush=True)
        else:print(json.dumps(row,ensure_ascii=False),flush=True)

if __name__=='__main__':run()
