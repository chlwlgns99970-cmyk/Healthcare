"""Focused parser checks: official single-roll evidence, never generic kimbap weights."""
import unittest
from import_kfind_processed_foods import package_unit


class FoodAmountMetadataTest(unittest.TestCase):
    def test_official_one_roll_name_and_total_have_package_unit(self):
        self.assertEqual('줄', package_unit('백종원한줄김밥', '주먹밥/김밥/초밥', '', '210g', (216., 'g')))
        self.assertEqual('줄', package_unit('단백한줄 불닭김밥', '주먹밥/김밥/초밥', '', '210g', (220., 'g')))

    def test_generic_and_multiple_roll_names_do_not_assume_one_roll(self):
        for name in ('계란김밥', '고추김밥', '참치김밥', '세줄꼬마김밥', '압도적참치김밥(5줄)', '11줄김밥'):
            self.assertIsNone(package_unit(name, '주먹밥/김밥/초밥', '', '210g', (250., 'g')))

    def test_ingredient_and_kit_never_become_ready_to_eat_roll(self):
        for name in ('한줄김밥용햄', '한줄김밥키트', '한줄김밥세트', '한줄김밥(반제)'):
            self.assertIsNone(package_unit(name, '주먹밥/김밥/초밥', '', '210g', (250., 'g')))
        self.assertIsNone(package_unit('한줄김밥', '김', '', '4g', (250., 'g')))

    def test_missing_total_and_volume_do_not_invent_roll_mass(self):
        self.assertIsNone(package_unit('백종원한줄김밥', '주먹밥/김밥/초밥', '', '210g', None))
        self.assertIsNone(package_unit('백종원한줄김밥', '주먹밥/김밥/초밥', '', '210g', (400., 'ml')))


if __name__ == '__main__':
    unittest.main()
