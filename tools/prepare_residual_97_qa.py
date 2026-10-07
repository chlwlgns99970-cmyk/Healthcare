"""New mapping UI checks against the immutable 419 baseline."""
import json
from audit_recipe_residual_97 import ROOT, OUT, FINAL, load, save


def run():
    source = ROOT / 'app/src/androidTest/java/com/example/healthcare/FinalRecipeResidualSamsungTest.kt'
    prefix = source.read_text(encoding='utf-8').split('    @Test fun originalCompleteRemainsSeparate()')[0]
    prefix = prefix.replace('class FinalRecipeResidualSamsungTest', 'class Residual97SamsungTest')
    prefix = prefix.replace('final-residual-$name.png', 'residual-97-$name.png')
    prefix += '''    private fun verified(id:String,rid:String,name:String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind=="REFERENCE_RECIPE" && it.amountGrams>0 })
        assertEquals(name,rows.first().recipeName)
        show(id)
        visible("$name · ${rows.first().recipeBasis}")
        rows.forEach { row ->
            visible("${row.ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(row.amountGrams)}g · 약 ${kotlin.math.round(row.estimatedKcal).toInt()} kcal")
        }
        visible("$name · ${rows.first().recipeBasis}");capture(rid)
    }
'''
    refs = {r['recipeId']: r for r in load(FINAL / 'validated-reference-compositions.json')}
    chosen = []
    for item in load(OUT / 'new-app-complete-foods.json'):
        mapping = item['selectedMapping']
        ref = refs[mapping['selectedId']]
        n = len(chosen) + 1
        args = [mapping['foodIds'][0], ref['recipeId'], ref['name']]
        prefix += '    @Test fun newMapping%02d() { verified(%s) }\n' % (n, ','.join(json.dumps(a, ensure_ascii=False) for a in args))
        chosen.append(mapping | dict(compositionKind=ref['compositionKind'], testMethod='newMapping%02d' % n))
    assert chosen
    source.with_name('Residual97SamsungTest.kt').write_text(prefix + '}\n', encoding='utf-8')
    save('newly-published-device-fixtures.json', chosen)
    print('Prepared', len(chosen), 'mapping UI tests; every ingredient is asserted.')


if __name__ == '__main__':
    run()
