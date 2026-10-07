"""Offline deterministic import of a finite reviewed official chain snapshot.

Only visible menu cards are identities. Descriptions are PARTIAL ingredient
evidence, never full recipes. Explicit gram nutrition bases may be imported;
sugar/saturated fat are never substituted for carbohydrate/total fat.
"""
from __future__ import annotations
import csv
import hashlib
import json
import re
from collections import Counter
from decimal import Decimal
from pathlib import Path
from urllib.parse import urljoin
from lxml import html
from import_franchise_expansion import MENU_HEADERS, NUTRITION_HEADERS, write_csv, read_csv
from generate_franchise_brand_audit import parse_catalog, normalize
from import_franchise_quality import allergens as positive_allergens, ingredient_tokens

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data-source/franchise"
RAW = SOURCE / "raw/delivery-chain-pages.json"
DATE = "2026-10-04"
VERSION = "delivery-chain-v1"
_LABEL_TREES = {}
CATALOG = ROOT / "app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt"
# A source category belongs to the menu card/list, never copied from its brand.
CONFIG = [
    ("피자스쿨", "피자", ["pizzaschool-menu"], ["pizza school", "pizzaschool"]),
    ("파파존스", "피자", ["papajohns-pizza", "papajohns-other-side"], ["파파존스피자", "papa johns", "papajohns"]),
    ("청년피자", "피자", ["youngman-menu", "youngman-original", "youngman-side", "youngman-two"], ["youngman pizza", "youngmanpizza"]),
    ("노랑통닭", "치킨", ["norang-chicken-native", "norang-side-native"], ["norang tongdak", "norangtongdak"]),
    ("지코바", "치킨", ["gcova-chicken1", "gcova-chicken2", "gcova-chicken3", "gcova-chicken4", "gcova-chicken6"], ["지코바치킨", "gcova"]),
    ("처갓집양념치킨", "치킨", ["cheogajip-menu", "cheogajip-all"], ["처갓집", "처갓집치킨", "cheogajip"]),
    ("피자마루", "피자", ["pizzamaru-menu"], ["pizzamaru"]),
    ("멕시카나", "치킨", ["mexicana-menu", "mexicana-page2", "mexicana-page3"], ["멕시카나치킨", "mexicana"]),
    ("60계치킨", "치킨", ["60chicken-list"], ["60계", "육십계치킨", "60chicken"]),
    ("반올림피자", "피자", ["banolim-menu"], ["반올림피자샵", "banolim pizza", "banolimpizza"]),
    ("프랭크버거", "햄버거", ["frank-menu"], ["frankburger", "frank burger"]),
    ("노브랜드버거", "햄버거", ["nobrand-menu-native"], ["no brand burger", "nobrandburger", "NBB"]),
    ("새마을식당", "한식", ["newmaul-list"], ["newmaul"]),
    ("빽다방", "카페", ["paik-coffee", "paik-drink", "paik-dessert", "paik-ccino"], ["paiks coffee", "paikdabang"]),
    ("배스킨라빈스", "아이스크림", ["baskin-ice", "baskin-prepack", "baskin-drink", "baskin-coffee", "baskin-drink-sub", "baskin-coffee-sub"], ["베스킨라빈스", "베라", "배라", "baskin robbins", "baskinrobbins"]),
    ("파리바게뜨", "베이커리", ["paris-menu"], ["파리바게트", "파바", "paris baguette", "parisbaguette"]),
    ("요아정", "디저트", ["yoajung-list"], ["요거트아이스크림의정석", "yoajung"]),
    ("미소야", "돈가스", ["misoya-menu"], ["misoya"]),
    ("할리스", "카페", ["hollys-home"], ["할리스커피", "hollys", "hollys coffee"]),
    ("7번가피자", "피자", ["7th-list"] + [f"7th-cate{i}" for i in range(2, 9)], ["세븐스트리트피자", "7th street pizza", "7thpizza"]),
    ("토마토도시락", "도시락", ["tomato-list"], ["tomato dosirak", "tomatodosirak"]),
    ("동대문엽기떡볶이", "분식", ["yupdduk-list"], ["엽기떡볶이", "엽떡", "yupdduk"]),
    ("공차", "음료", ["gongcha-list"], ["gong cha", "gongcha"]),
    ("던킨", "베이커리", ["dunkin-list", "dunkin-donut", "dunkin-food", "dunkin-coffee", "dunkin-drink", "dunkin-snack"], ["던킨도너츠", "던킨도넛", "dunkin", "dunkin donuts"]),
    ("족발야시장", "족발·보쌈", ["jokbal-menu-resolved"], []),
    ("호식이두마리치킨", "치킨", ["hosigi-list"], ["호식이", "호식이치킨", "hosigi"]),
]


def clean(value):
    return re.sub(r"\s+", " ", value or "").strip()


def text(node, xpath="."):
    found = node.xpath(xpath)
    return clean(found[0].text_content()) if found else ""


def category(name, source):
    """Conservative per-menu map of literal names or an official menu group."""
    n = normalize(name)
    if source in ("피자", "프리미엄 피자", "두 판 세트", "세트 메뉴") and not any(w in n for w in ("스파게티", "파스타", "뇨끼")):
        return "피자"
    if source in ("아이스크림", "프리팩"):
        return "아이스크림"
    if source in ("아이스크림 케이크", "케이크"):
        return "디저트"
    if source in ("디저트", "디저트/스낵") and not any(w in n for w in ("샌드위치", "토스트")):
        return "디저트"
    for words, mapped in [(("스파게티", "파스타", "뇨끼"), "파스타"),
        (("샐러드", "포케"), "샐러드·포케"), (("샌드위치", "그린랩"), "샌드위치"),
        (("토스트",), "토스트"), (("떡볶이",), "분식"), (("김밥",), "김밥"),
        (("피자",), "피자"), (("버거",), "햄버거"), (("돈카츠", "돈까스", "돈가스", "로스카츠", "히레카츠"), "돈가스"),
        (("라멘",), "라멘"), (("우동", "모밀", "소바"), "국수·우동"),
        (("볶음밥", "덮밥", "마요밥"), "덮밥"), (("죽",), "죽"),
        (("족발", "보쌈"), "족발·보쌈"), (("라떼", "커피", "아메리카노", "에스프레소"), "카페")]:
        if any(w in n for w in words):
            return mapped
    # Original official pizza/burger groups disambiguate names like 치킨바베큐.
    return {"피자": "피자", "프리미엄 피자": "피자", "세트 메뉴": "피자", "두 판 세트": "피자",
        "버거": "햄버거", "NBB 어메이징": "햄버거", "치킨": "치킨", "아이스크림": "아이스크림",
        "아이스크림 케이크": "디저트", "디저트": "디저트", "프리팩": "아이스크림",
        "음료": "음료", "커피": "카페", "커피/음료": "음료", "빽스치노": "음료",
        "브레드": "베이커리", "케이크": "디저트", "선물": "베이커리", "디저트/스낵": "디저트",
        "요거트 아이스크림/토핑": "디저트", "도시락": "도시락", "고기류": "한식"}.get(source, "")


def card_rows(page):
    tree = html.fromstring(re.sub(r"<!--.*?-->", "", page["text"], flags=re.S))
    key, url = page["key"], page["sourceUrl"]
    results = []

    def add(name, group="", description="", link=""):
        name = clean(name)
        if name:
            results.append((name, group, clean(description), urljoin(url, link) if link else url))

    if key == "pizzaschool-menu":
        for node in tree.xpath("//h3[contains(@class,'grid-entry-title')]/a"):
            name = clean(node.text_content())
            if not any(w in name for w in ("피자", "스파게티", "치즈볼", "치킨텐더", "치킨스틱", "새우링", "꽈배기", "그라탕")):
                continue
            add(name, "피자" if "피자" in name else "사이드", text(node.getparent().getparent().getparent(), ".//*[contains(@class,'grid-entry-excerpt')]"), node.get("href"))
    elif key == "papajohns-pizza":
        for node in tree.xpath("//a[starts-with(@href,'/menu/pizza/')]"):
            add(text(node, ".//p[contains(@class,'font-bold')]"), "피자", link=node.get("href"))
    elif key == "papajohns-other-side":
        for node in tree.xpath("//p[contains(@class,'lg:text-xl') and contains(@class,'font-bold')]"):
            name = clean(" ".join(node.xpath("./text()")))
            if "굿즈" not in name:
                add(name, "사이드")
    elif key.startswith("youngman-"):
        group = {"youngman-menu": "프리미엄 피자", "youngman-original": "세트 메뉴", "youngman-two": "두 판 세트"}.get(key, "사이드")
        for node in tree.xpath("//*[contains(@class,'menu_name')]"):
            ancestors = node.xpath("ancestor::a[1]")
            add(node.text_content(), group, link=ancestors[0].get("href", "") if ancestors else "")
    elif key.startswith("norang-"):
        for node in tree.xpath("//a[contains(@href,'_view.html')]/p"):
            add(node.text_content(), "치킨" if "chicken" in key else "사이드", link=node.getparent().get("href"))
    elif key.startswith("gcova-chicken"):
        for node in tree.xpath("//*[contains(@class,'sub_title')]"):
            add(node.text_content(), "치킨")
    elif key.startswith("cheogajip-"):
        for node in tree.xpath("//li[@class='gall_text_href']/h2"):
            add(node.text_content(), "치킨" if "치킨" in node.text_content() or "윙" in node.text_content() else "사이드")
    elif key == "pizzamaru-menu":
        for node in tree.xpath("//*[@id='content_wrap']//div[contains(@class,'con08_menu_tt')]"):
            group = node.xpath("ancestor::div[contains(@class,'con08_menu ')][1]")
            # Official sixth carousel is the side group, separate from pizzas.
            is_side = group and "con08_menu06" in group[0].get("class", "")
            add(node.text_content(), "사이드" if is_side else "피자", text(node.getparent(), ".//*[contains(@class,'con08_menu_desc')]"))
    elif key.startswith("mexicana-"):
        for node in tree.xpath("//dl[@class='tit']"):
            add(text(node, "./dt"), "치킨", text(node, "./dd"))
    elif key == "60chicken-list":
        for node in tree.xpath("//p[@class='name']"):
            # Responsive clones retain identical identities; de-duplicated below.
            add(node.text_content(), "치킨" if "치킨" in node.text_content() or "콤보" in node.text_content() or "윙" in node.text_content() else "사이드", text(node.getparent(), ".//p[@class='text']"))
    elif key == "banolim-menu":
        for node in tree.xpath("//p[contains(@class,'_title_')]"):
            add(node.text_content(), "")
    elif key == "frank-menu":
        for node in tree.xpath("//p[@class='menu_ko']"):
            add(node.text_content(), "버거" if "버거" in node.text_content() else "사이드", text(node.getparent(), ".//p[@class='stext']"))
    elif key == "nobrand-menu-native":
        for node in tree.xpath("//li[contains(@class,'menu_item')]/button[@data-name]"):
            group = text(node.xpath("ancestor::div[contains(@class,'menu_group')][1]")[0], ".//em[@class='menu_group_title']")
            name = re.split(r"<\s*/?\s*br\s*/?>", node.get("data-name", ""), flags=re.I)[0]
            description = html.fromstring("<div>" + node.get("data-story", "") + "</div>").text_content()
            add(name, group, description)
    elif key == "newmaul-list":
        for node in tree.xpath("//h3[not(@class='blind')]"):
            add(node.text_content(), "고기류")
    elif key.startswith("paik-"):
        group = {"paik-coffee": "커피", "paik-drink": "음료", "paik-dessert": "디저트", "paik-ccino": "빽스치노"}[key]
        for node in tree.xpath("//h3[@class='font-bl']"):
            add(node.text_content(), group, text(node.getparent(), ".//p"))
    elif key.startswith("baskin-"):
        group = text(tree, "//h2[@class='page-header__title']")
        group = {"Ice Cream": "아이스크림", "Ice Cream Cake": "아이스크림 케이크", "Dessert": "디저트", "Prepack": "프리팩", "Beverage": "음료", "Coffee": "커피"}.get(group, group)
        for node in tree.xpath("//strong[@class='menu-list__title']"):
            links = node.getparent().xpath("./a[@href]")
            add(node.text_content(), group, link=links[0].get("href") if links else "")
    elif key == "paris-menu":
        for node in tree.xpath("//h3[@class='product-name']"):
            preceding = node.xpath("preceding::h2[@class='category-title'][1]")
            ancestors = node.xpath("ancestor::a[1]")
            add(node.text_content(), clean(preceding[0].text_content()) if preceding else "", link=ancestors[0].get("href") if ancestors else "")
    elif key == "yoajung-list":
        for node in tree.xpath("//*[contains(@class,'main_renew_top_con_top_2nd_item')][@data-total]"):
            add(text(node, ".//*[contains(@class,'main_renew_top_con_top_2nd_item_top_2nd')]/p"), "요거트 아이스크림/토핑")
    elif key == "misoya-menu":
        for node in tree.xpath("//p[@class='title']/strong"):
            if clean(node.text_content()) in ("신선합니다","맛있습니다","건강합니다"):
                continue
            add(node.text_content(), "", text(node.getparent(), ".//span[@class='body']"))
    elif key == "hosigi-list":
        for node in tree.xpath("//div[starts-with(@id,'caption_')]/h4"):
            # The caption's first text node is the food identity; span is an option.
            name = clean(" ".join(node.xpath("./text()")))
            add(name, "치킨" if "치킨" in name else "", text(node.getparent(), "./p"))
    elif key == "hollys-home":
        for node in tree.xpath("//div[@class='menu_view01']"):
            add(text(node, ".//div[@class='menu_detail']/p/span"), "커피", text(node, ".//p[@class='menu_info']"))
    elif key.startswith("7th-"):
        for node in tree.xpath("//strong[ancestor::div[contains(@class,'menu')]]"):
            name = clean(node.text_content())
            if name in ("MENU", "NEW"):
                continue
            add(name, "사이드" if key == "7th-cate8" else "피자")
    elif key == "tomato-list":
        for node in tree.xpath("//p[@class='menu_tit']"):
            add(node.text_content(), "도시락")
    elif key == "yupdduk-list":
        for node in tree.xpath("//p[@class='menutitle']"):
            preceding = node.xpath("preceding::p[@class='menutabtitle'][1]")
            add(node.text_content(), clean(preceding[0].text_content()) if preceding else "")
    elif key == "gongcha-list":
        for node in tree.xpath("//a[contains(@href,'product_detail')]"):
            add(text(node, ".//div[@class='text-a']/p"), "음료", link=node.get("href"))
    elif key.startswith("dunkin-"):
        for node in tree.xpath("//a[contains(@href,'/menu/view?') or contains(@href,'/menu/viewProductCat?')]"):
            group = "디저트" if "cat=1" in node.get("href") else "커피" if "cat=6" in node.get("href") else "음료" if "cat=3" in node.get("href") else ""
            name = text(node, ".//h4")
            if not any(word in name for word in ("텀블러", "머그", "키링", "굿즈")):
                add(name, group, link=node.get("href"))
    elif key == "jokbal-menu-resolved":
        for node in tree.xpath("//p[contains(@class,'menu_list_tit')]"):
            add(node.text_content(), "", text(node.getparent(), ".//p[contains(@class,'menu_list_desc')]"))
    return results


def list_label_evidence(menu, page):
    """Preserve available labels when their serving basis is unresolved."""
    tree = _LABEL_TREES.get(page["key"])
    if tree is None:
        tree = html.fromstring(page["text"])
        _LABEL_TREES[page["key"]] = tree
    if menu["brand"] == "빽다방":
        for node in tree.xpath("//h3[@class='font-bl']"):
            if normalize(node.text_content()) != normalize(menu["name"]):
                continue
            card = node.getparent()
            allergy = " ".join(clean(n.text_content()) for n in card.xpath(".//p[@class='menu_ingredient_basis']") if "알레르기" in n.text_content())
            raw = " | ".join(clean(n.text_content()) for n in card.xpath(".//ul[@class='ingredient_table']/li|.//p[@class='menu_ingredient_basis']"))
            return allergy, raw
    if menu["brand"] == "요아정":
        for node in tree.xpath("//*[contains(@class,'main_renew_top_con_top_2nd_item')][@data-total]"):
            name = text(node, ".//*[contains(@class,'main_renew_top_con_top_2nd_item_top_2nd')]/p")
            if normalize(name) == normalize(menu["name"]):
                raw = " | ".join(f"{k}={v}" for k,v in node.attrib.items() if k.startswith("data-"))
                return node.get("data-allergy", "").replace("|", ","), raw
    return "", ""


def number(value):
    match = re.fullmatch(r"\s*([0-9]+(?:\.[0-9]+)?)\s*(?:g|mg|kcal)?\s*", value)
    return match.group(1) if match else ""


def make_id(brand, name, prefix="official-menu-delivery"):
    digest = hashlib.sha256((normalize(brand) + ":" + normalize(name)).encode()).hexdigest()[:16]
    return f"{prefix}-{digest}"


def detail_evidence(menu, page):
    """Return nutrition only when this exact detail has an explicit g basis."""
    tree = html.fromstring(page["text"])
    name, brand, url = menu["name"], menu["brand"], page["sourceUrl"]
    result, ingredient, allergen, nutrient_raw = [], "", "", ""

    def add(variant, amount, kcal, protein="", sodium="", basis="", source_category=""):
        if not number(amount) or not number(kcal):
            return
        variant_name = name + (" (" + variant + ")" if variant else "")
        result.append(dict(id=make_id(brand, variant_name, "official-delivery"), brand=brand, name=variant_name,
            category=category(name, menu["sourceCategory"]), referenceAmount=number(amount), unit="g", energyKcal=number(kcal),
            carbohydrateGrams="", proteinGrams=number(protein), fatGrams="", sodiumMilligrams=number(sodium),
            sourceUrl=url, sourceDate="", verifiedAt=DATE, saleState="CURRENT_OFFICIAL_NUTRITION_SNAPSHOT", sourcePage="",
            sourceCategory=source_category or menu["sourceCategory"], servingBasisText=basis))

    if brand == "피자스쿨":
        if normalize(text(tree, "//h1[@class='av-special-heading-tag']")) != normalize(name):
            return result, ingredient, allergen, "DETAIL_IDENTITY_MISMATCH"
        tables = tree.xpath("//table[@class='tbl_type01']")
        for table in tables:
            table_text = clean(table.text_content())
            if "알레르기" in table_text:
                allergen += table_text
                continue
            if "열량" not in table_text:
                continue
            nutrient_raw = table_text
            values = {}
            for row in table.xpath(".//tr"):
                cells = [clean(c.text_content()) for c in row.xpath("./th|./td")]
                if len(cells) > 1:
                    values[cells[0]] = cells[1:]
            basis = values.get("1회 섭취 참고량", [""])[0]
            mass = re.search(r"\(([0-9]+(?:\.[0-9]+)?)g\)", basis)
            # Per-portion energy must use the declared reference amount, not total pizza mass.
            if mass:
                add("오리지널", mass.group(1), values.get("열량", [""])[0], values.get("단백질", [""])[0], values.get("나트륨", [""])[0], basis)
        for heading in tree.xpath("//h4[contains(@class,'iconlist_title')]"):
            if clean(heading.text_content()) == "주요토핑":
                ingredient = clean(heading.getparent().text_content())
    elif brand == "청년피자":
        detail_name = text(tree, "//div[@class='info_title']/p")
        if normalize(detail_name) != normalize(name):
            return result, ingredient, allergen, "DETAIL_IDENTITY_MISMATCH"
        ingredient = text(tree, "//div[@class='info_sub_title']/p")
        tables = tree.xpath("//table[not(@class)]")
        if tables:
            table = tables[0]
            nutrient_raw = clean(table.text_content())
            headers = clean(" ".join(table.xpath(".//thead//text()")))
            for row in table.xpath(".//tbody/tr"):
                values = [clean(c.text_content()) for c in row.xpath("./td")]
                if "1회분 중량" in headers and len(values) == 10:
                    add(values[0], values[4], values[5], values[7], values[9], f"{values[3]}조각 = {values[4]}g ({values[0]})")
                elif "1회제공량g" in normalize(headers) and len(values) == 6:
                    add("", values[0], values[1], values[3], values[5], f"1회 제공량 {values[0]}g")
    elif brand == "배스킨라빈스":
        if normalize(text(tree, "//span[@class='menu-view-header__title--ko']")) != normalize(name):
            return result, ingredient, allergen, "DETAIL_IDENTITY_MISMATCH"
        values = {}
        for row in tree.xpath("//*[contains(@class,'menu-view-nutrition__item')]"):
            values[text(row, "./dt")] = text(row, "./dd")
        nutrient_raw = " | ".join(f"{k}: {v}" for k, v in values.items())
        allergen = values.get("※ 알레르기 성분", "")
        add("", values.get("1회 제공량(g)", ""), values.get("열량(kcal)", ""), values.get("단백질(g)", ""), values.get("나트륨(mg)", ""), "1회 제공량 " + values.get("1회 제공량(g)", "") + "g")
    elif brand == "파리바게뜨":
        if normalize(text(tree, "//h1[@class='product-name']")) != normalize(name):
            return result, ingredient, allergen, "DETAIL_IDENTITY_MISMATCH"
        ingredient = text(tree, "//div[@class='product-info']/p")
        allergen = text(tree, "//div[contains(@class,'product-allergy')]/div[@class='product-info-group-description']")
        nutrient_raw = text(tree, "//div[contains(@class,'product-nutrition')]/div[@class='product-info-group-description']")
        mass = re.search(r"총 내용량\s*:\s*([0-9.]+)\s*g", nutrient_raw)
        kcal = re.search(r"총 내용량당 칼로리\(kcal\)\s*:\s*([0-9.]+)", nutrient_raw)
        value = lambda label: (re.search(label + r"\s*:\s*([0-9.]+)", nutrient_raw).group(1) if re.search(label + r"\s*:\s*([0-9.]+)", nutrient_raw) else "")
        if mass and kcal:
            add("", mass.group(1), kcal.group(1), value(r"단백질\(g\)"), value(r"나트륨\(mg\)"), "총 내용량당 " + mass.group(1) + "g")
    return result, ingredient, allergen, nutrient_raw


def candidate_audit(existing, configs, pages, menus, nutrition):
    groups = {"치킨": [], "피자": [], "햄버거": [], "분식": [], "한식": [], "죽": [], "도시락": [], "중식": [], "일식": [], "족발/보쌈": [], "찜/탕": [], "샌드위치": [], "베이커리": [], "카페": [], "음료": [], "디저트": [], "아이스크림": [], "기타 실제 배달 체인": []}
    crosswalk = {"김밥":"분식", "죽·비빔밥":"죽", "마라탕":"중식", "돈가스":"일식", "라멘":"일식", "국수·우동":"일식", "족발·보쌈":"족발/보쌈", "찜닭":"찜/탕", "국밥":"찜/탕", "부대찌개":"찜/탕", "삼계탕":"찜/탕", "샤브샤브":"찜/탕", "덮밥":"한식", "토스트":"샌드위치", "샐러드·포케":"기타 실제 배달 체인"}
    new = {row[0]: row for row in configs}
    all_brands = {row["name"]: (row["category"], row["officialUrl"]) for row in existing}
    all_brands.update({name:(cat,pages[keys[0]]["sourceUrl"]) for name,cat,keys,aliases in configs})
    unresolved = [("펠리카나","치킨","pelicana-menu","Official page renders categories, menu cards require dynamic data; no exact visible menu parsed."),
        ("배떡","분식","baedduck-menu","Official menu is image-only in captured page; nutrition/allergen pages also reviewed, exact image table transcription unresolved."),
        ("신참떡볶이","분식","sincham-list","Official Imweb menu requires image identity review; no text-only identity guessed."),
        ("뚜레쥬르","베이커리","tous-menu","Official current public page returned redirect shell without menu cards."),
        ("롤링파스타","기타 실제 배달 체인","rolling-home","Official menu categories present but actual menu names embedded in images."),
        ("설빙","디저트","sulbing-menu","Official entry/redirect page did not expose actual menu cards."),
        ("쥬씨","음료","juicy-correct-home","Candidate domain lookup failed; official exact identity source unresolved."),
        ("가장맛있는족발","족발/보쌈","familyjokbal-correct-home","Candidate domain lookup failed; current official source unresolved."),
        ("더벤티","카페","theventi-brand","Official company-to-brand landing page captured; current menu route unresolved."),
        ("빽보이피자","피자","paikboy-correct-home","Official candidate URL timed out; no menu identity imported."),
        ("푸라닭","치킨","puradak-home","Official landing/redirect shell has no actual menu cards."),
        ("또래오래","치킨","toreore-home","Official TLS verification failed in capture."),
        ("고피자","피자","gopizza-home","Official dynamic home captured without current menu cards."),
        ("짬뽕지존","중식","jizon-home","Candidate public page returned minimal placeholder, exact official current menu unresolved."),
        ("오봉도시락","도시락","obong-home","Candidate public page returned minimal placeholder; alternative domain lookup failed."),
        ("땅스부대찌개","찜/탕","ttangs-home","Candidate domain is parked/for-sale, rejected as official food source."),
        ("신의주찹쌀순대","찜/탕","sinuiju-home","Candidate domain lookup failed; exact current official source unresolved.")]
    for name,cat,key,reason in unresolved:
        all_brands[name] = (cat,pages[key]["sourceUrl"])
    unresolved_by_name = {name:reason for name,cat,key,reason in unresolved}
    rows = []
    for name,(cat,url) in sorted(all_brands.items()):
        group = crosswalk.get(cat,cat)
        groups[group if group in groups else "기타 실제 배달 체인"].append(name)
        found = [p for p in pages.values() if p["brand"] == name or (name == "처갓집양념치킨" and p["brand"] == "처갓집")]
        rows.append(dict(brand=name,industry=group,officialSource=url,sourceType="OFFICIAL_BRAND_WEB_PAGE" if found else "PRESERVED_OFFICIAL_PUBLIC_BASELINE",
            discoveryStatus="ADDED_ACTUAL_OFFICIAL_MENUS" if name in new else "UNRESOLVED" if name in unresolved_by_name else "EXISTING_PROTECTED_BASELINE",
            actualMenuCount=sum(r["brand"]==name for r in menus),nutritionMenuCount=sum(r["brand"]==name for r in nutrition),
            sourceAttempts=[dict(key=p["key"],sourceUrl=p["sourceUrl"],status=p.get("status"),failureReason=p.get("failureReason",""),sha256=p.get("originalSha256","")) for p in found],
            unresolvedReason=unresolved_by_name.get(name,"Nutrition absent remains UNKNOWN; explicit serving/nutrient/recipe fields only." if name in new else "Protected baseline; no broad re-import.")))
    return dict(verifiedAt=DATE,parserVersion=VERSION,industries=groups,candidates=rows,
        policy="Finite official/public discovery only. No delivery-app scraping, guessed menus, nutrition, serving, zero defaults or allergen-safe inference.")


def gcova_nutrition(pages):
    basis_file = SOURCE/"raw/gcova/txt.jpg"
    expected_basis = "6D6D69BA4F3ED5EC93CE219D2F099815D555C1252E0FDA0DD6C5EC4ADD104025"
    if hashlib.sha256(basis_file.read_bytes()).hexdigest().upper() != expected_basis:
        raise ValueError("Official Gcova explicit 100g basis image changed; review required")
    result = []
    for label in read_csv(SOURCE/"gcova-reviewed-nutrition.csv"):
        file = SOURCE/"raw/gcova"/label["image"]
        if hashlib.sha256(file.read_bytes()).hexdigest().upper() != label["sha256"]:
            raise ValueError("Visually reviewed official Gcova label changed")
        page = pages[label["pageKey"]]
        if label["image"] not in page["text"] or label["labelName"] not in page["text"]:
            raise ValueError("Official page-to-nutrition exact label identity mismatch")
        result.append(dict(id=make_id("지코바",label["name"],"official-delivery"),brand="지코바",name=label["name"],category="치킨",
            referenceAmount="100",unit="g",energyKcal=label["energyKcal"],carbohydrateGrams="",proteinGrams=label["proteinGrams"],
            fatGrams=label["fatGrams"],sodiumMilligrams=label["sodiumMilligrams"],sourceUrl=urljoin(page["sourceUrl"],"images/pop_info/"+label["image"]),
            sourceDate="",verifiedAt=DATE,saleState="CURRENT_OFFICIAL_NUTRITION_SNAPSHOT",sourcePage="",
            sourceCategory="치킨",servingBasisText="100g당 영양량; 가정 단위/한 마리 조리 후 중량은 미확인",sourceHash=label["sha256"],menuIdentitySourceUrl=page["sourceUrl"]))
    return result


def run():
    pages = {p["key"]:p for p in json.loads(RAW.read_text(encoding="utf-8"))}
    menus, nutrition, evidence = {}, {}, {}
    url_pages = {p["sourceUrl"].lower():p for p in pages.values() if p.get("status")==200 and "text" in p}
    for brand, cat, keys, aliases in CONFIG:
        for key in keys:
            page = pages[key]
            if page.get("status") != 200 or page.get("failureReason"):
                continue
            for name, group, description, url in card_rows(page):
                identity = (brand,normalize(name))
                row = dict(id=make_id(brand,name),brand=brand,name=name,sourceFoodCode=make_id(brand,name,"DELIVERY").upper(),
                    sourceUrl=url,sourceDate="",verifiedAt=DATE,saleState="CURRENT_MENU_LISTED",energyKcal="",servingAmount="",servingUnit="",
                    sourceCategory=group,mappedCategory=category(name,group),sourcePageKey=key,sourceHash=page["originalSha256"])
                if identity in menus:
                    continue
                menus[identity] = row
                detail = url_pages.get(url.lower())
                found, ing, allergen, raw_nutrients = detail_evidence(row,detail) if detail else ([],"","","")
                ing = ing or description
                list_allergen, list_raw = list_label_evidence(row,page) if brand in ("빽다방","요아정") else ("","")
                allergen = allergen or list_allergen
                raw_nutrients = raw_nutrients or list_raw
                ids = [row["id"]]
                for nr in found:
                    nr["sourceHash"] = detail["originalSha256"]
                    nr["menuIdentitySourceUrl"] = row["sourceUrl"]
                    nutrition[(brand,normalize(nr["name"]))] = nr
                    ids.append(nr["id"])
                if ing or allergen or raw_nutrients:
                    for fid in ids:
                        evidence[fid] = dict(foodItemId=fid,sourceFoodCode=row["sourceFoodCode"],brand=brand,name=name,
                            ingredientText=ing,ingredients=ingredient_tokens(ing),allergenText=allergen,allergens=positive_allergens(allergen),ingredientStatus="PARTIAL_DESCRIPTION" if ing else "UNKNOWN",
                            allergenStatus="PARTIAL_INGREDIENT_EVIDENCE" if allergen else "UNKNOWN",sourceUrl=detail["sourceUrl"] if detail else url,
                            checkedAt=DATE,sourceDate="",parserVersion=VERSION,evidenceKind="OFFICIAL_MENU_DESCRIPTION_OR_LABEL",
                            mayContainAllergens="",crossContactText="",staleCandidate="false",sourceHash=(detail or page)["originalSha256"],
                            rawNutritionText=raw_nutrients,sourceCategory=group,menuCategory=row["mappedCategory"])
    for row in gcova_nutrition(pages):
        nutrition[(row["brand"],normalize(row["name"]))] = row
    menu_rows = sorted(menus.values(),key=lambda r:(r["brand"],r["id"]))
    nutrition_rows = sorted(nutrition.values(),key=lambda r:(r["brand"],r["id"]))
    # Exact same identity becomes a nutrition FoodItem; variants keep their menu identity.
    nutrition_ids = set(nutrition)
    menu_rows = [r for r in menu_rows if (r["brand"],normalize(r["name"])) not in nutrition_ids]
    valid_ids = {r["id"] for r in menu_rows + nutrition_rows}
    evidence = {fid:row for fid,row in evidence.items() if fid in valid_ids}
    for row in menu_rows + nutrition_rows:
        exact_code = row.get("sourceFoodCode",row["id"].removeprefix("official-").upper())
        if row["id"] in evidence:
            evidence[row["id"]].update(sourceFoodCode=exact_code,name=row["name"],
                menuCategory=row.get("mappedCategory",row.get("category","")))
        if row["id"] not in evidence:
            evidence[row["id"]] = dict(foodItemId=row["id"],sourceFoodCode=exact_code,
                brand=row["brand"],name=row["name"],ingredientStatus="UNKNOWN",allergenStatus="UNKNOWN",sourceUrl=row["sourceUrl"],
                checkedAt=DATE,sourceDate="",parserVersion=VERSION,evidenceKind="EXACT_OFFICIAL_MENU_ID",staleCandidate="false",
                sourceHash=row.get("sourceHash",""),sourceCategory=row["sourceCategory"],menuCategory=row.get("mappedCategory",row.get("category","")))
        # Exact HTTPS pizza table states one slice and its mass together. No
        # arithmetic, whole-pizza/pre-cooking mass or cup capacity is substituted.
        declared = re.fullmatch(r"1조각 = ([0-9]+(?:\.[0-9]+)?)g \((R|L)\)",row.get("servingBasisText",""))
        if row["brand"] == "청년피자" and declared and row["sourceUrl"].startswith("https://"):
            evidence[row["id"]].update(householdUnit="조각",basisAmountPerUnit=declared.group(1),basisUnit="g",
                servingSourceReference=row["sourceUrl"],servingEvidenceKind="OFFICIAL_SERVING",
                servingSourceSize=row["name"] + " · " + row["servingBasisText"])
        declared_slices = re.fullmatch(r"([0-9]+)조각\s*\(([0-9]+(?:\.[0-9]+)?)g\)\s*/\s*(?:약\s*)?[0-9]+회\s*제공량",row.get("servingBasisText",""))
        if row["brand"] == "피자스쿨" and declared_slices and row["sourceUrl"].startswith(("http://","https://")):
            count, mass = Decimal(declared_slices.group(1)), Decimal(declared_slices.group(2))
            if count > 0 and mass > 0:
                per_slice = format(mass/count,"f").rstrip("0").rstrip(".") if "." in format(mass/count,"f") else format(mass/count,"f")
                evidence[row["id"]].update(householdUnit="조각",basisAmountPerUnit=per_slice,basisUnit="g",
                    servingSourceReference=row["sourceUrl"],servingEvidenceKind="VERIFIED_CONVERSION",
                    servingSourceSize=row["name"] + " · 원문 " + row["servingBasisText"] + " · 명시 조각수로 나눈 1조각 " + per_slice + "g")
    counts = Counter(r["brand"] for r in menu_rows + nutrition_rows)
    if any(not counts[name] for name,cat,keys,aliases in CONFIG):
        raise ValueError("A new registered brand must expose actual parsed menu identities: " + str(counts))
    for r in nutrition_rows:
        if r["carbohydrateGrams"] or (r["fatGrams"] and r["brand"] != "지코바"):
            raise ValueError("These official tables provide sugar/saturated fat, not total macros")
    write_csv(SOURCE/"delivery-menu-snapshot.csv",MENU_HEADERS+("sourceCategory","mappedCategory","sourcePageKey","sourceHash"),menu_rows)
    write_csv(SOURCE/"delivery-nutrition.csv",NUTRITION_HEADERS+("sourceCategory","servingBasisText","sourceHash","menuIdentitySourceUrl"),nutrition_rows)
    headers = tuple(dict.fromkeys(tuple(read_csv(SOURCE/"food-metadata-evidence.csv")[0])+("rawNutritionText","sourceCategory","menuCategory",
        "householdUnit","basisAmountPerUnit","basisUnit","servingSourceReference","servingEvidenceKind","servingSourceSize")))
    write_csv(SOURCE/"delivery-metadata-evidence.csv",headers,sorted(evidence.values(),key=lambda r:r["foodItemId"]))
    existing = parse_catalog(CATALOG.read_text(encoding="utf-8"))
    audit = candidate_audit(existing,CONFIG,pages,menu_rows,nutrition_rows)
    (SOURCE/"delivery-chain-candidate-audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    registry = [dict(name=name,category=cat,officialUrl=pages[keys[0]]["sourceUrl"],aliases=aliases,machineReadableNutrition=any(r["brand"]==name for r in nutrition_rows),menuCount=counts[name]) for name,cat,keys,aliases in CONFIG]
    (SOURCE/"delivery-chain-registry.json").write_text(json.dumps(registry,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    register_brands(registry)
    print(json.dumps(dict(menuOnly=len(menu_rows),nutrition=len(nutrition_rows),metadata=len(evidence),brands=counts),ensure_ascii=False))


def register_brands(registry):
    original = CATALOG.read_text(encoding="utf-8")
    start, end = "        // BEGIN GENERATED DELIVERY CHAIN REGISTRY", "        // END GENERATED DELIVERY CHAIN REGISTRY"
    rows = []
    for row in registry:
        quote = lambda value: json.dumps(value,ensure_ascii=False)
        aliases = ", ".join(quote(alias) for alias in row["aliases"])
        rows.append(f'        FranchiseBrand({quote(row["name"])}, {quote(row["category"])}, {quote(row["officialUrl"])}, setOf({aliases}), {str(row["machineReadableNutrition"]).lower()}),')
    block = start + "\n" + "\n".join(rows) + "\n" + end
    if start in original:
        updated = re.sub(re.escape(start) + r".*?" + re.escape(end),lambda match:block,original,flags=re.S)
    else:
        anchor = '            officialMenuNames = listOf("골드피자 토스트", "콘베이컨 에그마요 토스트"))'
        if anchor not in original:
            raise ValueError("Protected baseline registry anchor changed; review before appending brands")
        updated = original.replace(anchor,anchor + ",\n" + block)
    CATALOG.write_text(updated,encoding="utf-8")


if __name__ == "__main__":
    run()
