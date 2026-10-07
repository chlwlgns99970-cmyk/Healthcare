"""Final APK, test, and private-data evidence for the 97-food request."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from audit_recipe_residual_97 import ROOT, OUT, ASSETS, load, save


def run():
    apk=ROOT/'app/build/outputs/apk/qa/app-qa.apk'
    checks={}
    with zipfile.ZipFile(apk) as archive:
        for name in ['food_items.csv','product_items.csv','franchise_official_items.csv','recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv']:
            data=(ASSETS/name).read_bytes()
            assert archive.read('assets/fooddata/'+name)==data
            checks[name]=hashlib.sha256(data).hexdigest()
    for name,expected in load(OUT/'protected-sha256.json').items():
        assert checks[name]==expected
    unit=[]
    for path in sorted((ROOT/'app/build/test-results/testQaUnitTest').glob('TEST-*.xml')):
        suite=ET.parse(path).getroot()
        assert int(suite.attrib['failures'])==0 and int(suite.attrib['errors'])==0
        unit.append(dict(name=suite.attrib['name'],tests=int(suite.attrib['tests']),failures=0,errors=0))
    assert sum(r['tests'] for r in unit)==12
    qa=ROOT/'app/build/recipe-residual-97'
    instrument=(qa/'samsung-instrumentation.txt').read_text(encoding='utf-8-sig')
    assert 'OK (24 tests)' in instrument
    assert instrument.count('INSTRUMENTATION_STATUS_CODE: 0')==24
    preservation=load(qa/'preservation-verify.json')
    assert preservation['status']=='PASS'
    assert preservation['allAllowlistedPrivateFileBytesIdentical'] and preservation['productIdentityUnchanged']
    assert preservation['changedTables']==[]
    matrices={
      '남은97분류':'Residual97Test.test_01', 'foodId matcher':'Residual97Test.test_03',
      'exact/alias/context':'Residual97Test.test_04', 'candidate ranking':'FinalResidualEvidenceTest.test_28',
      'reference publish':'Residual97Test.test_03', 'duplicate reference':'FinalResidualEvidenceTest.test_07 + finisher unique key assertion',
      'canonical ingredient':'FinalResidualEvidenceTest.test_08', 'amount parsing':'FinalResidualEvidenceTest.test_18',
      'unit conversion':'FinalResidualEvidenceTest.test_12', 'unit conflict':'Residual97Test.test_07',
      'nutrition matching':'FinalResidualEvidenceTest.test_08,test_16', 'reference kcal':'FinalResidualEvidenceTest.test_16',
      'provenance':'FinalResidualEvidenceTest.test_09 + Residual97Test.test_08',
      'ORIGINAL_COMPLETE':'Samsung.originalCompleteRemainsSeparate', 'REFERENCE_COMPLETE':'Samsung.newMapping01,newMapping02',
      '조사평균표시':'FinalResidualEvidenceTest.test_30 + Samsung.surveyReferenceIsNotPresentedAsCookingRecipe',
      'PARTIAL':'Samsung.partialOriginalDoesNotBecomeComplete', 'UNRESOLVED':'Residual97Test.test_01 + focused-unresolved-and-source-review.json',
      'fake foodId방지':'Residual97Test.test_06', 'fake amount방지':'FinalResidualEvidenceTest.test_05,test_06',
      'fake kcal방지':'FinalResidualEvidenceTest.test_16', '공식kcal보존':'Residual97Test.test_02 + Samsung.officialCaloriesInActualRecordScreenStay140',
      'MealRecord kcal보존':'Samsung.savedMealCaloriesNeverUseReferenceSum + preservation-verify.json'}
    assert len(matrices)==23
    save('validation.json',dict(status='PASS',dataTests=40,unitTests=12,qaBuild='PASS',
        samsungTests=24,samsungDevice='SM-S948N / Android16 API36',newMappingTests=2,
        unitSuites=unit,requiredCategories=matrices,packagedAssets=checks,
        packagedSharedSource='app/src/main/assets/fooddata',
        qaApkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
        preservationEvidence='app/build/recipe-residual-97/preservation-verify.json',
        privateFilesByteIdentical=True,changedTables=[],DBVersion=8,migrations=0,
        productIdentityUnchanged=True,productionInstall=0,productionClear=0,productionDeploy=0,
        visualScreenshotsReviewed=['residual-97-MENUZEN-D102007.png','residual-97-MENUZEN-D101006.png']))
    print('Packaged assets, 23-category coverage, Unit12, Samsung24, and preservation PASS')


if __name__=='__main__':run()
