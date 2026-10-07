"""New-route official documents; quantity retrieval never auto-publishes recipes."""
import json
from pathlib import Path
import collect_residual_89_documents as collector

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-residual-93'
queue=json.loads((OUT/'scope-87.json').read_text(encoding='utf-8'))
for row in queue:
    row['retrievalKeys']=row.get('retrievalKeys') or row['searchVariants']
(OUT/'scope-89.json').write_text(json.dumps(queue,ensure_ascii=False,indent=2),encoding='utf-8')
collector.OUT=OUT
collector.HOSTS += ('ice.go.kr/upload','nyc.kywa.or.kr/images','kdca.go.kr/bbs',
                    'kamis.or.kr/images','kocw.xcache.kinxcdn.com','loy.ac.kr/docdl',
                    'hanwooboard.or.kr/upload','old.diabetes.or.kr/new_workshop',
                    'www.korean.go.kr/common/download','u1.ac.kr/bakery')
collector.run()
