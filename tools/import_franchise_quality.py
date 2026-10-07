"""Deterministic metadata parser; menu names never become ingredient/allergen evidence."""
from __future__ import annotations
import csv
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlsplit, parse_qs, urljoin
from lxml import html
from import_franchise_expansion import read_csv, write_csv, MENU_HEADERS, NUTRITION_HEADERS
from generate_franchise_brand_audit import normalize, parse_catalog, CATALOG

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data-source/franchise"
RAW = SOURCE / "raw"
VERSION = "franchise-quality-v1"
METADATA_HEADERS = ("foodItemId", "sourceFoodCode", "brand", "name", "ingredientText", "ingredients",
    "allergenText", "allergens", "ingredientStatus", "allergenStatus", "sourceUrl", "checkedAt", "sourceDate",
    "parserVersion", "evidenceKind", "mayContainAllergens", "crossContactText", "staleCandidate", "sourceHash")
CAUSES = {"우유": ("우유",), "달걀": ("난류", "알류", "계란", "달걀"), "땅콩": ("땅콩",),
    "견과류": ("호두", "잣", "아몬드", "캐슈넛", "견과류"), "밀": ("밀", "밀가루"), "대두": ("대두",),
    "새우": ("새우",), "게": ("게",), "생선": ("생선", "고등어"), "조개류": ("조개류", "조개", "굴", "홍합", "전복")}
INGREDIENT_TERMS = ("흑염소", "양고기", "소고기", "쇠고기", "돼지고기", "닭고기", "닭가슴살", "오리고기", "전복", "새우", "홍게", "게살", "오징어", "낙지", "명란", "연어", "참치", "고등어", "굴", "홍합", "수삼", "인삼", "능이버섯", "자연송이", "표고버섯", "느타리버섯", "팽이버섯", "버섯", "감자", "양파", "애호박", "당근", "브로콜리", "청경채", "열무김치", "열무", "김치", "배추", "미역", "김", "감태", "장조림", "계란", "달걀", "메추리알", "현미", "귀리", "보리", "흑미", "퀴노아", "찹쌀", "쌀", "밥", "율무", "녹두", "단호박", "완두콩", "콩", "두부", "호두", "잣", "땅콩", "치즈", "우유", "크림", "꿀", "사과", "바나나", "토마토", "양상추", "상추", "오이", "아보카도", "파프리카", "옥수수", "고구마", "밤", "대추", "깨", "참깨", "검은깨")
INGREDIENT_TERMS += ("문어", "닭다리살", "훈제오리", "베이컨", "햄", "양상추", "로메인", "크루통", "병아리콩", "에다마메", "단무지", "콩나물", "양배추", "숙주", "미나리", "부채살", "차돌", "우삼겹", "바질", "통밀", "레몬", "양송이", "마늘", "수육", "양지고기", "파", "깻잎", "멸치", "고추", "등심", "안심", "소시지")
NAMESPACE = {"봉추찜닭":"bongchu", "일미리금계찜닭":"ilmiri", "육수당":"yuksudang", "현대옥":"hyundaiok",
    "이화수전통육개장":"ihwasoo", "죠스떡볶이":"jaws", "청년다방":"youngdabang", "두끼":"dookki",
    "스쿨푸드":"schoolfood", "바르다김선생":"teacherkim", "홍콩반점0410":"hongkong", "이비가짬뽕":"ebiga",
    "탕화쿵푸":"tanghua", "춘리마라탕":"chunli", "죽이야기":"jukstory", "국수나무":"namuya",
    "역전우동0410":"yeokjeon", "홍익돈까스":"hongik", "원할머니보쌈":"wonhalmoni", "놀부부대찌개":"nolboo",
    "핵밥":"hecbob", "채선당":"chaesundang", "소담촌":"sodamchon", "포케올데이":"pokeallday",
    "슬로우캘리":"slowcali", "써브웨이":"subway", "에그드랍":"eggdrop"}


def document(markup):
    root = html.fromstring(markup or "<div></div>") if re.search(r"<html|<!doctype", markup or "", re.I) else html.fragment_fromstring(markup or "<div></div>", create_parent="div")
    for node in root.xpath("//script|//style|//comment()"):
        if node.getparent() is not None:
            node.getparent().remove(node)
    return root


def text(markup):
    return re.sub(r"\s+", " ", " ".join(document(markup).itertext())).strip()


def allergens(raw):
    # Match source declaration tokens, not substrings such as '참깨' -> 게.
    tokens = set(re.findall(r"[가-힣]+", raw or ""))
    return "|".join(sorted(cause for cause, aliases in CAUSES.items() if any(alias in tokens for alias in aliases)))


def ingredient_tokens(raw):
    # Component descriptions are partial. Optional additions/recipe substitutions
    # are excluded so e.g. an optional 현미죽 is not assigned to the default 죽.
    content = re.split(r"\[.*?변경|※.*?(?:변경|추가)|옵션", raw)[0]
    # Short syllables are whole lexical items: 김선생/김밥/육수당 are not 김,
    # 밤새 is not chestnut, 파프리카 is not green onion.
    return "|".join(sorted({term for term in INGREDIENT_TERMS if
        (re.search(r"(?<![가-힣])"+re.escape(term)+r"(?![가-힣])",content) if len(term)==1 else term in content)}))


def source_date(raw):
    cleaned = text(raw)
    found = re.search(r"(20\d\d)\s*(?:년|[.])\s*(\d{1,2})\s*(?:월|[.])\s*(\d{1,2})?", cleaned[:650])
    return f"{found[1]}-{int(found[2]):02}" + (f"-{int(found[3]):02}" if found[3] else "") if found else ""


def allergy_table(raw):
    rows = {}
    for row in document(raw).xpath("//tr"):
        cells = [text(html.tostring(cell, encoding="unicode")) for cell in row.xpath("./td")]
        if len(cells) != 3 or cells[0] == "메뉴명":
            continue
        for name in cells[0].split("/"):
            key = normalize(name)
            value = (cells[1], cells[2], cells[0])
            if key in rows and rows[key] != value:
                raise ValueError(f"Conflicting official allergy table names: {name}")
            rows[key] = value
    return rows


def bon_metadata():
    payload = json.loads((RAW / "bon-details.json").read_text(encoding="utf-8"))
    menus = {row["id"]: row for row in read_csv(SOURCE / "official-menu-snapshot.csv")}
    tables = {key: allergy_table(value) for key, value in payload["allergyTables"].items()}
    output, audit = [], []
    for capture in payload["menus"]:
        menu = menus[capture["menuId"]]
        info = capture.get("originalFields", {})
        table_hash = info.get("allergyTableHash", "")
        table = payload["allergyTables"].get(table_hash, "")
        components = " · ".join(filter(None, [text(info.get(key, "")) for key in ("subExp", "menuFeature", "materialsStory")]))
        known = tables.get(table_hash, {}).get(normalize(menu["name"]))
        declared, cross, exact_name = known if known else ("", "", "")
        # BON DOSIRAK explicitly excludes rice/soup/sides. A declaration of its
        # main dish cannot certify the complete lunchbox.
        partial = "기본구성을 제외" in text(table)
        status = ("PARTIAL_DECLARATION" if partial else "CONFIRMED_LABEL") if declared.strip() else "UNKNOWN"
        date = source_date(table)
        output.append(dict(foodItemId=menu["id"], sourceFoodCode=menu["sourceFoodCode"], brand=menu["brand"], name=menu["name"],
            ingredientText=components, ingredients=ingredient_tokens(components),
            allergenText=declared, allergens=allergens(declared), mayContainAllergens=allergens(cross), crossContactText=cross,
            ingredientStatus="PARTIAL_DESCRIPTION" if components else "UNKNOWN", allergenStatus=status,
            sourceUrl=capture["url"], checkedAt=capture["checkedAt"], sourceDate=date, parserVersion=VERSION,
            evidenceKind="OFFICIAL_MENU_DESCRIPTION_AND_BRAND_ALLERGEN_TABLE", staleCandidate=str(bool(date and date < "2026-04")).lower(), sourceHash=capture.get("sha256", "")))
        audit.append(dict(menuId=menu["id"], brand=menu["brand"], name=menu["name"], detailChecked=True,
            detailSource=capture["url"], matchedAllergenTableName=exact_name, sourceDate=date,
            allergyTableCoverage="MAIN_COURSE_ONLY" if partial else "DECLARED_MENU", nutritionFieldsProvided=False,
            servingFieldsProvided=False, existingAssetExactJoin=None))
    return output, audit


def captured_pages():
    """Offline original responses; later captures replace earlier identical URLs."""
    result = {}
    for filename in ("zero-brand-homepages.json", "zero-menu-pages-responses.json", "zero-menu-round2-responses.json",
                     "menu-detail-round3-responses.json", "quality-round4-responses.json", "quality-existing-menu-trace-responses.json",
                     "quality-round5-responses.json", "quality-round6-responses.json", "quality-round7-responses.json", "quality-round8-responses.json",
                     "quality-round9-responses.json", "quality-round10-responses.json", "quality-round11-responses.json", "quality-round12-responses.json"):
        path = RAW / filename
        if path.exists():
            for row in json.loads(path.read_text(encoding="utf-8-sig")):
                result[(row["brand"], row["url"],json.dumps(row.get("requestBody"),sort_keys=True))] = row
    return sorted(result.values(), key=lambda row: (row["brand"], row["url"]))


def content(node):
    return re.sub(r"\s+", " ", " ".join(node.itertext())).strip()


def first(node, xpath):
    matches = node.xpath(xpath)
    return content(matches[0]) if matches else ""


def number(raw):
    found = re.match(r"^\s*(\d+(?:\.\d+)?)\s*(?:g|mg|kcal|ml|\(|$)", raw, re.I)
    return found[1] if found else ""


def menu_identity(brand, name):
    return "official-quality-" + NAMESPACE[brand] + "-" + hashlib.sha256(normalize(name).encode()).hexdigest()[:12]


def make_metadata(menu, capture, ingredients="", direct="", cross="", status="UNKNOWN", date=""):
    return dict(foodItemId=menu["id"], sourceFoodCode=menu["sourceFoodCode"], brand=menu["brand"], name=menu["name"],
        ingredientText=ingredients, ingredients=ingredient_tokens(ingredients), ingredientStatus="PARTIAL_DESCRIPTION" if ingredients else "UNKNOWN",
        allergenText=direct, allergens=allergens(direct), mayContainAllergens=allergens(cross), crossContactText=cross,
        allergenStatus=status, sourceUrl=capture["url"], checkedAt=capture["checkedAt"], sourceDate=date,
        parserVersion=VERSION, evidenceKind="OFFICIAL_MENU_DETAIL", staleCandidate=str(bool(date and date < "2026-04")).lower(), sourceHash=capture.get("sha256", ""))


def quality_menus():
    menus, nutrition, metadata, exclusions = {}, {}, {}, []

    def add(capture, name, description="", nutrient=None, direct="", cross="", status="UNKNOWN", date=""):
        brand = capture["brand"]
        name = re.sub(r"\s+", " ", name).strip()
        if not name or brand not in NAMESPACE:
            return
        identity = menu_identity(brand, name)
        menu = dict(id=identity, brand=brand, name=name, sourceFoodCode=identity.removeprefix("official-").upper(),
            sourceUrl=capture["url"], sourceDate=date, verifiedAt=capture["checkedAt"], saleState="CURRENT_MENU_LISTED",
            energyKcal="", servingAmount="", servingUnit="")
        # Prefer a product detail over category metadata; identical menus on
        # multiple responsive pages retain one deterministic brand/name identity.
        if description.startswith(name):
            description=description[len(name):].lstrip(" ·")
        candidate = make_metadata(menu, capture, description, direct, cross, status, date)
        previous = metadata.get(identity)
        if previous is None or (bool(direct),len(description)) > (bool(previous["allergenText"]),len(previous["ingredientText"])):
            metadata[identity] = candidate
            menus[identity] = menu
        if nutrient:
            required = ("referenceAmount", "energyKcal")
            if not all(nutrient.get(key) for key in required) or float(nutrient["referenceAmount"]) <= 0:
                exclusions.append(dict(brand=brand,name=name,reason="NUTRITION_BASIS_MISSING",sourceUrl=capture["url"]))
                return
            # Preserve source errors in audit; do not publish internally
            # contradictory energy/macros as usable calorie records.
            if all(nutrient.get(k) for k in ("carbohydrateGrams","proteinGrams","fatGrams")):
                calculated = 4*float(nutrient["carbohydrateGrams"])+4*float(nutrient["proteinGrams"])+9*float(nutrient["fatGrams"])
                if abs(calculated-float(nutrient["energyKcal"])) > max(40, calculated*.2):
                    exclusions.append(dict(brand=brand,name=name,reason="OFFICIAL_ENERGY_MACRO_CONTRADICTION",originalNutrients=nutrient,sourceUrl=capture["url"]))
                    return
            record = dict(menu, **nutrient, unit="g", sourcePage="", saleState="CURRENT_OFFICIAL_NUTRITION_SNAPSHOT")
            if identity in nutrition and any(nutrition[identity].get(k) != record.get(k) for k in ("referenceAmount","energyKcal","carbohydrateGrams","proteinGrams","fatGrams","sodiumMilligrams")):
                raise ValueError(f"Conflicting official nutrition identity {brand} {name}: {nutrition[identity]}, {record}")
            nutrition[identity] = record

    for capture in captured_pages():
        if "text" not in capture or capture["brand"] not in NAMESPACE:
            continue
        brand, url = capture["brand"], capture["url"]
        root = document(capture["text"])
        if brand == "슬로우캘리" and "co_id=menu" in url:
            descriptions = {}
            for item in root.xpath('//*[contains(concat(" ",normalize-space(@class)," ")," menu_item ")]'):
                name = first(item,'.//*[contains(@class,"menu_title bold")]')
                description = first(item,'.//*[contains(@class,"menu_item_move_des02")]')
                if name:
                    descriptions[normalize(name)] = description
            for item in root.xpath('//*[contains(concat(" ",normalize-space(@class)," ")," menu_table_item ")]'):
                values = [content(cell) for cell in item.xpath('./div[contains(@class,"menu_table_td")]')]
                if len(values)!=8 or not all(number(v) for v in values[1:]):
                    raise ValueError("Slowcali nutrition table schema changed")
                base_name = re.sub(r"\s*\([^)]*\)$", "", values[0]).strip()
                add(capture, values[0], descriptions.get(normalize(base_name), ""),
                    dict(category="샐러드·포케",referenceAmount=values[1],energyKcal=values[2],sodiumMilligrams=values[3],
                         carbohydrateGrams=values[4],fatGrams=values[6],proteinGrams=values[7]))
        elif brand == "에그드랍" and "https://eggdrop.com/menu/view.php" in url:
            name = first(root,'//div[@class="menu"]//header/p')
            if not name:
                continue
            description = first(root,'//div[@class="visual"]/p')
            components = first(root,'//div[@class="ingredients"]//div[@class="box"]')
            sections = root.xpath('//*[contains(concat(" ",normalize-space(@class)," ")," accordion-item ")]')
            declared, cross = "", ""
            block=next((node.getparent().getparent().getparent() for node in root.xpath('//strong') if content(node)=="알레르기 정보"),None)
            if block is not None:
                bodies=block.xpath('./div[@class="body"]')
                lines=[re.sub(r"\s+"," ",line).strip() for line in bodies[0].itertext()] if bodies else []
                lines=[line for line in lines if line]
                declared=" ".join(re.sub(r"^알레르기 유발 가능 식재료\s*:?\s*","",line) for line in lines if "혼입 가능성" not in line)
                cross=" ".join(line for line in lines if "혼입 가능성" in line)
            tables = root.xpath('//table[contains(@class,"table")]')
            nutrient=None
            if tables:
                headers=[content(n) for n in tables[0].xpath('.//thead//th')]
                values=[content(n) for n in tables[0].xpath('.//tbody//td')]
                if headers[:5] != ["영양소","중량","열량","당","단백질"] or len(values)!=7:
                    raise ValueError("Eggdrop nutrition table schema changed")
                # Saturated fat is deliberately not imported as total fat.
                if re.fullmatch(r"\d+(?:\.\d+)?\s*g",values[0]):
                    nutrient=dict(category="토스트·샌드위치",referenceAmount=number(values[0]),energyKcal=number(values[1]),
                        proteinGrams=number(values[3]),carbohydrateGrams="",fatGrams="",sodiumMilligrams=number(values[5]))
                else:
                    exclusions.append(dict(brand=brand,name=name,reason="WEIGHT_UNIT_NOT_DECLARED",originalValues=values,sourceUrl=url))
            add(capture,name," · ".join(filter(None,[description,components])),nutrient,declared,cross,"CONFIRMED_LABEL" if declared else "UNKNOWN")
        elif brand == "써브웨이" and "/menuView/" in url:
            name = first(root,'//*[contains(@class,"prod_name")]/h2') or first(root,'//h2[@class="title"]')
            # Product name is the Korean h2; category labels belong to navigation.
            if not name:
                names=root.xpath('//h2')
                name=next((content(n) for n in names if re.search('[가-힣]',content(n))),"")
            tables=root.xpath('//table')
            if not name or not tables:
                continue
            headers=[content(n) for n in tables[0].xpath('.//thead//th')]
            value_rows=[[content(n) for n in tr.xpath('./td')] for tr in tables[0].xpath('.//tbody/tr')]
            if headers[:3]!=["중량(g)","열량(kcal)","단백질(g)"] or any(len(values)!=6 for values in value_rows):
                raise ValueError(f"Subway nutrition table schema changed: {url}, {headers}, {value_rows}")
            # Salad names and sandwich names share titles but have distinct
            # official product IDs and nutrient bases. Preserve the category.
            category=urlsplit(url).path.split('/')[-1]
            if category in ("salad","grain_salad") and "샐러드" not in name:
                name += " 그레인 샐러드" if category=="grain_salad" else " 샐러드"
            description=first(root,'//*[contains(@class,"summary")]')
            for values in value_rows:
                variant_name=name
                weight=values[0]
                if ':' in weight:
                    explicit_variant,weight=weight.rsplit(':',1)
                    variant_name=explicit_variant
                    if category in ("salad","grain_salad") and "샐러드" not in variant_name:
                        variant_name += " 그레인 샐러드" if category=="grain_salad" else " 샐러드"
                add(capture,variant_name,description,dict(category=category,referenceAmount=number(weight),energyKcal=number(values[1]),
                    carbohydrateGrams="",proteinGrams=number(values[2]),fatGrams="",sodiumMilligrams=number(values[5])))
        elif brand == "국수나무" and "/food/food.php" in url:
            for item in root.xpath('//*[contains(concat(" ",normalize-space(@class)," ")," food-info ")]'):
                add(capture,first(item,'./h3'),first(item,'./div[@class="txt"]'))
        elif brand == "일미리금계찜닭" and "/html/menu.php" in url:
            for node in root.xpath('//p[contains(@class,"menu_name")]'):
                add(capture,content(node),first(node.getparent(),'.//p[contains(@class,"menu_sl_txt")]'))
        elif brand == "육수당" and "board=menu_01" in url:
            for node in root.xpath('//p[contains(@class,"menu_name")]'):
                add(capture,content(node),first(node.getparent(),'.//*[contains(@class,"menu_desc") or contains(@class,"menu_txt")]'))
        elif brand == "이화수전통육개장" and "bo_table=menu" in url:
            for item in root.xpath('//li[contains(@class,"gall_li")]'):
                add(capture,first(item,'.//div[@class="bo_tit"]'),first(item,'.//*[contains(@class,"gall_text_href")]'))
        elif brand == "죠스떡볶이" and "/menu/" in url:
            for item in root.xpath('//a[contains(@href,"menu/view.html")]|//*[contains(@class,"menu_detail")]'):
                name=first(item,'.//h3[contains(@class,"tit")]')
                if name:
                    add(capture,name,first(item,'.//p[@class="desc"]'))
            if "/view.html" in url:
                add(capture,first(root,'//h3[contains(@class,"tit")]'),first(root,'//p[contains(@class,"desc")]'))
        elif brand == "바르다김선생" and "/menu/view.html" in url:
            for item in root.xpath('//div[@class="slick-item"]'):
                add(capture,first(item,'.//div[@class="tit"]'),first(item,'.//div[@class="desc"]'))
        elif brand == "스쿨푸드" and "freewebclub.com/menu/menu.html" in url:
            for node in root.xpath('//p[@class="tit"]'):
                parent=node.getparent()
                if parent.xpath('.//p[@class="des"]') and not content(node) in ("브랜드소개","창업문의","매장안내","고객의 소리"):
                    description=first(parent,'.//p[@class="des"]')
                    if "스쿨푸드의 다양한 소식" not in description and "대표 메뉴들을" not in description:
                        add(capture,content(node),description)
        elif brand in ("홍콩반점0410","역전우동0410") and "/theborn_brand/" in url:
            for item in root.xpath('//div[@class="menu_info"]'):
                add(capture,first(item,'./div[@class="name"]/p')," ".join(content(n) for n in item.xpath('./p[not(@class)]')))
        elif brand == "이비가짬뽕" and "/page/sub2_" in url:
            for node in root.xpath('//p[@class="m-tit"]'):
                add(capture,content(node),first(node.getparent(),'.//p[contains(@class,"txt") or contains(@class,"desc")]'))
        elif brand == "춘리마라탕" and "/menu/" in url:
            for node in root.xpath('//h3')[:4]:
                add(capture,content(node),first(node.getparent(),'./p'),date="2026-09-11")
        elif brand == "죽이야기" and urlsplit(url).path == "/15":
            for node in root.xpath('//*[starts-with(@id,"caption_")]/h4'):
                add(capture,content(node),first(node.getparent(),'./p'))
        elif brand == "현대옥" and urlsplit(url).path == "/menu":
            for node in root.xpath('//h4'):
                name=content(node)
                if name != "메뉴":
                    add(capture,name," ".join(content(n) for n in node.getparent().xpath('./dl|./dd')))
        elif brand == "원할머니보쌈" and "/bossam/menu.asp" in url:
            for item in root.xpath('//div[@class="info"]'):
                add(capture,first(item,'./strong'),first(item,'./p'))
        elif brand == "소담촌" and "hid=allMenu" in url:
            for item in root.xpath('//div[@class="main_allMenu_textbox"]'):
                name=first(item,'./h3') or first(item,'./h3/following-sibling::p[1]')
                add(capture,name," ".join(content(n) for n in item.xpath('./h4|./h4/following-sibling::p')))
        elif brand == "봉추찜닭" and "go=page1.0" in url:
            for item in root.xpath('//div[@class="menu_text"]'):
                add(capture,first(item,'./h2'),first(item,'./p'))
        elif brand == "탕화쿵푸" and "/brand/index.php" in url:
            for node in root.xpath('//h5'):
                add(capture,content(node))
        elif brand == "두끼" and "/menu/trend" in url:
            for node in root.xpath('//h6'):
                # Official named two-person buffet recipes, never a fixed
                # weighed serving or a complete allergen declaration.
                name=" ".join(str(value).strip() for value in node.xpath('./text()')).strip()
                item=node
                while item.getparent() is not None and item.tag!='li':
                    item=item.getparent()
                components=" · ".join(content(tr) for tr in item.xpath('.//tr[th="소스" or th="재료"]'))
                add(capture,name,components)
        elif brand == "포케올데이" and "/menu_balance_box" in url:
            for item in root.xpath('//*[contains(concat(" ",normalize-space(@class)," ")," bh_item ")]'):
                name = first(item,'.//div[@class="bh_title"]//span')
                labels = {first(node,'./p'):first(node,'./span') for node in
                          item.xpath('.//div[@class="info"]/div')}
                mass = labels.get("중량", "")
                nutrient = None
                if re.fullmatch(r"\d+(?:\.\d+)?g", mass) and all(
                    number(labels.get(label, "")) for label in ("칼로리", "탄수화물", "단백질", "지방")):
                    nutrient = dict(category="샐러드·포케", referenceAmount=number(mass),
                        energyKcal=number(labels["칼로리"]), carbohydrateGrams=number(labels["탄수화물"]),
                        proteinGrams=number(labels["단백질"]), fatGrams=number(labels["지방"]), sodiumMilligrams="")
                add(capture,name,nutrient=nutrient)
        elif brand == "포케올데이" and urlsplit(url).path == "/nutrition":
            # These are published calculator ingredients or named finished
            # products. kcal without a declared mass/unit remains menu-only.
            for select in root.xpath('//select[@id="side_menu" or @id="drink" or @id="main_menu" or @id="menu"]'):
                if select.get("id")=="main_menu":
                    continue
                for option in select.xpath('./option[@data-kcal]'):
                    name=re.sub(r"\s*\([\d.]+\s*Kcal\)$","",content(option),flags=re.I)
                    add(capture,name)
        elif brand == "청년다방" and "/menu_view.html" in url:
            for node in root.xpath('//p[contains(@class,"side_li_ttl")]|//p[contains(@class,"view_pg_ttl")]'):
                name=content(node)
                description=first(node.getparent(),'.//p[@class="side_li_txt"]')
                if not description and "view_pg_ttl" in node.get("class",""):
                    description=" ".join(content(n) for n in root.xpath('//p[@class="view_txt"]')[:1])
                add(capture,name,description)
        elif brand == "역전우동0410" and "udon0410.com/menu/" in url:
            for node in root.xpath('//h2'):
                add(capture,content(node),first(node.getparent(),'./p'))
        elif brand == "핵밥" and urlsplit(url).path == "/main":
            for node in root.xpath('//*[starts-with(@id,"caption_")]/h4'):
                if re.match(r"^\[.+?\]",content(node)):
                    add(capture,re.sub(r"^\[.+?\]\s*","",content(node)))
        elif brand == "홍익돈까스" and "/menu_2021_" in url:
            # Names are explicitly styled bold; price paragraphs and sub-brand
            # navigation are excluded rather than classified by food words.
            for node in root.xpath('//p[.//span[@text-style-option="fontStyleBold"]]'):
                name=content(node)
                if re.search('[가-힣]',name) and not re.search(r"원|변경|시즌|메뉴|포장|가맹|홍익돈까스만",name) and len(name)<40:
                    add(capture,name)
    # Offline, explicitly reviewed source image labels. The hashes prevent a
    # silent menu-image revision from reusing an old transcription.
    for row in json.loads((SOURCE / "reviewed-image-menu-transcriptions.json").read_text(encoding="utf-8")):
        if hashlib.sha256((SOURCE / row["path"]).read_bytes()).hexdigest().upper()!=row["sha256"]:
            raise ValueError("Reviewed official menu image hash changed")
        capture=dict(brand=row["brand"],url=row["sourceUrl"],checkedAt=row["checkedAt"],sha256=row["sha256"])
        for name in row["name"].split(" / "):
            add(capture,name,row["ingredientText"],direct=row["allergenText"],cross=row["crossContactText"],status=row["allergenStatus"],date=row["sourceDate"])
    table=json.loads((SOURCE / "reviewed-subway-allergen-transcriptions.json").read_text(encoding="utf-8"))
    if hashlib.sha256((SOURCE / table["path"]).read_bytes()).hexdigest().upper()!=table["sha256"]:
        raise ValueError("Reviewed official Subway allergen image hash changed")
    exact={normalize(row["name"]):row for row in table["rows"]}
    for row in metadata.values():
        if row["brand"]!="써브웨이":
            continue
        label=exact.get(normalize(row["name"]))
        if label:
            row.update(allergenText=label["direct"],allergens=allergens(label["direct"]),allergenStatus="CONFIRMED_LABEL",
                crossContactText=label["mayContain"],mayContainAllergens=allergens(label["mayContain"]),
                sourceUrl=table["sourceUrl"],sourceHash=table["sha256"],sourceDate=table["sourceDate"],
                evidenceKind="OFFICIAL_FIXED_RECIPE_ALLERGEN_CHART_WHEAT_BREAD_BASIS")
            row["ingredientText"]+=" · 공식 알레르기표 기준 구성: 양상추, 토마토, 오이, 양파, 피망, 위트빵"
            row["ingredients"]=ingredient_tokens(row["ingredientText"])
            row["ingredientStatus"]="PARTIAL_DESCRIPTION"
    (SOURCE / "quality-nutrition-exclusions.json").write_text(json.dumps(exclusions,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    return sorted(menus.values(),key=lambda row:row["id"]), sorted(nutrition.values(),key=lambda row:row["id"]), sorted(metadata.values(),key=lambda row:row["foodItemId"])


def finish_audits(rows, bon_audit, quality_menus, nutrition):
    """Trace every original menu ID; missing source fields remain explicitly unknown."""
    catalog = parse_catalog((ROOT / CATALOG).read_text(encoding="utf-8-sig"))
    captures = captured_pages()
    original = read_csv(SOURCE / "official-menu-snapshot.csv") + read_csv(SOURCE / "additional-menu-snapshot.csv")
    legacy = []
    for brand in catalog:
        for name in brand["references"]:
            identity = "official-menu-" + normalize(brand["name"]) + "-" + normalize(name)
            legacy.append(dict(id=identity, brand=brand["name"], name=name, sourceFoodCode=identity.upper(),
                sourceUrl=brand["officialUrl"], sourceDate="", verifiedAt="2026-09-27", saleState="LEGACY_MENU_REFERENCE",
                energyKcal="", servingAmount="", servingUnit=""))
    write_csv(SOURCE / "legacy-menu-snapshot.csv", MENU_HEADERS, legacy)
    original += legacy
    assert len(original) == 578 and len({r["id"] for r in original}) == 578
    metadata = {r["foodItemId"]: r for r in rows}
    original_bon = {r["menuId"]: r for r in bon_audit}
    original_audit = []
    foods = sum((read_csv(ROOT / "app/src/main/assets/fooddata" / filename)
                 for filename in ("food_items.csv", "product_items.csv", "franchise_official_items.csv")), [])
    exact_foods = {}
    for food in foods:
        exact_foods.setdefault((food["brand"], normalize(food["name"])), []).append(food)
    for menu in original:
        record = metadata.get(menu["id"])
        checked = [r for r in captures if r["brand"] == menu["brand"]]
        successful = [r for r in checked if r.get("text")]
        if record is None:
            capture = next((r for r in successful if r["url"] == menu["sourceUrl"]), None)
            capture = capture or (successful[0] if successful else None)
            capture = capture or dict(url=menu["sourceUrl"], checkedAt=menu["verifiedAt"], sha256="")
            record = make_metadata(menu, capture)
            record["evidenceKind"] = "OFFICIAL_MENU_FIELD_AVAILABILITY_REVIEW"
            rows.append(record)
            metadata[menu["id"]] = record
        exact = exact_foods.get((menu["brand"], normalize(menu["name"])), [])
        detail = original_bon.get(menu["id"], {})
        reasons = []
        if detail:
            reasons.append("BON_DETAIL_DOES_NOT_PUBLISH_NUTRITION_OR_SERVING_MASS")
        elif menu["brand"] == "신전떡볶이":
            reasons.append("CURRENT_MENU_KCAL_HAS_NO_BASIS;TWO_HISTORIC_100G_REPORTS_KEPT_SEPARATELY")
        elif menu["brand"] == "한솥":
            reasons.append("PUBLIC_MENU_LIST_HAS_NO_LINKABLE_NUTRITION;OFFICIAL_LIST_API_HTTP403")
        else:
            reasons.append("REVIEWED_PUBLIC_MENU_SOURCE_HAS_NO_EXACT_MENU_NUTRITION_BASIS_AND_MASS")
        original_audit.append(dict(menuId=menu["id"], brand=menu["brand"], name=menu["name"],
            sourceFoodCode=menu["sourceFoodCode"], checkedAt=record["checkedAt"], sourceUrl=record["sourceUrl"],
            sourceHash=record["sourceHash"], reviewedPublicUrls=sorted({r["url"] for r in checked}),
            ingredientStatus=record["ingredientStatus"], allergenStatus=record["allergenStatus"],
            ingredientTokens=record["ingredients"], directAllergens=record["allergens"],
            crossContactAllergens=record["mayContainAllergens"], nutritionFieldsProvided=False,
            servingFieldsProvided=False, exactExistingFoodIds=[r["id"] for r in exact],
            nutritionResolution="UNRESOLVED", reason=";".join(reasons),
            exactMenuAllergenTableMatch=detail.get("matchedAllergenTableName", "")))
    # Current official menu labels may enrich an existing same-brand/name food
    # identity, without changing that food's nutrition basis, ID or source code.
    joined = []
    for record in rows:
        if not (record["ingredients"] or record["allergens"] or record["mayContainAllergens"]):
            continue
        for food in exact_foods.get((record["brand"], normalize(record["name"])), []):
            if food["id"] == record["foodItemId"]:
                continue
            joined.append(dict(record, foodItemId=food["id"], sourceFoodCode=food["sourceFoodCode"],
                name=food["name"], evidenceKind=record["evidenceKind"] + "_EXACT_BRAND_NAME_JOIN"))
    rows += joined
    baseline = json.loads((SOURCE / "quality-baseline.json").read_text(encoding="utf-8"))
    brand_review = []
    for brand in baseline["zeroMenuBrands"]:
        checked = [r for r in captures if r["brand"] == brand]
        names = [r for r in quality_menus if r["brand"] == brand]
        nutrients = [r for r in nutrition if r["brand"] == brand]
        brand_review.append(dict(brand=brand, checkedAt="2026-10-04", addedExactMenuCount=len(names),
            addedRecordableNutritionCount=len(nutrients), result="OFFICIAL_MENUS_IMPORTED" if names else "UNRESOLVED",
            reason="Official named menu cards/details/images imported; only explicit nutrient basis is recordable."
                if names else "Official nolboo.co.kr homepage/menu URL returned HTTP404 or timed out; no usable authoritative menu response. No menu values invented.",
            reviewedSources=[dict(url=r["url"], resolvedUrl=r.get("resolvedUrl", ""), status=r.get("status"),
                error=r.get("error", ""), sha256=r.get("sha256", ""), checkedAt=r["checkedAt"]) for r in checked]))
    (SOURCE / "zero-brand-investigation.json").write_text(json.dumps(brand_review, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (SOURCE / "menu-information-audit.json").write_text(json.dumps(original_audit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return rows


if __name__ == "__main__":
    rows, audit = bon_metadata()
    menu_rows, nutrient_rows, other_metadata = quality_menus()
    write_csv(SOURCE / "quality-menu-snapshot.csv", MENU_HEADERS, [row for row in menu_rows if row["id"] not in {item["id"] for item in nutrient_rows}])
    write_csv(SOURCE / "quality-nutrition.csv", NUTRITION_HEADERS, nutrient_rows)
    rows += other_metadata
    rows = finish_audits(rows, audit, menu_rows, nutrient_rows)
    write_csv(SOURCE / "food-metadata-evidence.csv", METADATA_HEADERS, sorted(rows, key=lambda row: (row["foodItemId"], row["sourceUrl"])))
    print(f"BON: metadata {len(rows)}, ingredient tokens {sum(bool(row['ingredients']) for row in rows)}, explicit allergen matches {sum(row['allergenStatus'] != 'UNKNOWN' for row in rows)}")
    print(f"Quality expansion: {len(menu_rows)} exact menu identities, {len(nutrient_rows)} official nutrition entries")
