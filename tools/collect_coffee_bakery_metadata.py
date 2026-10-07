"""Collect exact-brand official coffee/bakery menu evidence without changing nutrition.

Each HTTP request is cached once, including failures. Re-running generation uses the
same raw input. A current public menu name is not a guarantee that its recipe is the
same version as an older K-FIND row: that scope is retained in evidenceKind.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import re
import threading
from concurrent.futures import ThreadPoolExecutor
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "data-source/franchise/raw"
MANIFEST = RAW / "coffee-bakery-request-manifest.json"
OUTPUT = ROOT / "data-source/franchise/food-metadata-coffee-bakery.csv"
AUDIT = ROOT / "data-source/franchise/coffee-bakery-metadata-audit.json"
CHECKED_AT = "2026-10-04"
PARSER_VERSION = "coffee-bakery-2026-10-04-v2"
FIELDS = "foodItemId,sourceFoodCode,brand,name,ingredientText,ingredients,allergenText,allergens,ingredientStatus,allergenStatus,sourceUrl,checkedAt,sourceDate,parserVersion,evidenceKind,mayContainAllergens,crossContactText,staleCandidate,sourceHash".split(",")


def normalize(value: str) -> str:
    import unicodedata
    return re.sub(r"[^0-9a-z가-힣]", "", unicodedata.normalize("NFKC", value).lower())


def text(value: str) -> str:
    return " ".join(html.unescape(re.sub(r"<[^>]+>", " ", value or "")).split())


class Cache:
    def __init__(self) -> None:
        RAW.mkdir(parents=True, exist_ok=True)
        self.entries = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {}
        self.lock = threading.Lock()

    def save(self) -> None:
        MANIFEST.write_text(json.dumps(self.entries, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")

    def fetch(self, url: str, *, form: dict[str, str] | None = None, label: str = "source") -> bytes:
        method = "POST" if form is not None else "GET"
        body = urllib.parse.urlencode(sorted(form.items())).encode() if form is not None else None
        key = method + " " + url + (" " + body.decode() if body else "")
        if key in self.entries:
            entry = self.entries[key]
            if entry.get("error"):
                return b""
            return (RAW / entry["file"]).read_bytes()
        digest = hashlib.sha256(key.encode()).hexdigest()[:12]
        filename = f"coffee-bakery-{label}-{digest}.raw"
        request = urllib.request.Request(url, data=body, headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/133.0 Safari/537.36",
            "Accept": "application/json,text/html,*/*", "Referer": url,
        })
        entry = {"url": url, "method": method, "form": form or {}, "checkedAt": CHECKED_AT,
                 "parserVersion": PARSER_VERSION, "file": filename}
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                raw = response.read()
                entry.update(status=response.status, finalUrl=response.url,
                             contentType=response.headers.get("Content-Type", ""),
                             sourceDate=response.headers.get("Last-Modified", ""),
                             sourceHash=hashlib.sha256(raw).hexdigest(), byteCount=len(raw))
            (RAW / filename).write_bytes(raw)
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            entry["error"] = str(error)
            raw = b""
        with self.lock:
            self.entries[key] = entry
            self.save()
        return raw

    def cached(self, url: str) -> tuple[bytes, dict]:
        entry = self.entries.get("GET " + url, {})
        return ((RAW / entry["file"]).read_bytes() if entry and not entry.get("error") else b"", entry)


def decode(raw: bytes) -> str:
    try:
        return raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        return raw.decode("cp949", errors="replace")


def probe(cache: Cache) -> None:
    for label, url in [
        ("starbucks-drink-list", "https://www.starbucks.co.kr/menu/drink_list.do"),
        ("starbucks-food-list", "https://www.starbucks.co.kr/menu/food_list.do"),
        ("tlj-product-result", "https://www.tlj.co.kr/product/result.asp"),
    ]:
        source = decode(cache.fetch(url, label=label))
        print(label, "chars", len(source))
        print("scripts", re.findall(r'<script[^>]+src=[\"\']([^\"\']+)', source, re.I)[-12:])
        print("detail links", re.findall(r'(?:href|onclick)=[\"\']([^\"\']*(?:view|detail|Get|seq)[^\"\']*)', source, re.I)[:8])


def tlj_list(cache: Cache) -> list[dict]:
    raw, _ = cache.cached("https://www.tlj.co.kr/product/result.asp")
    output = []
    for block in re.findall(r'<li class="item_wrap">(.*?)</li>', decode(raw), re.S):
        name = re.search(r'<span class="name">(.*?)</span>', block, re.S)
        code = re.search(r"viewDetail\('([0-9]+)'\)", block)
        if name and code:
            output.append({"name": text(name[1]), "code": code[1]})
    return output


def categories(cache: Cache) -> list[tuple[str, str]]:
    result = []
    for kind in ("drink", "food"):
        raw, _ = cache.cached(f"https://www.starbucks.co.kr/menu/{kind}_list.do")
        source = decode(raw)
        start = source.index("function getCateCodeCng")
        end = source.index("function getCateEq", start)
        result.extend((kind, code) for code in re.findall(r'result\s*=\s*"(W[0-9]+)"', source[start:end]))
    return result


def collect_bulk(cache: Cache) -> None:
    jobs = [(f"starbucks-{kind}-category-{code}", f"https://www.starbucks.co.kr/upload/json/menu/{code}.js")
            for kind, code in categories(cache)]
    jobs += [(f"tlj-detail-{row['code']}", f"https://www.tlj.co.kr/product/detail.asp?gubun=result&prod_num={row['code']}")
             for row in tlj_list(cache)]
    # Each public URL is requested at most once. Failed requests are cached too.
    with ThreadPoolExecutor(max_workers=6) as executor:
        futures = [executor.submit(cache.fetch, url, label=label) for label, url in jobs]
        for index, future in enumerate(futures, 1):
            future.result()
            if index % 100 == 0 or index == len(futures):
                print("cached", index, "of", len(futures), flush=True)


def starbucks_list(cache: Cache) -> list[dict]:
    output = {}
    for kind, code in categories(cache):
        raw, _ = cache.cached(f"https://www.starbucks.co.kr/upload/json/menu/{code}.js")
        if not raw:
            continue
        for row in json.loads(decode(raw)).get("list", []):
            output[row["product_CD"]] = row | {"kind": kind}
    return [output[code] for code in sorted(output)]


def collect_starbucks_details(cache: Cache) -> None:
    jobs = [(f"starbucks-{row['kind']}-detail-{row['product_CD']}",
             f"https://www.starbucks.co.kr/menu/{row['kind']}_view.do?product_cd={row['product_CD']}")
            for row in starbucks_list(cache)]
    with ThreadPoolExecutor(max_workers=6) as executor:
        futures = [executor.submit(cache.fetch, url, label=label) for label, url in jobs]
        for index, future in enumerate(futures, 1):
            future.result()
            if index % 100 == 0 or index == len(futures):
                print("Starbucks detail cached", index, "of", len(futures), flush=True)


ALLERGEN_ALIASES = {
    "달걀": {"난류", "알류", "계란", "달걀"}, "우유": {"우유", "유제품"}, "밀": {"밀", "밀가루"},
    "대두": {"대두"}, "땅콩": {"땅콩"},
    "견과류": {"견과류", "견과", "호두", "잣", "아몬드", "캐슈넛", "피스타치오", "마카다미아"},
    "새우": {"새우"}, "게": {"게", "꽃게", "대게", "홍게"},
    "생선": {"생선", "고등어", "참치", "연어", "명태", "대구"},
    "조개류": {"조개류", "조개", "홍합", "굴", "전복"},
}

# Literal source descriptions only. Composite names stay intact; e.g. a stated
# 통밀빵/연유/화이트초코 never manufactures a 밀/우유 ingredient or allergen.
DESCRIPTION_INGREDIENTS = {
    "우유", "저지방 우유", "스팀 밀크", "밀크", "두유", "에스프레소", "콜드 브루", "원두", "커피",
    "녹차", "홍차", "말차", "황차", "캐모마일", "히비스커스", "스피어민트", "페퍼민트",
    "레몬머틀", "레몬 그라스", "레몬그라스", "레몬밤", "로즈힙", "라벤더", "베르가못",
    "사과", "바나나", "딸기", "망고", "파인애플", "파파야", "복숭아", "블루베리", "라즈베리",
    "크랜베리", "레몬", "자몽", "오렌지", "한라봉", "청귤", "유자", "키위", "포도", "건포도",
    "아몬드", "호두", "땅콩", "피스타치오", "헤이즐넛", "캐슈넛", "코코넛", "잣",
    "토마토", "방울토마토", "방울 토마토", "대추 방울 토마토", "오이", "양상추", "양배추",
    "루꼴라", "바질", "바질 페스토", "케일", "시금치", "아보카도", "단호박", "호박", "감자",
    "고구마", "옥수수", "양파", "마늘", "버섯", "당근", "쑥",
    "치킨", "닭가슴살", "닭고기", "베이컨", "햄", "소시지", "연어", "새우", "게살", "참치", "달걀", "계란",
    "통밀빵", "식빵", "치아바타", "호밀빵", "곡물빵", "콩", "팥", "흑임자", "깨", "찰떡", "찹쌀",
    "통밀", "밀가루", "쌀", "가루쌀", "현미", "귀리", "퀴노아",
    "연유", "버터", "생크림", "크림", "크림치즈", "휘핑크림", "마스카포네", "치즈",
    "모짜렐라 치즈", "체다 치즈", "요거트", "그릭 요거트",
    "초콜릿", "화이트초코", "다크 초콜릿", "꿀", "설탕", "메이플 시럽", "바닐라 빈", "바닐라",
    "카라멜", "캐러멜", "시나몬",
}
KOREAN_PARTICLES = r"(?:으로|에서|에게|처럼|부터|까지|을|를|이|가|와|과|로|에|의|는|은|도|만)"


def description_ingredients(description: str) -> set[str]:
    """Extract literal named components, never product names or implied subingredients."""
    found: list[tuple[int, int, str]] = []
    for term in sorted(DESCRIPTION_INGREDIENTS, key=lambda value: (-len(value), value)):
        pattern = r"(?<![가-힣A-Za-z0-9])" + re.escape(term) + r"(?=$|[^가-힣A-Za-z0-9]|" + KOREAN_PARTICLES + r"(?=$|[^가-힣A-Za-z0-9]))"
        for match in re.finditer(pattern, description):
            tail = description[match.end():]
            # Flavor descriptions are not a declaration that the named food was used.
            if re.match(r"\s*(?:향|맛|풍미)", tail) or re.match(KOREAN_PARTICLES + r"\s*(?:향|맛|풍미)", tail):
                continue
            if any(match.start() < end and match.end() > start for start, end, _ in found):
                continue
            found.append((match.start(), match.end(), term))
    return {term for _, _, term in found}


def allergen_declaration(label: str) -> tuple[str, str]:
    """A missing field never certifies absence. Preserve unsupported positive labels too."""
    label = text(label)
    if not label or label == "-":
        return "", "UNKNOWN"
    if normalize(label) in {"알레르기정보없음", "알레르기정보미제공", "정보없음", "정보미제공", "미확인"}:
        return "", "UNKNOWN"
    if normalize(label) in {"없음", "해당없음", "알레르기유발요인없음"}:
        return "", "CONFIRMED_LABEL"
    words = set(re.findall(r"[가-힣A-Za-z]+", label))
    canonical = {cause for cause, aliases in ALLERGEN_ALIASES.items() if words & aliases}
    supported_words = set().union(*ALLERGEN_ALIASES.values())
    extra = {word for word in words if word not in supported_words and word in {
        "돼지고기", "쇠고기", "소고기", "닭고기", "복숭아", "토마토", "아황산류", "메밀", "오징어"}}
    tags = canonical | extra
    # Unknown declaration tokens are retained and prevent a false complete
    # no-intersection result. Common declaration prose is not an allergen.
    unresolved = words - supported_words - extra - {
        "함유", "포함", "알레르기", "정보", "유발요인", "성분", "가금류", "가금류에", "한함"}
    if unresolved:
        tags |= unresolved
        return "|".join(sorted(tags)), "PARTIAL_UNRESOLVED_DECLARATION"
    return "|".join(sorted(tags)), "CONFIRMED_LABEL" if tags else "UNKNOWN"


def parse_tlj_detail(source: str, code: str) -> dict | None:
    name = re.search(r'<span class="name">(.*?)</span>', source, re.S)
    if not name:
        return None
    desc = re.search(r'<div class="p_desc2">(.*?)</div>', source, re.S)
    description = ""
    if desc:
        lines = re.findall(r'<li(?:\s[^>]*)?>(.*?)</li>', desc[1], re.S)
        description = text(" ".join(lines[1:]))
    allergy = re.search(r'<tr class="is-allergy">(.*?)</tr>', source, re.S)
    cell = re.search(r'<td[^>]*>(.*?)</td>', allergy[1], re.S) if allergy else None
    label = text(cell[1]) if cell else ""
    allergens, status = allergen_declaration(label)
    return {"officialName": text(name[1]), "officialCode": code, "ingredientText": description,
            "allergenText": label, "allergens": allergens, "allergenStatus": status,
            "brand": "뚜레쥬르", "temperature": "", "kind": "food", "sourceDate": ""}


def parse_starbucks_detail(source: str, code: str, kind: str) -> dict | None:
    match = re.search(r'view\s*:\s*remapView\(\s*', source)
    if not match:
        return None
    view, _ = json.JSONDecoder().raw_decode(source[match.end():])
    if str(view.get("PRODUCT_CD", "")) != code:
        raise ValueError("Starbucks detail product code differs from requested code")
    label = text(view.get("ALLERGY", "")).replace("@", ", ")
    allergens, status = allergen_declaration(label)
    return {"officialName": text(view.get("PRODUCT_NM", "")), "officialCode": code,
            "ingredientText": text(view.get("CONTENT", "")), "allergenText": label,
            "allergens": allergens, "allergenStatus": status, "brand": "스타벅스", "kind": kind,
            "temperature": "HOT" if view.get("HOT_YN") == "Y" else "ICED" if view.get("HOT_YN") == "N" else "",
            "sourceDate": ""}


def bundled_menu_identity(food: dict) -> tuple[str, str, str]:
    # K-FIND prefixes are generic source classifications, not part of the brand menu name.
    menu = food["name"].split("_", 1)[-1]
    variant = ""
    scope = "EXACT_BRAND_CANONICAL_MENU_NAME"
    if food["brand"] == "스타벅스":
        match = re.search(r'\s*(핫\(HOT\)|아이스\(ICED\))\s*\((Tall|Grande|Venti|Trenta)\)\s*$', menu, re.I)
        if match:
            variant = "ICED" if "ICED" in match[1].upper() else "HOT"
            menu = menu[:match.start()].strip()
            if variant == "ICED":
                menu = "아이스 " + menu
            scope += "|EXPLICIT_" + variant + "_VARIANT|ALLERGEN_DECLARATION_MENU_SCOPE_SIZE_" + match[2].upper()
        else:
            size = re.search(r'\s*\((Tall|Grande|Venti|Trenta)\)\s*$', menu, re.I)
            if size:
                menu = menu[:size.start()].strip()
                scope += "|ALLERGEN_DECLARATION_MENU_SCOPE_SIZE_" + size[1].upper()
    return normalize(menu), variant, scope


def official_records(cache: Cache) -> tuple[list[dict], list[dict]]:
    records, failures = [], []
    for item in tlj_list(cache):
        url = f"https://www.tlj.co.kr/product/detail.asp?gubun=result&prod_num={item['code']}"
        raw, entry = cache.cached(url)
        row = parse_tlj_detail(decode(raw), item["code"]) if raw else None
        if row:
            records.append(row | {"sourceUrl": url, "sourceHash": entry["sourceHash"]})
        else:
            failures.append({"url": url, "reason": entry.get("error") or "NO_PRODUCT_DETAIL"})
    for item in starbucks_list(cache):
        url = f"https://www.starbucks.co.kr/menu/{item['kind']}_view.do?product_cd={item['product_CD']}"
        raw, entry = cache.cached(url)
        row = parse_starbucks_detail(decode(raw), item["product_CD"], item["kind"]) if raw else None
        if row and row["officialName"]:
            records.append(row | {"sourceUrl": url, "sourceHash": entry["sourceHash"]})
        else:
            failures.append({"url": url, "reason": entry.get("error") or "NO_PRODUCT_DETAIL"})
    return records, failures


def generate_rows(foods: list[dict], records: list[dict]) -> tuple[list[dict], list[dict]]:
    index: dict[tuple[str, str], list[dict]] = {}
    for row in records:
        index.setdefault((row["brand"], normalize(row["officialName"])), []).append(row)
    output, unmatched = [], []
    for food in foods:
        if food.get("brand") not in {"스타벅스", "뚜레쥬르"}:
            continue
        name, variant, scope = bundled_menu_identity(food)
        candidates = index.get((food["brand"], name), [])
        if variant:
            candidates = [r for r in candidates if r["temperature"] == variant]
        if len(candidates) != 1:
            unmatched.append({"foodItemId": food["id"], "brand": food["brand"], "name": food["name"],
                              "reason": "AMBIGUOUS_CURRENT_MENU" if candidates else "NO_EXACT_CURRENT_MENU"})
            continue
        source = candidates[0]
        output.append(dict.fromkeys(FIELDS, "") | {
            "foodItemId": food["id"], "sourceFoodCode": food["sourceFoodCode"], "brand": food["brand"], "name": food["name"],
            "ingredientText": source["ingredientText"], "ingredientStatus": "PARTIAL_DESCRIPTION" if source["ingredientText"] else "UNKNOWN",
            "ingredients": "|".join(sorted(description_ingredients(source["ingredientText"]))),
            "allergenText": source["allergenText"], "allergens": source["allergens"], "allergenStatus": source["allergenStatus"],
            "sourceUrl": source["sourceUrl"], "checkedAt": CHECKED_AT, "sourceDate": source["sourceDate"], "parserVersion": PARSER_VERSION,
            "evidenceKind": scope + "|OFFICIAL_MENU_CODE:" + source["officialCode"] + "|CURRENT_OFFICIAL_MENU_RECIPE_VERSION_UNVERIFIED",
            "staleCandidate": "true", "sourceHash": source["sourceHash"],
        })
    return sorted(output, key=lambda row: row["foodItemId"]), sorted(unmatched, key=lambda row: row["foodItemId"])


def generate(cache: Cache) -> None:
    records, failures = official_records(cache)
    foods = []
    for path in sorted((ROOT / "app/src/main/assets/fooddata").glob("*.csv")):
        with path.open(encoding="utf-8-sig", newline="") as handle:
            foods.extend(row for row in csv.DictReader(handle) if row.get("id") and row.get("sourceFoodCode"))
    rows, unmatched = generate_rows(foods, records)
    with OUTPUT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    counts = {}
    for brand in ("스타벅스", "뚜레쥬르"):
        selected = [row for row in rows if row["brand"] == brand]
        counts[brand] = {"officialMenus": sum(r["brand"] == brand for r in records), "exactJoinedFoodItems": len(selected),
                         "confirmedAllergenLabels": sum(r["allergenStatus"] == "CONFIRMED_LABEL" for r in selected),
                         "partialAllergenDeclarations": sum(r["allergenStatus"].startswith("PARTIAL") for r in selected),
                         "unknownAllergenLabels": sum(r["allergenStatus"] == "UNKNOWN" for r in selected),
                         "partialIngredientDescriptions": sum(r["ingredientStatus"] == "PARTIAL_DESCRIPTION" for r in selected)}
        counts[brand]["partialNamedIngredientEvidence"] = sum(bool(r["ingredients"]) for r in selected)
    audit = {"checkedAt": CHECKED_AT, "parserVersion": PARSER_VERSION, "counts": counts, "failures": failures, "unmatched": unmatched,
             "sourceDatePolicy": "No publication/recipe date is invented. Fetch date is checkedAt; page template comments and product photo dates are not recipe dates.",
             "scope": "Exact brand and normalized current menu title only. Explicit Starbucks HOT/ICED is verified against official HOT_YN. Current menu declaration does not verify the older K-FIND nutrition recipe version.",
             "nutritionChanges": 0, "completeIngredientDeclarations": 0, "csvSha256": hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}
    audit["ingredientExtractionPolicy"] = "Literal official description dictionary with word/particle boundaries; longest named component wins. Flavor-only mentions and compound decomposition are excluded. Product names and allergen labels are never ingredient inputs."
    AUDIT.write_text(json.dumps(audit, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"counts": counts, "failures": len(failures), "csvSha256": audit["csvSha256"]}, ensure_ascii=False))


def verify_cache(cache: Cache) -> None:
    verified = 0
    errors = []
    for key, entry in sorted(cache.entries.items()):
        if entry.get("error"):
            errors.append({"request": key, "error": entry["error"]})
            continue
        raw = (RAW / entry["file"]).read_bytes()
        if hashlib.sha256(raw).hexdigest() != entry["sourceHash"] or len(raw) != entry["byteCount"]:
            raise ValueError("Cached official source hash/size mismatch: " + entry["file"])
        if entry["checkedAt"] != CHECKED_AT:
            raise ValueError("Unexpected cached retrieval date: " + entry["file"])
        url = urllib.parse.urlsplit(entry["url"])
        if url.scheme != "https" or url.hostname not in {"www.starbucks.co.kr", "www.tlj.co.kr"}:
            raise ValueError("Not an official HTTPS source: " + entry["url"])
        verified += 1
    print(json.dumps({"officialSourceHashesVerified": verified, "cachedRequestFailures": errors}, ensure_ascii=False))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--collect-bulk", action="store_true")
    parser.add_argument("--collect-starbucks-details", action="store_true")
    parser.add_argument("--generate", action="store_true", help="Offline deterministic generation from cached official raw files")
    parser.add_argument("--verify-cache", action="store_true", help="Offline source SHA-256, size, date and official-origin verification")
    args = parser.parse_args()
    cache = Cache()
    if args.probe:
        probe(cache)
    if args.collect_bulk:
        collect_bulk(cache)
    if args.collect_starbucks_details:
        collect_starbucks_details(cache)
    if args.generate:
        generate(cache)
    if args.verify_cache:
        verify_cache(cache)


if __name__ == "__main__":
    main()
