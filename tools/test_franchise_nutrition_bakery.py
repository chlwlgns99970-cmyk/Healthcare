import json
import unittest
from pathlib import Path
from franchise_nutrition_bakery import parse_paris, parse_tlj, official_product_path

FIXTURES=Path(__file__).resolve().parents[1]/'data-source/franchise-sync/fixtures'
def fixture(label):
    return json.loads((FIXTURES/('nutrition-'+label+'-menu.json')).read_text(encoding='utf8')),(FIXTURES/('nutrition-'+label+'-detail.html')).read_bytes()

class BakeryNutritionTest(unittest.TestCase):
    def test_utf8_url_spelling_matches_without_changing_product_identity(self):
        from urllib.parse import quote
        m,p=fixture('paris')
        root=__import__('lxml.html',fromlist=['html']).fromstring(p)
        original=root.xpath('//link[@rel="canonical"]/@href')[0]
        canonical='https://www.paris.co.kr/product/롤치즈빵/'
        p=p.replace(original.encode(),canonical.encode())
        m['externalId']=quote('/product/롤치즈빵').lower()
        f=parse_paris(m,p)
        self.assertEqual(18,f['proteinGrams']);self.assertEqual(820,f['sodiumMilligrams'])
        self.assertIsNone(f['carbohydrateGrams']);self.assertIsNone(f['fatGrams'])
        self.assertEqual(official_product_path(canonical),official_product_path(m['externalId']))
        with self.assertRaises(AssertionError):parse_paris(dict(m,externalId='/product/다른제품'),p)
        with self.assertRaises(AssertionError):parse_paris(dict(m,name='다른제품'),p)
    def test_reserved_delimiters_are_not_decoded_into_another_product_path(self):
        self.assertNotEqual(official_product_path('/product/a%2fb'),official_product_path('/product/a/b'))
        self.assertNotEqual(official_product_path('/product/a%3fb'),official_product_path('/product/a'))
    def test_external_product_id_query_or_foreign_canonical_is_rejected(self):
        m,p=fixture('paris')
        for suffix in ('?variant=other','#other'):
            with self.assertRaises(AssertionError):parse_paris(dict(m,externalId=m['externalId']+suffix),p)
        with self.assertRaises(AssertionError):parse_paris(m,p.replace(b'www.paris.co.kr',b'other.example'))
    def test_paris_published_total_basis(self):
        m,p=fixture('paris'); f=parse_paris(m,p)
        self.assertEqual((f['energyKcal'],f['servingAmount'],f['proteinGrams'],f['sodiumMilligrams']),(875,240,18,820))
        self.assertIsNone(f['fatGrams']);self.assertIsNone(f['carbohydrateGrams'])
    def test_tlj_published_basis_not_hardcoded_summary(self):
        m,p=fixture('tlj'); f=parse_tlj(m,p)
        self.assertEqual((f['energyKcal'],f['servingAmount'],f['proteinGrams'],f['sodiumMilligrams']),(190,70,5,160))
        self.assertIsNone(f['fatGrams']);self.assertIsNone(f['carbohydrateGrams'])
    def test_per100_not_whole_package_weight(self):
        m,p=fixture('paris-per100');f=parse_paris(m,p)
        self.assertEqual((f['energyKcal'],f['servingAmount'],f['proteinGrams']),(335,100,11))
    def test_serving_calories_not_whole_cake_mass(self):
        m,p=fixture('paris-serving');f=parse_paris(m,p)
        self.assertEqual(f['energyKcal'],370);self.assertEqual(1,f['servingAmount']);self.assertEqual('인분',f['servingUnit'])
    def test_less_than_is_not_an_exact_protein_value(self):
        m,p=fixture('paris');p=p.replace('단백질(g): 18'.encode(),'단백질(g): 1g 미만'.encode())
        self.assertIsNone(parse_paris(m,p)['proteinGrams'])
    def test_impossible_published_kcal_unit_is_withheld(self):
        m,p=fixture('paris-per100');p=p.replace(b': 335',b': 1995')
        with self.assertRaisesRegex(AssertionError,'physical upper bound'):parse_paris(m,p)
    def test_product_identity_rejected(self):
        for label,parser in [('paris',parse_paris),('tlj',parse_tlj)]:
            m,p=fixture(label);m['externalId']='wrong'
            with self.assertRaises(AssertionError):parser(m,p)
    def test_variant_or_truncated_title_rejected(self):
        for label,parser in [('paris',parse_paris),('tlj',parse_tlj)]:
            m,p=fixture(label);m['name']=m['name'][:3]+'...'
            with self.assertRaises(AssertionError):parser(m,p)
    def test_missing_weight_not_zero_or_assumed(self):
        m,p=fixture('tlj');p=p.replace(b'70',b'')
        f=parse_tlj(m,p);self.assertIsNone(f['servingAmount'])
    def test_explicit_zero_kcal_preserved(self):
        m,p=fixture('paris');p=p.replace(b': 875',b': 0')
        self.assertEqual(parse_paris(m,p)['energyKcal'],0)
class ParisInlineNutritionTest(unittest.TestCase):
    # Minimal contract from manager public GET, 2026-10-14, HTTP 200.
    # Response SHA256: 262954df8a46916122ad02bdf20df8499f15b0fd111310d853bb5d60ee8412d0
    menu = dict(brand='파리바게뜨', externalId='/product/체다치즈와-고다치즈를-더한-롤치즈빵/',
                name='체다치즈와 고다치즈를 더한 롤치즈빵')
    payload = '''<link rel="canonical" href="https://www.paris.co.kr/product/체다치즈와-고다치즈를-더한-롤치즈빵/">
    <h1 class="product-name">체다치즈와 고다치즈를 더한 롤치즈빵</h1>
    <div class="product-info-group product-nutrition" data-test='31'>
    <h4 class="product-info-group-title">영양정보</h4><div class="product-info-group-description">
    <p>총 내용량: 135g &middot; 총 내용량당 칼로리(kcal): 420</p>
    <p>총 내용량당 - 나트륨(mg): 750 &middot; 당류(g): 9 &middot; 포화지방(g): 5 &middot; 단백질(g): 13</p>
    </div></div>'''

    def test_verified_optional_fields_and_existing_values(self):
        f = parse_paris(self.menu, self.payload)
        self.assertEqual((420, 135, 'g', 750, 13, 9, 5), tuple(f[k] for k in
                         ('energyKcal', 'servingAmount', 'servingUnit', 'sodiumMilligrams',
                          'proteinGrams', 'sugarsGrams', 'saturatedFatGrams')))
        self.assertEqual('공식 총 내용량 기준', f['servingDescription'])
        self.assertIsNone(f['carbohydrateGrams'])
        self.assertIsNone(f['fatGrams'])
        f = parse_paris(self.menu, self.payload.replace('</p>', ' &middot; 탄수화물(g): 20 &middot; 지방(g): 10</p>', 1))
        self.assertEqual((20, 10, 9, 5), tuple(f[k] for k in
                         ('carbohydrateGrams', 'fatGrams', 'sugarsGrams', 'saturatedFatGrams')))

    def test_optional_fields_independently_missing_zero_or_inexact(self):
        for label, field, value, other, other_value in (
                ('당류', 'sugarsGrams', 9, 'saturatedFatGrams', 5),
                ('포화지방', 'saturatedFatGrams', 5, 'sugarsGrams', 9)):
            for replacement, expected in (('', None), (label+'(g): 0', 0),
                                          (label+'(g): 1g 미만', None)):
                with self.subTest(field=field, replacement=replacement):
                    f = parse_paris(self.menu, self.payload.replace(label+'(g): '+str(value), replacement))
                    self.assertEqual(expected, f[field])
                    self.assertEqual(other_value, f[other])
                    self.assertIsNone(f['fatGrams'])
                    self.assertIsNone(f['carbohydrateGrams'])
        f = parse_paris(self.menu, self.payload.replace('당류(g): 9', '').replace('포화지방(g): 5', ''))
        self.assertIsNone(f['sugarsGrams'])
        self.assertIsNone(f['saturatedFatGrams'])

    def test_identity_rejection(self):
        for changes in (dict(externalId='/product/다른제품/'), dict(name='롤치즈빵'),
                        dict(externalId=self.menu['externalId']+'?variant=other')):
            with self.subTest(changes=changes), self.assertRaises(AssertionError):
                parse_paris(dict(self.menu, **changes), self.payload)
        with self.assertRaises(AssertionError):
            parse_paris(self.menu, self.payload.replace('www.paris.co.kr', 'other.example'))

    def test_basis_checks_preserved(self):
        without_energy = self.payload.replace('총 내용량당 칼로리(kcal): 420', '칼로리 미공개')
        with self.assertRaises(AssertionError):
            parse_paris(self.menu, without_energy)
        per100 = self.payload.replace('총 내용량당 칼로리(kcal): 420', '100g당 칼로리(kcal): 420')
        self.assertEqual(100, parse_paris(self.menu, per100)['servingAmount'])
        with self.assertRaisesRegex(AssertionError, 'physical upper bound'):
            parse_paris(self.menu, per100.replace(': 420', ': 1995'))
        missing_weight = self.payload.replace('총 내용량: 135g', '총 내용량 미공개')
        self.assertIsNone(parse_paris(self.menu, missing_weight)['servingAmount'])

class ParisSecondTemplateInlineTest(unittest.TestCase):
    # Supplied public excerpt only; no independent acquisition or SHA verification.
    # Supplied raw SHA256:
    # b88a5589f5853d0cdea32ddc53b43fe2a086b4406b5e73e06ea992d323883f8a
    menu = dict(brand='파리바게뜨', name='초코반딸기반케이크 (시즌과일)',
                externalId='/product/new-%ec%b4%88%ec%bd%94%eb%b0%98%eb%94%b8%ea%b8%b0%eb%b0%98%ec%bc%80%ec%9d%b4%ed%81%ac')
    canonical = '<link rel="canonical" href="https://www.paris.co.kr/product/new-초코반딸기반케이크/">'
    heading = '<h1 class="">초코반딸기반케이크 (시즌과일)</h1><div class="excerpt">제품 설명</div>'
    panel = '''<div class="product-info">
    <p>총 내용량: 690g · 100g당 칼로리(kcal): 310</p>
    <p>100g당 - 나트륨(mg): 80 · 당류(g): 18 · 포화지방(g): 12 · 단백질(g): 4</p>
    </div>'''

    def payload(self, headings=None, panels=None):
        return self.canonical + '<h1 class="brand">파리바게뜨</h1>' + (
            self.heading * 2 if headings is None else headings) + (
            self.panel if panels is None else panels)

    def test_supplied_desktop_mobile_titles_and_per100_values(self):
        f = parse_paris(self.menu, self.payload())
        self.assertEqual((310, 4, 80, 18, 12, 100, 'g'), tuple(f[k] for k in (
            'energyKcal', 'proteinGrams', 'sodiumMilligrams', 'sugarsGrams',
            'saturatedFatGrams', 'servingAmount', 'servingUnit')))
        self.assertEqual('공식 100g 기준', f['servingDescription'])
        self.assertIsNone(f['carbohydrateGrams'])
        self.assertIsNone(f['fatGrams'])
        self.assertEqual(100, parse_paris(self.menu, self.payload(
            panels=self.panel.replace('690g', '1200g')))['servingAmount'])

    def test_one_or_two_normalized_titles_and_bounded_count(self):
        normalized = self.heading.replace('(시즌과일)', '（ 시즌과일 ）').replace('케이크 ', '케이크\n ')
        for headings in (self.heading, self.heading + normalized):
            with self.subTest(headings=headings):
                self.assertEqual(310, parse_paris(self.menu, self.payload(headings=headings))['energyKcal'])
        for headings in ('', self.heading * 3):
            with self.subTest(headings=headings), self.assertRaises(AssertionError):
                parse_paris(self.menu, self.payload(headings=headings))

    def test_navigation_generic_and_nonadjacent_headings_rejected(self):
        for heading in (
                self.heading.replace('class=""', 'class="brand"'),
                self.heading.replace('class=""', 'class="generic"'),
                self.heading.replace(' class=""', ''),
                self.heading.replace('<div class="excerpt">', '<div class="other">'),
                self.heading.replace('</h1>', '</h1><p>intervening</p>'),
                self.heading.replace('<div class="excerpt">제품 설명</div>', '')):
            with self.subTest(heading=heading), self.assertRaises(AssertionError):
                parse_paris(self.menu, self.payload(headings=heading))

    def test_differing_duplicates_and_full_title_changes_rejected(self):
        for name in ('초코반딸기반케이크', '초코반딸기반케이크 (다른과일)',
                     '초코반딸기반케이크 (시즌과일) 2호', '초코반딸기...',
                     '딸기반초코반케이크 (시즌과일)'):
            changed = self.heading.replace(self.menu['name'], name)
            with self.subTest(name=name):
                for headings in (changed, self.heading + changed):
                    with self.assertRaises(AssertionError):
                        parse_paris(self.menu, self.payload(headings=headings))
                with self.assertRaises(AssertionError):
                    parse_paris(dict(self.menu, name=name), self.payload())

    def test_exact_canonical_path_and_external_id_preserved(self):
        for external_id in ('/product/new-다른케이크', self.menu['externalId']+'?size=2',
                            self.menu['externalId']+'#variant', 'https://www.paris.co.kr'+self.menu['externalId']):
            with self.subTest(external_id=external_id), self.assertRaises(AssertionError):
                parse_paris(dict(self.menu, externalId=external_id), self.payload())
        for payload in (self.payload().replace('new-초코반딸기반케이크/', 'new-초코반딸기반케이크-2호/'),
                        self.payload().replace('www.paris.co.kr', 'other.example')):
            with self.assertRaises(AssertionError):
                parse_paris(self.menu, payload)

    def test_explicit_nutrition_labels_exclude_ingredients_and_nested_p(self):
        ingredients = '<div class="product-info"><p>원재료: 설탕 · 단백질(g): 99</p></div>'
        self.assertEqual(4, parse_paris(self.menu, self.payload(panels=ingredients+self.panel))['proteinGrams'])
        for panel in (ingredients, self.panel.replace('100g당 칼로리(kcal)', '칼로리(kcal)'),
                      self.panel.replace('<p>', '<section><p>').replace('</p>', '</p></section>')):
            with self.subTest(panel=panel), self.assertRaises(AssertionError):
                parse_paris(self.menu, self.payload(panels=panel))

    def test_identical_nutrition_duplicates_accepted_conflicts_rejected(self):
        duplicate = self.panel.replace('310', ' 310 ').replace('690g', '690 g')
        self.assertEqual(310, parse_paris(self.menu, self.payload(panels=self.panel+duplicate))['energyKcal'])
        for old, new in (('310', '311'), ('690g', '691g'), ('단백질(g): 4', '단백질(g): 5'),
                         ('100g당', '100ml당')):
            with self.subTest(old=old), self.assertRaises(AssertionError):
                parse_paris(self.menu, self.payload(panels=self.panel+self.panel.replace(old, new)))

    def test_legacy_and_second_template_conflicting_evidence_rejected(self):
        legacy_heading = '<h1 class="product-name">'+self.menu['name']+'</h1>'
        self.assertEqual(310, parse_paris(self.menu, self.payload(headings=legacy_heading))['energyKcal'])
        with self.assertRaises(AssertionError):
            parse_paris(self.menu, self.payload(headings=legacy_heading+self.heading.replace('(시즌과일)', '(다른과일)')))
        legacy_panel = self.panel.replace('product-info', 'product-nutrition')
        self.assertEqual(310, parse_paris(self.menu, self.payload(panels=legacy_panel+self.panel))['energyKcal'])
        with self.assertRaises(AssertionError):
            parse_paris(self.menu, self.payload(panels=legacy_panel+self.panel.replace('310', '311')))

class TljInlineNutritionTest(unittest.TestCase):
    # Offline contract from the user-provided verified public extract, not a GET.
    # Supplied source SHA256 (not independently verified):
    # 63bec57772c1c32531a48da8de6cb59df87ce5029eab291e8fe9fe386a0c66be
    menu = dict(brand='뚜레쥬르', externalId='5379', name='피글렛의 달콤한 딸기우유 (200ml)')
    payload = '''<link rel="canonical" href="https://www.tlj.co.kr/product/detail.asp?gubun=result&amp;prod_num=5379">
    <span class="name">피글렛의 달콤한 딸기우유 (200ml)</span>
    <table summary="108g"><caption>영양성분</caption>
    <thead><tr><td>중량(g)200</td></tr></thead><tbody>
    <tr><th>열량(kcal)</th><td>145</td></tr>
    <tr><th>당류(g/%)</th><td>18g/18%</td></tr>
    <tr><th>단백질(g/%)</th><td>3.8/7%</td></tr>
    <tr><th>포화지방(g/%)</th><td>4g/27%</td></tr>
    <tr><th>나트륨(mg/%)</th><td>175/9%</td></tr>
    </tbody></table>'''

    def test_supplied_full_title_contract_and_existing_values(self):
        f = parse_tlj(self.menu, self.payload)
        self.assertEqual((145, 200, 'g', 3.8, 175, 18, 4), tuple(f[k] for k in
                         ('energyKcal', 'servingAmount', 'servingUnit', 'proteinGrams',
                          'sodiumMilligrams', 'sugarsGrams', 'saturatedFatGrams')))
        self.assertEqual('공식 1회 제공량 기준', f['servingDescription'])
        self.assertIsNone(f['carbohydrateGrams'])
        self.assertIsNone(f['fatGrams'])
        extra = '<tr><th>탄수화물(g/%)</th><td>22/7%</td></tr><tr><th>지방(g/%)</th><td>6/11%</td></tr>'
        f = parse_tlj(self.menu, self.payload.replace('</tbody>', extra+'</tbody>'))
        self.assertEqual((22, 6, 18, 4), tuple(f[k] for k in
                         ('carbohydrateGrams', 'fatGrams', 'sugarsGrams', 'saturatedFatGrams')))

    def test_optional_values_independent_missing_zero_and_exact_labels(self):
        for label, field, value, other, other_value in (
                ('당류', 'sugarsGrams', '18g/18%', 'saturatedFatGrams', 4),
                ('포화지방', 'saturatedFatGrams', '4g/27%', 'sugarsGrams', 18)):
            row = '<tr><th>'+label+'(g/%)</th><td>'+value+'</td></tr>'
            for replacement, expected in (('', None), ('0g/9%', 0), ('2.5/9%', 2.5),
                                          ('/9%', None), ('1g 미만/9%', None)):
                payload = self.payload.replace(row, '' if replacement == '' else row.replace(value, replacement))
                with self.subTest(field=field, replacement=replacement):
                    f = parse_tlj(self.menu, payload)
                    self.assertEqual(expected, f[field])
                    self.assertEqual(other_value, f[other])
            f = parse_tlj(self.menu, self.payload.replace(label+'(g/%)', label+'(%)'))
            self.assertIsNone(f[field])
            with self.assertRaisesRegex(AssertionError, 'Duplicate official nutrient'):
                parse_tlj(self.menu, self.payload.replace('</tbody>', row+'</tbody>'))
        f = parse_tlj(self.menu, self.payload.replace('18g/18%', '').replace('4g/27%', ''))
        self.assertIsNone(f['sugarsGrams'])
        self.assertIsNone(f['saturatedFatGrams'])

    def test_truncated_title_and_wrong_product_id_rejected(self):
        for changes in (dict(name='피글렛의 달콤한 딸기우유'), dict(externalId='5380')):
            with self.subTest(changes=changes), self.assertRaises(AssertionError):
                parse_tlj(dict(self.menu, **changes), self.payload)

    def test_missing_published_weight_remains_unknown(self):
        f = parse_tlj(self.menu, self.payload.replace('중량(g)200', '중량 미공개'))
        self.assertIsNone(f['servingAmount'])
        self.assertIsNone(f['servingUnit'])


class TljCrosswalkInlineTests(unittest.TestCase):
    menu = dict(TljInlineNutritionTest.menu, name='피글렛의 달콤한 딸기우유 ...')
    payload = TljInlineNutritionTest.payload
    listing = """<meta charset="utf-8"><ul><li class="item_wrap">
    <a href="javascript:viewDetail('5379')"><span class="name">피글렛의 달콤한 딸기우유 ...</span>
    <img alt="피글렛의 달콤한 딸기우유 (200ml)"></a>
    <a href="javascript:viewDetail('5379')"><span class="name">피글렛의 달콤한 딸기우유 ...</span></a>
    </li></ul>""".encode('utf-8')

    def test_exact_crosswalk_and_actual_bytes_hash(self):
        import hashlib
        from franchise_nutrition_bakery import TLJ_LIST_URL
        fact = parse_tlj(self.menu, self.payload, self.listing)
        self.assertEqual('tlj-listing-display-id-alt', fact['matchedBy'])
        self.assertEqual(TLJ_LIST_URL, fact['listSourceURL'])
        self.assertEqual(hashlib.sha256(self.listing).hexdigest(), fact['listSourceSha256'])
        self.assertEqual(TljInlineNutritionTest.menu['name'], fact['officialDetailFullName'])
        self.assertEqual(self.menu['name'], fact['listingDisplayName'])
        self.assertEqual((145, 200, 3.8, 18, 4, 175), tuple(fact[k] for k in
            ('energyKcal', 'servingAmount', 'proteinGrams', 'sugarsGrams', 'saturatedFatGrams', 'sodiumMilligrams')))
        self.assertIsNone(fact['carbohydrateGrams']); self.assertIsNone(fact['fatGrams'])
        zero = parse_tlj(self.menu, self.payload.replace('18g/18%', '0g/0%').replace('4g/27%', ''), self.listing)
        self.assertEqual(0, zero['sugarsGrams']); self.assertIsNone(zero['saturatedFatGrams'])
        # A provided listing does not change already exact detail-title behavior.
        self.assertNotIn('matchedBy', parse_tlj(TljInlineNutritionTest.menu, self.payload, b'<html/>'))

    def test_crosswalk_failclosed_identity_guards(self):
        text = self.listing.decode('utf-8')
        variants = [None, b'<html/>', text.replace('5379', '5380').encode(),
            text.replace('우유 ...', '우유').encode(),
            text.replace('딸기우유 (200ml)', '초코우유 (200ml)').encode(),
            text.replace('(200ml)', '(300ml)').encode(),
            (text+text).encode(),
            text.replace("viewDetail('5379')", "viewDetail('5380')", 1).encode(),
            text.replace("viewDetail('5379')", 'void(0)', 1).encode(),
            text.replace('<img alt=', '<img alt="other"><img alt=', 1).encode(),
            text.replace('<span class="name">', '<span class="name">wrong', 1).encode()]
        for listing in variants:
            with self.subTest(listing=listing), self.assertRaises(AssertionError):
                parse_tlj(self.menu, self.payload, listing)
        for payload in (self.payload.replace('prod_num=5379', 'prod_num=5380'),
                        self.payload.replace('www.tlj.co.kr', 'evil.example'),
                        self.payload.replace('(200ml)', '(300ml)'),
                        self.payload.replace('</span>', '</span><span class="name">duplicate</span>')):
            with self.subTest(payload=payload), self.assertRaises(AssertionError):
                parse_tlj(self.menu, payload, self.listing)
        with self.assertRaises(AssertionError):
            parse_tlj(dict(self.menu, externalId='5380'), self.payload, self.listing)

class TljBasisCompletionInlineTests(unittest.TestCase):
    menu = dict(id='stable-1264', brand='뚜레쥬르', externalId='1264', name='탑클래식(포도)750ml')
    payload = '''<link rel="canonical" href="https://www.tlj.co.kr/product/detail.asp?prod_num=1264">
    <span class="name">탑클래식(포도)750ml</span><table summary="108g"><caption>영양성분</caption>
    <thead><tr><td>총량750ml</td><td>servings7.5</td><td>중량(g) 100ml</td></tr></thead><tbody>
    <tr><th>열량(kcal)</th><td>50</td></tr><tr><th>단백질(g/%)</th><td>0.2</td></tr>
    <tr><th>나트륨(mg/%)</th><td>5</td></tr><tr><th>당류(g/%)</th><td>10</td></tr>
    <tr><th>포화지방(g/%)</th><td>0</td></tr></tbody></table>'''

    def test_direct_explicit_ml_legacy_g_and_ambiguous_units(self):
        fact = parse_tlj(self.menu, self.payload)
        self.assertEqual((100, 'ml', 50, .2, 5, 10, 0), tuple(fact[k] for k in
            ('servingAmount', 'servingUnit', 'energyKcal', 'proteinGrams', 'sodiumMilligrams', 'sugarsGrams', 'saturatedFatGrams')))
        self.assertIsNone(fact['carbohydrateGrams'])
        self.assertIsNone(fact['fatGrams'])
        legacy = parse_tlj(self.menu, self.payload.replace('100ml', '100'))
        self.assertEqual((100, 'g'), (legacy['servingAmount'], legacy['servingUnit']))
        for text in ('100g/ml', '100ml g', '100g 100ml'):
            with self.subTest(text=text), self.assertRaises(AssertionError):
                parse_tlj(self.menu, self.payload.replace('100ml', text))
        missing = parse_tlj(self.menu, self.payload.replace('중량(g) 100ml', '중량 미공개'))
        self.assertIsNone(missing['servingAmount'])

    def test_same_source_null_basis_requires_all_known_values(self):
        from franchise_nutrition import retain_verified_on_conflict
        candidate = parse_tlj(self.menu, self.payload)
        candidate.update(officialProductId='1264', sourceUrl='https://www.tlj.co.kr/product/detail.asp?prod_num=1264',
                         sourceSha256='a'*64, checkedAt='2026-10-14T00:00:00Z')
        old = dict(candidate, servingAmount=None, servingUnit=None, checkedAt='2026-10-01T00:00:00Z', sourceSha256='b'*64)
        menu = dict(self.menu, officialNutrition=old)
        merged, conflicts = retain_verified_on_conflict(menu, candidate)
        self.assertFalse(conflicts)
        self.assertEqual((100, 'ml'), (merged['servingAmount'], merged['servingUnit']))
        self.assertTrue(all(merged[k] == v for k, v in old.items() if k not in ('servingAmount', 'servingUnit')))
        for changes in (dict(sourceUrl=candidate['sourceUrl']+'&x=1'), dict(officialProductId='other'),
                        dict(proteinGrams=None), dict(sugarsGrams=None), dict(saturatedFatGrams=None),
                        dict(energyKcal=51), dict(servingAmount=float('nan')), dict(servingUnit='unknown')):
            # Invalid candidate basis is withheld by the preexisting optional basis guard.
            with self.subTest(changes=changes):
                retained, conflicts = retain_verified_on_conflict(menu, dict(candidate, **changes))
                self.assertTrue(conflicts)
                self.assertEqual(old, retained)
        for partial in (dict(servingAmount=100), dict(servingUnit='ml')):
            partial_old = dict(old, **partial)
            retained, conflicts = retain_verified_on_conflict(dict(menu, officialNutrition=partial_old), candidate)
            self.assertTrue(conflicts)
            self.assertEqual(partial_old, retained)
        # Core-only captures require the same independent corroboration, even
        # when the existing optional-nutrient guard would not have run.
        core_old = {k: v for k, v in old.items() if k not in ('sugarsGrams', 'saturatedFatGrams')}
        core_candidate = {k: v for k, v in candidate.items() if k not in ('sugarsGrams', 'saturatedFatGrams')}
        for changes in (dict(proteinGrams=None), dict(sourceUrl='https://www.tlj.co.kr/other'), dict(officialProductId='125')):
            retained, conflicts = retain_verified_on_conflict(dict(menu, officialNutrition=core_old), dict(core_candidate, **changes))
            self.assertTrue(conflicts); self.assertEqual(core_old, retained)
        shop_known = dict(old, carbohydrateGrams=16)
        retained, conflicts = retain_verified_on_conflict(dict(menu, officialNutrition=shop_known), candidate)
        self.assertTrue(conflicts); self.assertEqual(shop_known, retained)

    def test_actual_outer_hash_navigation_only(self):
        t = TljCrosswalkInlineTests
        listing = t.listing.decode().replace("href=\"javascript:viewDetail('5379')\"", 'href="#"', 1).encode()
        self.assertEqual('tlj-listing-display-id-alt', parse_tlj(t.menu, t.payload, listing)['matchedBy'])
        for href in ('#other', 'https://foreign.example/product/5379', "javascript:viewDetail('5380')"):
            with self.subTest(href=href), self.assertRaises(AssertionError):
                parse_tlj(t.menu, t.payload, listing.replace(b'href="#"', ('href="'+href+'"').encode()))


if __name__=='__main__': unittest.main()
