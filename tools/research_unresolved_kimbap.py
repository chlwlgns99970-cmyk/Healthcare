"""Read original K-FIND fields for identity-specific kimbap serving evidence.

This audit never changes nutrition or assumes density / another product's mass.
Run this only when investigating the two unresolved generic records.
"""
from __future__ import annotations

import hashlib
import argparse
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parents[1]
NAMES = {"김밥_계란", "김밥_고추", "계란김밥", "고추김밥"}


def hidden_column_metadata(path: Path) -> list:
    """Inspect original OOXML cols, including columns hidden by the source file."""
    hidden = []
    with zipfile.ZipFile(path) as archive:
        for member in archive.namelist():
            if not member.startswith("xl/worksheets/sheet") or not member.endswith(".xml"):
                continue
            with archive.open(member) as source:
                for event, element in ET.iterparse(source, events=("start",)):
                    tag = element.tag.rsplit("}", 1)[-1]
                    if tag == "sheetData":
                        break
                    if tag == "col" and element.attrib.get("hidden") in {"true", "1"}:
                        hidden.append(dict(sheetXml=member, **element.attrib))
    return hidden


def inspect_workbook(path: Path) -> dict:
    result = {"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest().upper(),
              "rowsScanned": 0, "hiddenColumns": hidden_column_metadata(path), "matchingRows": []}
    book = openpyxl.load_workbook(path, read_only=True, data_only=True)
    try:
        for sheet in book:
            rows = sheet.iter_rows(values_only=True)
            headers = [str(value or "").strip() for value in next(rows)]
            name_index = headers.index("식품명")
            result["allHeaders"] = headers
            for row_number, values in enumerate(rows, 2):
                result["rowsScanned"] += 1
                name = str(values[name_index] or "").replace(" ", "")
                if name not in NAMES:
                    continue
                result["matchingRows"].append({"sheet": sheet.title, "sourceRow": row_number,
                    "fields": {key: value for key, value in zip(headers, values)
                               if key and value is not None}})
    finally:
        book.close()
    return result


def write_decision(audit):
    relevant = {"식품코드", "식품명", "식품기원명", "식품중분류명", "영양성분함량기준량",
        "에너지(kcal)", "단백질(g)", "지방(g)", "탄수화물(g)", "출처명", "1인(회)분량 참고량",
        "1회 섭취참고량", "식품중량", "업체명", "제조사명", "품목제조보고번호",
        "데이터생성방법명", "데이터생성일자", "데이터기준일자"}
    sources = []
    for workbook in audit:
        sources.append(dict(file=workbook["file"], sha256=workbook["sha256"],
            rowsScanned=workbook["rowsScanned"], allColumnsScanned=len(workbook["allHeaders"]),
            hiddenColumns=workbook["hiddenColumns"],
            hiddenColumnInspection="Original OOXML worksheet col hidden=true/1 attributes inspected; all row columns were read regardless of UI visibility.",
            matches=[dict(sheet=row["sheet"], sourceRow=row["sourceRow"],
                fields={key: value for key, value in row["fields"].items() if key in relevant})
                for row in workbook["matchingRows"]]))
    decision = dict(verifiedAt="2026-10-04", originalSources=sources,
        publicSources=[
            dict(url="https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do",
                result="Official K-FIND original snapshot. Exact food codes and both complete source workbooks inspected, including tail serving fields."),
            dict(url="https://www.haushop.co.kr/", result="Manufacturer's official shop confirms 농업회사법인(주)한우물 / 김제시 용지면 백자1길112; manufacturer is unrelated to anonymous generic D401/D501 recipe records."),
            dict(url="https://www.ssg.com/item/itemView.ssg?itemId=1000612560269",
                result="Retailer's original product disclosures match 한우물 계란김밥 230gx5 / 1150g manufacturer identity. Raw standardized intake reference is 210g; neither is a generic roll conversion."),
            dict(url="https://kobongmin.com/renewal/03_menu/02.php",
                result="Official 매운김밥 is a different branded recipe, without a matching anonymous K-FIND food code or a verified serving mass."),
        ], decisions=[
            dict(foodCodes=["D401-007560000-0001", "D401-007030000-0001", "D501-007030000-0001"],
                state="UNRESOLVED_SOLID_VOLUME_BASIS", verifiedServing=None,
                reason="Each original nutrition basis is 100ml, total 400ml, household reference empty, manufacturer absent. No identity-specific g mass, density or explicit roll-count conversion was established. Preserve original nutrition/unit and manual-input fallback; do not borrow 216g or a manufacturer's different product."),
            dict(foodCodes=["P123-203020300-0275"], state="DISTINCT_MANUFACTURER_PRODUCT",
                reason="Same display name alone is insufficient identity. Preserve its 100g/170kcal, total1150g and source metadata; 210g intake reference is not 1줄 mass and 230gx5 retail pack is not a conversion for generic egg/chili rolls.")],
        addedGenericServingConversions=0)
    (ROOT / "data-source/franchise/unresolved-kimbap-research.json").write_text(
        json.dumps(decision, ensure_ascii=False, indent=2, default=str) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cached-audit", action="store_true", help="Reuse completed full scan only after verifying source hashes and actual hidden-column metadata")
    args = parser.parse_args()
    output = ROOT / "app/build/franchise-expansion"
    output.mkdir(parents=True, exist_ok=True)
    if args.cached_audit:
        audit = json.loads((output / "unresolved-original-fields.json").read_text(encoding="utf-8"))
        for row in audit:
            path = ROOT / "data-source/kfind" / row["file"]
            if hashlib.sha256(path.read_bytes()).hexdigest().upper() != row["sha256"]:
                raise ValueError("Original workbook changed; full scan required")
            row["hiddenColumns"] = hidden_column_metadata(path)
        write_decision(audit)
        print("Verified original workbook hashes/hidden-column metadata; wrote identity-specific research decisions.")
        raise SystemExit(0)
    audit = []
    for name in ("kfind-food-db-2026-08-28.xlsx", "kfind-processed-food-db-2026-08-28.xlsx"):
        result = inspect_workbook(ROOT / "data-source/kfind" / name)
        audit.append(result)
        print(name, result["rowsScanned"], len(result["matchingRows"]), flush=True)
        (output / "unresolved-original-fields.json").write_text(
            json.dumps(audit, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    write_decision(audit)
