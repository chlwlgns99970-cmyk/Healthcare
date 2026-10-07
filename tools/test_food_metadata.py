import unittest
import generate_food_metadata as metadata

class FoodMetadataParserTest(unittest.TestCase):
    def test_complete_ingredient_text_and_status_survive_later_partial_composition(self):
        row=metadata.base_metadata(self.food())
        text='원액두유99.9%[대두고형분10%이상, 외국산(미국,캐나다,러시아등)], 식염'
        metadata.apply_evidence(row,{'ingredientText':text,'ingredients':'원액두유99.9%|식염',
            'ingredientStatus':'COMPLETE_DECLARATION','sourceUrl':'https://manufacturer.example/label'})
        metadata.apply_evidence(row,{'ingredientText':'공식 설명 일부','ingredientStatus':'PARTIAL_DESCRIPTION'})
        self.assertEqual(text,row['completeIngredientText'])
        self.assertEqual(text,row['ingredientText'])
        self.assertEqual('COMPLETE_DECLARATION',row['ingredientStatus'])

    def test_cross_contact_original_is_preserved_separately_from_positive_causes(self):
        row=metadata.base_metadata(self.food())
        metadata.apply_evidence(row,{'allergens':'대두','allergenStatus':'CONFIRMED_LABEL','allergenText':'대두 함유',
            'crossContactText':'우유, 토마토, 복숭아와 같은 시설','mayContainAllergens':'우유',
            'sourceUrl':'https://manufacturer.example/label'})
        self.assertEqual('대두 함유',row['allergenText'])
        self.assertEqual('우유, 토마토, 복숭아와 같은 시설',row['crossContactText'])
        self.assertEqual('대두',row['allergens']); self.assertEqual('우유',row['mayContainAllergens'])

    def test_serving_evidence_requires_positive_mass_units_and_source(self):
        row=metadata.base_metadata(self.food())
        evidence={'householdUnit':'개','basisAmountPerUnit':'12','basisUnit':'g',
            'servingSourceReference':'https://fdc.nal.usda.gov/food-details/167762/nutrients',
            'servingEvidenceKind':'VERIFIED_CONVERSION','servingSourceSize':'large'}
        metadata.apply_evidence(row,evidence)
        self.assertEqual('12',row['basisAmountPerUnit']); self.assertEqual('large',row['servingSourceSize'])
        for invalid in ('0','-1','nan'):
            with self.assertRaises(AssertionError): metadata.apply_evidence(row,evidence|{'basisAmountPerUnit':invalid})
    def food(self, identity='f1', brand='본죽'):
        return dict(id=identity, sourceType='K-FIND', sourceFoodCode=identity, name='테스트',
            normalizedName='테스트', brand=brand, servingDescription='100g 기준', dataVersion='2026-08-28')

    def test_ingredient_parser_preserves_composite_and_no_guess(self):
        self.assertEqual({'대두 10g','소스(원재료 미표시)'}, metadata.ingredient_tokens('대두 10g,소스(원재료 미표시)'))
        self.assertEqual(set(),metadata.ingredient_tokens('UNKNOWN'))

    def test_allergen_positive_normalization_and_token_boundaries(self):
        self.assertEqual({'달걀','대두','견과류','생선'},metadata.allergen_tokens('난류, 대두, 잣, 고등어'))
        self.assertEqual(set(),metadata.allergen_tokens('게살향료가없는표기 밀크티향료 설명'))

    def test_raw_fields_are_not_ingredients_allergens_or_serving_conversion(self):
        row=metadata.base_metadata(self.food(),{'품목제조보고번호':'12345','식품중량':'400ml','대표식품명':'김밥','1회 섭취참고량':'210g'})
        self.assertEqual('12345',row['productReportNumber'])
        self.assertEqual('210g',row['intakeReference'])
        self.assertEqual('400ml',row['packageSize'])
        self.assertEqual('UNKNOWN',row['allergenStatus'])
        self.assertEqual('',row['ingredients'])
        self.assertEqual('',row['foodGroups'])

    def test_unknown_is_not_confirmed_empty_declaration(self):
        row=metadata.base_metadata(self.food())
        metadata.apply_evidence(row,{'allergens':'','allergenStatus':'UNKNOWN'})
        self.assertEqual('UNKNOWN',row['allergenStatus'])
        metadata.apply_evidence(row,{'allergens':'','allergenStatus':'CONFIRMED_LABEL','sourceUrl':'https://official.example/allergen'})
        self.assertEqual('CONFIRMED_LABEL',row['allergenStatus'])

    def test_exact_id_brand_conflict_not_merged(self):
        rows, conflicts=metadata.build([self.food()],[],[{'foodItemId':'f1','brand':'본도시락','allergens':'밀','allergenStatus':'CONFIRMED_LABEL'}],[],[],[],[])
        self.assertEqual('UNKNOWN',rows[0]['allergenStatus'])
        self.assertEqual('BRAND_IDENTITY_CONFLICT',conflicts[0]['reason'])

    def test_same_name_distinct_food_codes_never_merge(self):
        rows,_=metadata.build([self.food('one'),self.food('two')],[],[{'foodItemId':'one','brand':'본죽','allergens':'밀','allergenStatus':'CONFIRMED_LABEL'}],[],[],[],[])
        by_id={r['foodItemId']:r for r in rows}
        self.assertEqual('밀',by_id['one']['allergens'])
        self.assertEqual('UNKNOWN',by_id['two']['allergenStatus'])

    def test_wrong_menu_name_cannot_attach_to_exact_id(self):
        rows,conflicts=metadata.build([self.food()],[],[{'foodItemId':'f1','brand':'본죽','name':'다른 메뉴','allergens':'밀','allergenStatus':'CONFIRMED_LABEL'}],[],[],[],[])
        self.assertEqual('UNKNOWN',rows[0]['allergenStatus'])
        self.assertEqual('MENU_NAME_IDENTITY_CONFLICT',conflicts[0]['reason'])

    def test_conflicting_official_declarations_cannot_certify_absence(self):
        facts=[{'foodItemId':'f1','brand':'본죽','allergens':value,'allergenStatus':'CONFIRMED_LABEL'} for value in ('밀','우유')]
        rows,conflicts=metadata.build([self.food()],[],facts,[],[],[],[])
        self.assertEqual('PARTIAL_CONFLICT',rows[0]['allergenStatus'])
        self.assertEqual({'밀','우유'},metadata.tokens(rows[0]['allergens']))
        self.assertEqual('OFFICIAL_DECLARATION_CONFLICT',conflicts[0]['reason'])

    def test_cross_contact_is_separate_and_provenance_kept(self):
        row=metadata.base_metadata(self.food())
        metadata.apply_evidence(row,{'allergens':'대두','mayContainAllergens':'새우','allergenStatus':'CONFIRMED_LABEL',
           'sourceUrl':'https://official.example/menu/1','checkedAt':'2026-10-04','sourceDate':'2025-11-11','staleCandidate':'true','parserVersion':'p1'})
        self.assertEqual('대두',row['allergens']); self.assertEqual('새우',row['mayContainAllergens'])
        self.assertIn('https://official.example/menu/1',row['sourceReference'])
        self.assertEqual('2025-11-11',row['sourceDate']);self.assertEqual('true',row['staleCandidate'])

    def test_repeated_label_cannot_erase_unresolved_official_conflict(self):
        facts=[{'foodItemId':'f1','brand':'본죽','allergens':value,'allergenStatus':'CONFIRMED_LABEL'}
               for value in ('밀','우유','우유')]
        rows,_=metadata.build([self.food()],[],facts,[],[],[],[])
        self.assertEqual('PARTIAL_CONFLICT',rows[0]['allergenStatus'])
        self.assertEqual({'밀','우유'},metadata.tokens(rows[0]['allergens']))

    def test_official_egg_label_uses_existing_canonical_cause(self):
        self.assertIn('달걀',metadata.allergen_tokens('땅콩@대두@우유@알류@밀'))

    def test_later_verified_source_cannot_erase_official_label_conflict(self):
        facts=[{'foodItemId':'f1','brand':'본죽','allergens':value,'allergenStatus':'CONFIRMED_LABEL'}
               for value in ('밀','우유')]
        verified=[{'stableTemplateId':'t','ingredients':'쌀','allergenTags':'우유',
                   'allergenCompleteness':'COMPLETE','sourceUrlOrIdentifier':'https://official.example/other',
                   'sourceName':'공식 자료','verifiedAt':'2026-10-04'}]
        rows,_=metadata.build([self.food()],[],facts,[],verified,[{'mealTemplateId':'t','foodItemId':'f1'}],[])
        self.assertEqual('PARTIAL_CONFLICT',rows[0]['allergenStatus'])

    def test_official_template_source_disagreement_is_audited_without_averaging(self):
        official=[{'foodItemId':'f1','brand':'본죽','allergens':'밀','allergenStatus':'CONFIRMED_LABEL'}]
        verified=[{'stableTemplateId':'t','ingredients':'쌀','allergenTags':'우유',
                   'allergenCompleteness':'COMPLETE','sourceUrlOrIdentifier':'https://official.example/other',
                   'sourceName':'공식 자료','verifiedAt':'2026-10-04'}]
        rows,conflicts=metadata.build([self.food()],[],official,[],verified,[{'mealTemplateId':'t','foodItemId':'f1'}],[])
        self.assertEqual('PARTIAL_CONFLICT',rows[0]['allergenStatus'])
        self.assertEqual({'밀','우유'},metadata.tokens(rows[0]['allergens']))
        self.assertEqual('OFFICIAL_DECLARATION_CONFLICT',conflicts[0]['reason'])

    def test_conflicting_source_food_code_cannot_attach_by_name_and_brand(self):
        official=[{'foodItemId':'f1','brand':'본죽','name':'테스트','sourceFoodCode':'different-code',
                   'allergens':'우유','allergenStatus':'CONFIRMED_LABEL'}]
        rows,conflicts=metadata.build([self.food()],[],official,[],[],[],[])
        self.assertEqual('UNKNOWN',rows[0]['allergenStatus'])
        self.assertEqual('',rows[0]['allergens'])
        self.assertEqual('SOURCE_FOOD_CODE_CONFLICT',conflicts[0]['reason'])

    def test_generated_snapshot_date_is_not_an_official_publication_date(self):
        food=self.food()|{'sourceType':'OFFICIAL-BRAND-NUTRITION','dataVersion':'2026-10-04'}
        row=metadata.base_metadata(food)
        self.assertEqual('',row['sourceDate'])
        self.assertEqual('OFFICIAL-BRAND-NUTRITION:f1',row['sourceReference'])
        metadata.apply_evidence(row,{'sourceUrl':'https://official.example/nutrition',
            'sourceDate':'2018-11-16','staleCandidate':'true'})
        self.assertEqual('2018-11-16',row['sourceDate'])
        self.assertEqual('true',row['staleCandidate'])

    def test_partial_recipe_does_not_certify_allergen_absence(self):
        rows,_=metadata.build([self.food()],[],[],[{'stableTemplateId':'t','ingredients':'쌀|채소','foodGroups':'VEGETABLE',
           'sourceUrl':'https://public.example/recipe/3','sourceName':'공공자료'}],[],[{'mealTemplateId':'t','foodItemId':'f1'}],[])
        self.assertEqual('PARTIAL_DESCRIPTION',rows[0]['ingredientStatus'])
        self.assertEqual('UNKNOWN',rows[0]['allergenStatus'])
        self.assertEqual('REFERENCE_RECIPE_COMPOSITION',rows[0]['foodGroupEvidenceScope'])

    def test_excel_date_normalization(self):
        self.assertEqual('2026-08-28',metadata.normalize_source_date('46262'))

    def test_stable_order_and_identical_inputs(self):
        f=[self.food('b'),self.food('a')]
        first=metadata.build(f,[],[],[],[],[],[])
        self.assertEqual(['a','b'],[r['foodItemId'] for r in first[0]])
        self.assertEqual(first,metadata.build(list(reversed(f)),[],[],[],[],[],[]))

if __name__=='__main__':unittest.main()
