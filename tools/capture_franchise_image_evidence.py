"""Capture official linked image evidence for reviewed manual transcriptions.

Images are source material, never OCR guesses or generated menu data. The offline
importer checks each transcription against the immutable source image SHA-256.
"""
from pathlib import Path
import concurrent.futures, hashlib, json, urllib.request
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data-source/franchise/raw/menu-images"
DATE = "2026-10-04"

def capture(item):
    name,url=item
    with urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":"Mozilla/5.0"}),timeout=14) as response:
        data=response.read()
    (OUT / name).write_bytes(data)
    return dict(path=f"raw/menu-images/{name}",sourceUrl=url,checkedAt=DATE,sha256=hashlib.sha256(data).hexdigest().upper())

if __name__=="__main__":
    OUT.mkdir(parents=True,exist_ok=True)
    items=[(f"chaesundang-menu-{i:02}.jpg",f"https://www.chaesundang.co.kr/images/menu1_{i}.jpg")for i in range(1,29)]
    items.append(("subway-allergens-202608.png","https://www.subway.co.kr/images/menu/allergy_img.png?2026080101"))
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        rows=list(executor.map(capture,items))
    (OUT.parent / "image-evidence-manifest.json").write_text(json.dumps(sorted(rows,key=lambda row:row["path"]),ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(f"Captured {len(rows)} exact linked official images")
