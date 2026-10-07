"""Focused collector tests; no Android, network, database or nutrition mutations."""
import unittest
from collect_coffee_bakery_metadata import (
    allergen_declaration, bundled_menu_identity, description_ingredients, generate_rows, parse_starbucks_detail, parse_tlj_detail,
)


class CoffeeBakeryMetadataTest(unittest.TestCase):
    def food(self, name="라떼_카페 라떼 핫(HOT) (Tall)"):
        return {"id": "kfind-test", "sourceFoodCode": "K-FIND-ORIGINAL", "brand": "스타벅스", "name": name}

    def official(self, name="카페 라떼", temperature="HOT"):
        return {"officialName": name, "officialCode": "41", "brand": "스타벅스", "temperature": temperature,
                "ingredientText": "신선한 스팀 밀크를 사용한 커피", "allergenText": "우유", "allergens": "우유",
                "allergenStatus": "CONFIRMED_LABEL", "sourceUrl": "https://www.starbucks.co.kr/menu/drink_view.do?product_cd=41",
                "sourceDate": "", "sourceHash": "test-hash"}

    def test_missing_allergen_never_certifies_absence(self):
        self.assertEqual(("", "UNKNOWN"), allergen_declaration(""))
        self.assertEqual(("", "UNKNOWN"), allergen_declaration("-"))
        self.assertEqual(("", "CONFIRMED_LABEL"), allergen_declaration("해당 없음"))
        labels, status = allergen_declaration("밀크티")
        self.assertNotIn("밀", labels.split("|"))
        self.assertNotEqual("CONFIRMED_LABEL", status)

    def test_no_information_statement_is_distinct_from_explicit_no_allergens(self):
        for label in ("알레르기 정보 없음", "알레르기 정보 미제공", "미확인"):
            self.assertEqual(("", "UNKNOWN"), allergen_declaration(label))
        self.assertEqual(("", "CONFIRMED_LABEL"), allergen_declaration("알레르기 유발 요인 없음"))

    def test_declared_aliases_and_unsupported_labels_preserved(self):
        labels, status = allergen_declaration("계란, 우유, 대두, 밀, 호두, 아황산류 함유")
        self.assertEqual({"달걀", "우유", "대두", "밀", "견과류", "아황산류"}, set(labels.split("|")))
        self.assertEqual("CONFIRMED_LABEL", status)
        labels, status = allergen_declaration("대두, 우유, 알류, 밀")
        self.assertIn("달걀", labels.split("|"))
        self.assertEqual("CONFIRMED_LABEL", status)
        labels, status = allergen_declaration("우유, 미해석원료")
        self.assertIn("미해석원료", labels.split("|"))
        self.assertEqual("PARTIAL_UNRESOLVED_DECLARATION", status)

    def test_tlj_only_declared_cell_is_allergen_evidence(self):
        source = ('<span class="name">우유빵</span><div class="p_desc2"><li class="tit">제품설명</li>'
                  '<li>우유향이 풍부한 빵</li></div><tr class="is-allergy"><th>알레르기 정보</th>'
                  '<td>계란, 우유, 밀 함유</td></tr>')
        row = parse_tlj_detail(source, "123")
        self.assertEqual("우유향이 풍부한 빵", row["ingredientText"])
        self.assertEqual({"달걀", "우유", "밀"}, set(row["allergens"].split("|")))
        row = parse_tlj_detail(source.split('<tr class="is-allergy">')[0], "123")
        self.assertEqual("UNKNOWN", row["allergenStatus"])

    def test_starbucks_detail_product_identity_required(self):
        source = 'view: remapView({"PRODUCT_CD":"41","PRODUCT_NM":"카페 라떼","HOT_YN":"Y","ALLERGY":"우유@호두","CONTENT":"제품설명"})'
        row = parse_starbucks_detail(source, "41", "drink")
        self.assertEqual("HOT", row["temperature"])
        self.assertEqual({"우유", "견과류"}, set(row["allergens"].split("|")))
        with self.assertRaises(ValueError):
            parse_starbucks_detail(source, "OTHER-PRODUCT", "drink")

    def test_exact_temperature_and_brand_required_no_fuzzy_matching(self):
        food = self.food("라떼_카페 라떼 아이스(ICED) (Tall)")
        self.assertEqual("아이스카페라떼", bundled_menu_identity(food)[0])
        rows, missing = generate_rows([food], [self.official()])
        self.assertFalse(rows)
        self.assertEqual("NO_EXACT_CURRENT_MENU", missing[0]["reason"])
        wrong_temperature = self.official("아이스 카페 라떼", "HOT")
        self.assertFalse(generate_rows([food], [wrong_temperature])[0])
        wrong_brand = self.official("아이스 카페 라떼", "ICED") | {"brand": "다른브랜드"}
        self.assertFalse(generate_rows([food], [wrong_brand])[0])

    def test_partial_description_never_full_ingredients_or_nutrition_replacement(self):
        rows, _ = generate_rows([self.food()], [self.official()])
        self.assertEqual("PARTIAL_DESCRIPTION", rows[0]["ingredientStatus"])
        self.assertEqual("스팀 밀크|커피", rows[0]["ingredients"])
        self.assertEqual("K-FIND-ORIGINAL", rows[0]["sourceFoodCode"])
        self.assertEqual("라떼_카페 라떼 핫(HOT) (Tall)", rows[0]["name"])
        self.assertEqual("true", rows[0]["staleCandidate"])
        self.assertIn("RECIPE_VERSION_UNVERIFIED", rows[0]["evidenceKind"])
        self.assertEqual("", rows[0]["sourceDate"])

    def test_duplicate_current_menu_does_not_choose_arbitrary_representative(self):
        duplicate = self.official() | {"officialCode": "OTHER"}
        rows, missing = generate_rows([self.food()], [self.official(), duplicate])
        self.assertFalse(rows)
        self.assertEqual("AMBIGUOUS_CURRENT_MENU", missing[0]["reason"])
        first = generate_rows([self.food()], [self.official()])
        self.assertEqual(first, generate_rows([self.food()], [self.official()]))

    def test_literal_description_particles_keep_composites_without_inferred_subingredients(self):
        ingredients = description_ingredients("우유와 바나나를 섞고 아몬드를 올렸어요. 치킨과 오이를 통밀빵에 넣은 제품")
        self.assertEqual({"우유", "바나나", "아몬드", "치킨", "오이", "통밀빵"}, ingredients)
        self.assertNotIn("밀", ingredients)
        self.assertEqual({"연유", "화이트초코"}, description_ingredients("달콤한 연유와 화이트초코로 만든 토핑"))
        self.assertNotIn("우유", description_ingredients("달콤한 연유와 화이트초코로 만든 토핑"))

    def test_flavor_only_and_name_only_do_not_claim_ingredients(self):
        self.assertEqual(set(), description_ingredients("바나나맛, 딸기 향과 베르가못의 풍미를 즐기는 제품"))
        food = self.food("라떼_우유 바나나")
        official = self.official("우유 바나나") | {"ingredientText": "부드럽고 달콤한 제품"}
        rows, _ = generate_rows([food], [official])
        self.assertEqual("", rows[0]["ingredients"])
        self.assertEqual("PARTIAL_DESCRIPTION", rows[0]["ingredientStatus"])


if __name__ == "__main__":
    unittest.main()
