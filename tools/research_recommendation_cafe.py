"""Focused, cached public official menu checks for recommendation cafe foods.

No name-based ingredient or allergen inference. Current menu recipes remain
unverified against the older K-FIND nutrition record.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from collect_coffee_bakery_metadata import (FIELDS, allergen_declaration,
                                          decode, description_ingredients,
                                          normalize, text)

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "app/build/cafe-targeted/raw"
MANIFEST = RAW / "manifest.json"
CHECKED_AT = "2026-10-04"
PARSER_VERSION = "recommendation-cafe-official-v1"
BRANDS = {"할리스", "커피빈", "빽다방", "파스쿠찌", "드롭탑", "바나프레소", "요거프레소", "이디야", "투썸플레이스"}
PROBES = {
    "할리스": ["https://m.hollys.co.kr/menu/menuView.do?idx=1016&menuDiv=BAKERY", "https://m.hollys.co.kr/menu/menuView.do?idx=665&menuDiv=BAKERY"],
    "커피빈": ["https://www.coffeebeankorea.com/menu/list.asp"],
    "빽다방": ["https://paikdabang.com/menu/menu_coffee/"],
    "파스쿠찌": ["https://www.caffe-pascucci.co.kr/"],
    "드롭탑": ["https://cafedroptop.com/menu"],
    "바나프레소": ["https://banapresso.com/menu_introduction"],
    "요거프레소": ["https://www.yogerpresso.co.kr/"],
    "이디야": ["https://www.ediya.com/contents/drink.html"],
    "투썸플레이스": ["https://www.twosome.co.kr/"],
}
COFFEE_FOOD_URL = "https://www.coffeebeankorea.com/menu/list.asp?category=6&category2=1&page=2"
PASC_DRINK_URL = "https://www.pascucci.co.kr/product/productList.asp?typeCode=00200050"
PASC_DELI_URL = "https://www.pascucci.co.kr/product/productList.asp?typeCode=00300020"
YOGER_MENU_URL = "https://www.yogerpresso.co.kr/menu/menu.html"
EDIYA_WATERMELON_URL = "https://www.ediya.com/contents/drink.html?chked_val=&skeyword=%EC%88%98%EB%B0%95"
EDIYA_TOMATO_URL = "https://www.ediya.com/contents/drink.html?chked_val=&skeyword=%ED%86%A0%EB%A7%88%ED%86%A0"
DROPTOP_MENU_BUNDLE_URL = "https://framerusercontent.com/sites/5KMzyG5oTfEZmk88F9XGqj/script_main.D2qh1k4t.mjs"
DROPTOP_MENU_ROUTE_URL = "https://framerusercontent.com/sites/5KMzyG5oTfEZmk88F9XGqj/hQniTwZnY0lCHLLfTlYe6KPde2cS03YjmBQdJg0IRJM.Btz2hdml.mjs"
DROPTOP_PUBLIC_CMS_URL = "https://framerusercontent.com/cms/YSX9yaNnA18VeLvXFrgY/IW5KSUQ7rKUwQRy1fafE/u9HClkFtf-chunk-default-0.framercms"
# Published by the unauthenticated official menu-introduction loader (gn).
# The fixed query selector identifies this read-only operation; no credential,
# upload token, account, store-order operation or private endpoint is used.
BANA_PUBLIC_MENU_URL = "https://banapresso.com/query"
BANA_PUBLIC_MENU_BODY = {"ws": "fprocess", "query": "EJKRUNO8U1KRSL4X4SSO", "params": {"nFCode": 200000}}
BANA_PUBLIC_MENU_KEY = BANA_PUBLIC_MENU_URL + " POST_JSON " + json.dumps(BANA_PUBLIC_MENU_BODY, separators=(",", ":"), ensure_ascii=False)

# Reviewed literal composite components from each accepted source description.
# Their constituents are never inferred (e.g. 에그샐러드 does not create 달걀).
LITERAL_COMPONENTS = {
    "닭가슴살 프레시 샌드위치": {"닭가슴살", "피클", "토마토"},
    "그릴드 치킨 샐러드": {"그릴드 치킨", "오리엔탈소스"},
    "햄 치즈 프렌치 토스트": {"브리오슈 프렌치 토스트", "에그샐러드", "치즈", "햄"},
    "그릭치킨샌드위치": {"감자 브레드", "훈제치킨", "로스트 파프리카", "양파", "모짜렐라치즈", "그릭소스"},
}


def parse_hollys(source):
    name = re.search(r"<h3>(.*?)<span>", source, re.S)
    desc = re.search(r'<p class="menu_txt">(.*?)</p>', source, re.S)
    allergy = re.search(r'<p class="allergy_item">.*?<span>(.*?)</span>', source, re.S)
    if not name or not desc:
        return None
    return {"officialName": text(name[1]), "ingredientText": text(desc[1]),
            "allergenText": text(allergy[1]) if allergy else ""}


def parse_coffee_foods(source):
    result = []
    for match in re.finditer(r'<span class="kor">(.*?)</span>.*?<dd>(.*?)</dd>', source, re.S):
        content = match[2]
        parts = re.split(r"<br\s*/?>", content, flags=re.I)
        allergy = next((p.split(":", 1)[-1] for p in parts if "알레르기" in p), "")
        result.append({"officialName": text(match[1]), "ingredientText": text(parts[0]), "allergenText": text(allergy)})
    return result


def parse_paik_foods(source):
    result = []
    titles = list(re.finditer(r'<p class="menu_tit">(.*?)</p>', source, re.S))
    for index, match in enumerate(titles):
        block = source[match.end():titles[index + 1].start() if index + 1 < len(titles) else len(source)]
        desc = re.search(r'<p class="txt">(.*?)</p>', block, re.S)
        label = re.search(r'<p class="menu_ingredient_basis">.*?알레르기 유발 성분\s*:\s*(.*?)</p>', block, re.S)
        prior = source[max(0, match.start() - 350):match.start()]
        image = re.findall(r'<img src="([^"]+)"', prior)
        result.append({"officialName": text(match[1]), "ingredientText": text(desc[1]).split("고카페인", 1)[0].strip() if desc else "",
                       "allergenText": text(label[1]) if label else "", "imageUrl": image[-1] if image else ""})
    return result


def exact_match(records, name):
    found = [r for r in records if normalize(r["officialName"]) == normalize(name)]
    return found[0] if len(found) == 1 else None


def parse_droptop_bakery_titles(raw):
    """Read literal category/title string fields in the linked Framer CMS chunk.

    The route schema declares j9pkOv3XB and J9DD3zPjc as String fields.
    This only records current titles; it creates no recipe or allergen evidence.
    """
    marker = (b"\x00\x00\x00\x09j9pkOv3XB\x0c\x00\x00\x00\x0c" + "베이커리".encode()
              + b"\x00\x00\x00\x09J9DD3zPjc\x0c")
    titles = []
    for match in re.finditer(re.escape(marker), raw):
        position = match.end()
        length = int.from_bytes(raw[position:position + 4], "big")
        value = raw[position + 4:position + 4 + length]
        assert len(value) == length and 0 < length < 400, "Invalid public CMS title field"
        titles.append(value.decode("utf-8"))
    return titles


def positive_records(cache):
    records = {}
    for url in PROBES["할리스"]:
        row = parse_hollys(cache.source(url))
        if row:
            row.update(brand="할리스", sourceUrl=url, requestKey=url, identityScope="EXACT_BRAND_NORMALIZED_MENU_TITLE")
            records[(row["brand"], normalize(row["officialName"]))] = row
    for row in parse_coffee_foods(cache.source(COFFEE_FOOD_URL)):
        row.update(brand="커피빈", sourceUrl=COFFEE_FOOD_URL, requestKey=COFFEE_FOOD_URL, identityScope="EXACT_BRAND_NORMALIZED_MENU_TITLE")
        records[(row["brand"], normalize(row["officialName"]))] = row
    # One audited identity exception only, independently verified by the exact
    # historical product image, explicit ICED title, and unique current record.
    paiks = parse_paik_foods(cache.source(PROBES["빽다방"][0]))
    found = exact_match(paiks, "카페라떼(ICED)")
    if found and found["imageUrl"].endswith("/빽s-라떼ICED-450x588.png"):
        row = found | {"brand": "빽다방", "sourceUrl": PROBES["빽다방"][0], "requestKey": PROBES["빽다방"][0],
                       "identityScope": "REVIEWED_SINGLE_PRODUCT_OFFICIAL_IMAGE_ALIAS|CURRENT_TITLE:카페라떼(ICED)|IMAGE:빽s-라떼ICED-450x588.png|EXPLICIT_ICED_UNIQUE_BASE_MENU"}
        records[("빽다방", normalize("빽’s 카페 라떼 아이스(ICED)"))] = row
    return records


def checked_requests(brand, food):
    if brand == "커피빈":
        return PROBES[brand] + [COFFEE_FOOD_URL]
    if brand == "파스쿠찌":
        return PROBES[brand] + ["https://www.pascucci.co.kr/", "https://www.pascucci.co.kr/index.asp",
                              PASC_DRINK_URL, PASC_DELI_URL, "https://www.pascucci.co.kr/lib/js/ui.js",
                              "https://www.pascucci.co.kr/product/ajax/productDetail.asp POST productSeq=2561"]
    if brand == "이디야":
        return PROBES[brand] + [EDIYA_WATERMELON_URL if "수박" in food["name"] else EDIYA_TOMATO_URL]
    if brand == "요거프레소":
        return PROBES[brand] + [YOGER_MENU_URL]
    if brand == "바나프레소":
        return PROBES[brand] + ["https://banapresso.com/static/js/main.9786b19e.js", "https://order.banapresso.com/", "https://order.banapresso.com/assets/main.DkLAebYf.js", BANA_PUBLIC_MENU_KEY]
    if brand == "드롭탑":
        return PROBES[brand] + [DROPTOP_MENU_BUNDLE_URL, DROPTOP_MENU_ROUTE_URL, DROPTOP_PUBLIC_CMS_URL]
    return PROBES[brand]


def unresolved_reason(food):
    return {
        "투썸플레이스": "OFFICIAL_HTTP_403_AND_EXACT_PUBLIC_OFFICIAL_DOMAIN_SEARCH_NO_RESULT",
        "드롭탑": "CURRENT_OFFICIAL_BAKERY_TAB_PUBLIC_CMS_HAS_NO_EXACT_TARGET;NO_RENAMED_ALIAS_ACCEPTED;EXACT_OFFICIAL_DOMAIN_SEARCH_NO_RESULT",
        "바나프레소": "PUBLIC_OFFICIAL_MENU_AND_ORDER_APP_SHELL_CHECKED;PUBLISHED_UNAUTHENTICATED_MENU_LOADER_SINGLE_ATTEMPT_TIMED_OUT;NO_TARGET_PRODUCT_RECORD_RETURNED;NO_AUTHENTICATED_OR_KEY_REQUIRED_API_USED;EXACT_OFFICIAL_DOMAIN_SEARCH_NO_RESULT",
        "요거프레소": "CURRENT_OFFICIAL_ALL_MENU_NO_EXACT_TARGET;SIMILAR_NEW_TITLE_OR_SIZE_NOT_ACCEPTED;EXACT_OFFICIAL_DOMAIN_SEARCH_NO_RESULT",
        "이디야": "OFFICIAL_CURRENT_KEYWORD_SEARCH_NO_EXACT_TARGET;SEASONAL_OR_RENAMED_RECIPE_IDENTITY_UNVERIFIED",
        "파스쿠찌": "CURRENT_OFFICIAL_DELI_CATEGORY_NO_EXACT_TARGET;EXACT_OFFICIAL_DOMAIN_SEARCH_NO_RESULT" if "시저" in food["name"] else "EXACT_BASE_NAME_PRODUCT_2561_FOUND_WITH_DISTINCT_ICED_PRODUCT_2562;SOURCE_DOES_NOT_EXPLICITLY_VERIFY_HOT;DESCRIPTION_HAS_FLAVOR_AND_UNSPECIFIED_PULP;ALLERGEN_DASH_IS_UNKNOWN",
    }.get(food["brand"], "NO_EXACT_OFFICIAL_MENU_IDENTITY")


def unresolved_observation(cache, food):
    if food["brand"] == "파스쿠찌":
        url = PASC_DELI_URL if "시저" in food["name"] else PASC_DRINK_URL
        names = [text(x) for x in re.findall(r"<h2>(.*?)</h2>", cache.source(url), re.S)]
        return {"observedCurrentCategoryTitles": names, "exactTargetTitleFound": False,
                "temperatureCheck": "Base Grapefruit Juice product 2561 and separate Iced Grapefruit Juice product 2562 observed; no literal HOT statement in product 2561 detail" if "자몽" in food["name"] else "not applicable",
                "candidateDescription": "새콤달콤 자몽향과 맛으로 과육이 씹히는 자몽 주스" if "자몽" in food["name"] else "",
                "candidateAllergenCell": "-" if "자몽" in food["name"] else ""}
    if food["brand"] == "요거프레소":
        names = [text(x) for x in re.findall(r'<p class="text">(.*?)</p>', cache.source(YOGER_MENU_URL), re.S)]
        wanted = food["name"].split("_", 1)[-1]
        assert normalize(wanted) not in {normalize(x) for x in names}
        tokens = {"딸기", "바나나", "망고", "블루베리", "자몽"}
        return {"currentAllMenuTitleCount": len(names), "exactTargetTitleFound": False,
                "similarTitlesNotAccepted": [x for x in names if any(t in x and t in wanted for t in tokens)]}
    if food["brand"] == "이디야":
        url = EDIYA_WATERMELON_URL if "수박" in food["name"] else EDIYA_TOMATO_URL
        source = cache.source(url)
        source = source[source.find('id="menu_ul"'):]
        names = [text(x).split(" ", 1)[-1] for x in re.findall(r"<h2[^>]*>(.*?)</h2>", source, re.S) if "B2B" not in x]
        return {"officialKeyword": "수박" if "수박" in food["name"] else "토마토", "similarTitlesNotAccepted": names,
                "exactTargetTitleFound": False}
    if food["brand"] == "드롭탑":
        raw = (ROOT / cache.entries[DROPTOP_PUBLIC_CMS_URL]["file"]).read_bytes()
        titles = parse_droptop_bakery_titles(raw)
        assert normalize(food["name"].split("_", 1)[-1]) not in {normalize(title) for title in titles}
        return {"captureScope": "Current official menu landing, its linked Framer main and /menu route scripts, and the public CMS chunk used by the Bakery tab.",
                "currentBakeryTitleCount": len(titles), "observedCurrentBakeryTitles": titles, "exactTargetTitleFound": False,
                "titleField": "J9DD3zPjc", "categoryField": "j9pkOv3XB", "newPublicUrlCountInFinalCheck": 3,
                "publicExactDomainSearchResultCount": 0}
    if food["brand"] == "바나프레소":
        return {"captureScope": "Current official menu introduction and linked ordering app HTML/script; one read-only public menu-loader request exactly as published by the menu-introduction script.",
                "publicMenuLoaderDiscovered": True, "loaderSourceUrl": "https://banapresso.com/static/js/main.9786b19e.js",
                "publicMenuLoaderOutcome": cache.entries[BANA_PUBLIC_MENU_KEY].get("error", ""),
                "menuRequestAttempts": 1, "newPublicUrlCountInFinalCheck": 1, "exactTargetTitleVerified": False,
                "publicExactDomainSearchResultCount": 0, "authenticatedOrKeyRequiredApiUsed": False}
    if food["brand"] == "투썸플레이스":
        return {"officialHttpStatus": 403, "publicExactDomainSearchResultCount": 0, "accessControlBypassAttempted": False}
    return {}


def generate(cache):
    research_path = ROOT / "data-source/recommendation/recommendation-ingredient-research.csv"
    with research_path.open(encoding="utf-8-sig", newline="") as handle:
        targets = {r["foodItemId"] for r in csv.DictReader(handle) if r["brand"] in BRANDS}
    with (ROOT / "app/src/main/assets/fooddata/food_items.csv").open(encoding="utf-8-sig", newline="") as handle:
        foods = [r for r in csv.DictReader(handle) if r["id"] in targets]
    assert len(foods) == len(targets), "Recommendation target must resolve to an actual FoodItem"
    records = positive_records(cache)
    evidence, audit = [], []
    for food in sorted(foods, key=lambda r: r["id"]):
        menu = food["name"].split("_", 1)[-1]
        source = records.get((food["brand"], normalize(menu)))
        requests = checked_requests(food["brand"], food)
        checked = [{"requestKey": key, **cache.entries.get(key, {"url": key, "error": "MISSING_CACHE"})} for key in requests]
        check = {"foodItemId": food["id"], "sourceFoodCode": food["sourceFoodCode"], "brand": food["brand"], "name": food["name"],
                 "checkedAt": CHECKED_AT, "parserVersion": PARSER_VERSION, "checkedSources": checked}
        row = dict.fromkeys(FIELDS, "") | {"foodItemId": food["id"], "sourceFoodCode": food["sourceFoodCode"], "brand": food["brand"], "name": food["name"],
                                             "checkedAt": CHECKED_AT, "parserVersion": PARSER_VERSION, "staleCandidate": "true"}
        if source:
            allergens, status = allergen_declaration(source["allergenText"])
            named = set(description_ingredients(source["ingredientText"]))
            reviewed = LITERAL_COMPONENTS.get(source["officialName"], set())
            assert all(x in source["ingredientText"] for x in reviewed)
            # Long composite components replace overlaps in the generic literal dictionary.
            named = {x for x in named if not any(x != y and x in y for y in reviewed)} | reviewed
            entry = cache.entries[source["requestKey"]]
            row.update(ingredientText=source["ingredientText"], ingredients="|".join(sorted(named)), ingredientStatus="PARTIAL_DESCRIPTION",
                       allergenText=source["allergenText"], allergens=allergens, allergenStatus=status, sourceUrl=source["sourceUrl"], sourceHash=entry["sourceHash"],
                       evidenceKind=source["identityScope"] + "|CURRENT_OFFICIAL_MENU_RECIPE_VERSION_UNVERIFIED")
            check.update(outcome="ACCEPTED_OFFICIAL_CURRENT_MENU", identityMatch=source["identityScope"], officialName=source["officialName"],
                         description=source["ingredientText"], allergenLabel=source["allergenText"], acceptedIngredients=row["ingredients"],
                         acceptedAllergens=allergens, officialImageUrl=source.get("imageUrl", ""))
        else:
            accepted_url = next((r["url"] for r in reversed(checked) if not r.get("error") and not r["url"].endswith(".js")), checked[-1]["url"])
            entry = next((r for r in reversed(checked) if r["url"] == accepted_url), {})
            reason = unresolved_reason(food)
            row.update(ingredientStatus="UNKNOWN", allergenStatus="UNKNOWN", sourceUrl=accepted_url, sourceHash=entry.get("sourceHash", ""),
                       evidenceKind="UNRESOLVED_AFTER_BRAND_SPECIFIC_PUBLIC_SOURCE_CHECK|" + reason)
            check.update(outcome="UNRESOLVED_AFTER_BRAND_SPECIFIC_PUBLIC_SOURCE_CHECK", reason=reason,
                         observedSourceOutcome=unresolved_observation(cache, food))
        evidence.append(row)
        audit.append(check)
    output = ROOT / "data-source/food-quality/cafe-targeted-evidence.csv"
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader(); writer.writerows(evidence)
    investigation = {"checkedAt": CHECKED_AT, "parserVersion": PARSER_VERSION,
                     "scope": "18 recommendation-linked cafe FoodItems; exact actual brand and normalized menu title after generic K-FIND underscore classification removal; one explicitly reviewed Paik image alias only.",
                     "sourceDatePolicy": "All accepted menu recipe publication dates unavailable. HTTP Last-Modified in checkedSources is document metadata, never assigned as a recipe sourceDate. checkedAt is retrieval date.",
                     "ingredientPolicy": "Only literal components stated in official product descriptions. Descriptions are partial; product titles, flavor mentions, allergen labels and composite subingredients never create ingredient claims.",
                     "allergenPolicy": "Ten canonical categories plus unsupported declared tokens retained. Empty/dash labels are UNKNOWN. No allergen inference from ingredients or menu names. Cross-contact is separate and empty when not declared.",
                     "publicExactSearches": {"커피빈": ["그릭치킨 햄 치즈 (official domain)", "ham toast official source discovery"], "파스쿠찌": ["그릴드치킨 시저 자몽 (old and current official domain)"],
                                            "바나프레소": ["게살듬뿍모닝 (official domain)"], "드롭탑": ["고르곤졸라 불고기 파니니 (official domain)"],
                                            "요거프레소": ["마시는 요거트 딸기 망고 블루베리 자몽 (official domain)"], "투썸플레이스": ["과카몰리치킨 (official domain)"]},
                     "counts": {"targetFoodItems": len(evidence), "acceptedOfficialCurrentMenus": sum(r["ingredientStatus"] == "PARTIAL_DESCRIPTION" for r in evidence),
                                "confirmedAllergenLabels": sum(r["allergenStatus"] == "CONFIRMED_LABEL" for r in evidence), "unresolvedFoodItems": sum(r["ingredientStatus"] == "UNKNOWN" for r in evidence),
                                "completeIngredientDeclarations": 0, "nutritionChanges": 0, "uniqueCachedRequests": len(cache.entries)},
                     "foods": audit, "csvSha256": hashlib.sha256(output.read_bytes()).hexdigest()}
    (ROOT / "data-source/food-quality/cafe-targeted-investigation.json").write_text(json.dumps(investigation, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(investigation["counts"], ensure_ascii=False))


def verify(cache):
    for key, entry in cache.entries.items():
        if entry.get("error"):
            continue
        raw = (ROOT / entry["file"]).read_bytes()
        assert len(raw) == entry["byteCount"] and hashlib.sha256(raw).hexdigest() == entry["sourceHash"], key
    # Positive exact-name / full declaration and negative unrelated-product checks.
    row = parse_hollys(cache.source(PROBES["할리스"][0]))
    assert row and row["officialName"] == "닭가슴살 프레시 샌드위치"
    parsed_allergens, status = allergen_declaration(row["allergenText"])
    assert set(parsed_allergens.split("|")) == {"닭고기", "달걀", "대두", "밀", "우유", "토마토"} and status == "CONFIRMED_LABEL"
    coffee = parse_coffee_foods(cache.source(COFFEE_FOOD_URL))
    assert exact_match(coffee, "그릭치킨 샌드위치")
    assert exact_match(coffee, "그릴드치킨 시저 샐러드") is None
    assert "달걀" not in description_ingredients("에그샐러드를 넣은 토스트")
    print("Verified cached hashes and focused positive/negative parser checks")


def verify_final_public_checks(cache):
    for key in (DROPTOP_MENU_BUNDLE_URL, DROPTOP_MENU_ROUTE_URL, DROPTOP_PUBLIC_CMS_URL, BANA_PUBLIC_MENU_KEY):
        entry = cache.entries[key]
        if entry.get("error"):
            assert key == BANA_PUBLIC_MENU_KEY and "timed out" in entry["error"]
            continue
        raw = (ROOT / entry["file"]).read_bytes()
        assert len(raw) == entry["byteCount"] and hashlib.sha256(raw).hexdigest() == entry["sourceHash"], key
    titles = parse_droptop_bakery_titles((ROOT / cache.entries[DROPTOP_PUBLIC_CMS_URL]["file"]).read_bytes())
    assert "잉글리쉬 머핀 샌드위치" in titles  # Positive current bakery title.
    assert normalize("고르곤졸라불고기파니니") not in {normalize(title) for title in titles}  # Exact target absent.
    print("Verified final public-cache hashes and positive/negative Bakery title checks")


class Cache:
    def __init__(self):
        RAW.mkdir(parents=True, exist_ok=True)
        self.entries = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {}

    def fetch(self, url, form=None, json_body=None):
        assert not (form and json_body), "One request body only"
        payload = json.dumps(json_body, separators=(",", ":"), ensure_ascii=False) if json_body else ""
        key = url + (" POST_JSON " + payload if json_body else " POST " + urllib.parse.urlencode(form) if form else "")
        # A blocked socket is not a remote HTTP request; an approved invocation
        # may make the first actual request while retaining the local blockage.
        blocked = self.entries.get(key, {}).get("error", "")
        if key in self.entries and "WinError 10013" not in blocked:
            return self.entries[key]
        filename = hashlib.sha256(key.encode()).hexdigest()[:20] + ".raw"
        row = {"url": url, "checkedAt": CHECKED_AT, "parserVersion": PARSER_VERSION, "file": str((RAW / filename).relative_to(ROOT))}
        if form:
            row.update(method="POST", form=form)
        if json_body:
            row.update(method="POST", publicMenuRequest=json_body, authenticationUsed=False)
        if blocked:
            row["priorSandboxSocketBlock"] = blocked
        headers = {"User-Agent": "Mozilla/5.0", "Accept": "text/html,application/json,*/*"}
        if json_body:
            # Match the public browser loader's Content-Type and JSON body.
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        req = urllib.request.Request(url, data=payload.encode() if json_body else urllib.parse.urlencode(form).encode() if form else None,
                                     headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                raw = response.read()
                row.update(status=response.status, finalUrl=response.url, sourceDate=response.headers.get("Last-Modified", ""),
                           contentType=response.headers.get("Content-Type", ""), sourceHash=hashlib.sha256(raw).hexdigest(), byteCount=len(raw))
            (RAW / filename).write_bytes(raw)
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            row["error"] = str(error)
        self.entries[key] = row
        return row

    def save(self):
        MANIFEST.write_text(json.dumps(self.entries, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    def source(self, url):
        row = self.entries.get(url, {})
        return decode((ROOT / row["file"]).read_bytes()) if row and not row.get("error") else ""


def probe(cache, urls):
    with ThreadPoolExecutor(max_workers=6) as pool:
        for row in pool.map(cache.fetch, urls):
            print(json.dumps({k:v for k,v in row.items() if k not in {"contentType"}}, ensure_ascii=False), flush=True)
    cache.save()


def inspect(cache):
    for url, row in cache.entries.items():
        source = cache.source(url)
        print("URL", url, "chars", len(source), "error", row.get("error", ""))
        if not source:
            continue
        print("links", re.findall(r'(?:href|src)=[\"\']([^\"\']+(?:menu|product|drink|food|bread|bakery)[^\"\']*)', source, re.I)[:35])
        for needle in ("프레시", "그릴드", "그릭", "프렌치", "카페라떼", "자몽", "시저", "고르곤", "게살", "마시는", "딸기", "수박", "토마토", "과카몰리"):
            found = source.find(needle)
            if found >= 0:
                print("match", needle, text(source[max(0,found-140):found+650]))
        print("scripts", re.findall(r'<script[^>]+src=[\"\']([^\"\']+)', source, re.I)[-8:])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--url", action="append", default=[])
    parser.add_argument("--inspect", action="store_true")
    parser.add_argument("--pascucci-detail", action="store_true")
    parser.add_argument("--final-public-menus", action="store_true")
    parser.add_argument("--verify-final-public-checks", action="store_true")
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    cache = Cache()
    if args.probe:
        probe(cache, args.url or [u for urls in PROBES.values() for u in urls])
    if args.inspect:
        inspect(cache)
    if args.pascucci_detail:
        print(json.dumps(cache.fetch("https://www.pascucci.co.kr/product/ajax/productDetail.asp", {"productSeq": "2561"}), ensure_ascii=False))
        cache.save()
    if args.final_public_menus:
        for url in (DROPTOP_MENU_BUNDLE_URL, DROPTOP_MENU_ROUTE_URL, DROPTOP_PUBLIC_CMS_URL):
            print(json.dumps(cache.fetch(url), ensure_ascii=False))
        print(json.dumps(cache.fetch(BANA_PUBLIC_MENU_URL, json_body=BANA_PUBLIC_MENU_BODY), ensure_ascii=False))
        cache.save()
    if args.generate:
        generate(cache)
    if args.verify:
        verify(cache)
    if args.verify_final_public_checks:
        verify_final_public_checks(cache)


if __name__ == "__main__":
    main()
