"""Device tests for every newly published mapping since the immutable 381 baseline."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-reference-finalization'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def run():
    before={s['recipeId']:s for s in load(OUT/'before-states.json')}
    states=load(ROOT/'data-source/recipe-final-residual/recipe-final-states.json')
    maps={m['originalRecipeId']:m for m in load(ROOT/'data-source/recipe-final-residual/food-mapping-decisions.json')}
    refs={r['recipeId']:r for r in load(ROOT/'data-source/recipe-final-residual/validated-reference-compositions.json')+load(ROOT/'data-source/recipe-final-residual/additional-original-compositions.json')}
    source=ROOT/'app/src/androidTest/java/com/example/healthcare/FinalRecipeResidualSamsungTest.kt'
    prefix=source.read_text(encoding='utf-8').split('    @Test fun originalCompleteRemainsSeparate()')[0]
    prefix=prefix.replace('class FinalRecipeResidualSamsungTest','class ReferenceFinalizationSamsungTest').replace('final-residual-$name.png','reference-finalization-$name.png')
    prefix+='''    private fun verified(id:String,rid:String,name:String,kind:String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind==kind && it.amountGrams>0 })
        assertEquals(name,rows.first().recipeName)
        show(id)
        if(kind=="ORIGINAL") visible("재료별 예상 열량")
        if(kind=="SURVEY_AVERAGE") visible("재료별 예상 열량 · 공공 조사 평균 참고 구성")
        visible("$name · ${rows.first().recipeBasis}")
        rows.forEach { row ->
            visible("${row.ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(row.amountGrams)}g · 약 ${kotlin.math.round(row.estimatedKcal).toInt()} kcal")
        }
        if(kind=="SURVEY_AVERAGE") visible("식이 조사 평균 자료로, 음식 조리를 위한 정보로 사용하기에는 적절하지 않습니다.")
        visible("$name · ${rows.first().recipeBasis}");capture(rid)
    }
'''
    chosen=[]
    for s in states:
        if before[s['recipeId']]['appCompleteAvailable'] or not s['appCompleteAvailable']:continue
        m=maps[s['recipeId']];r=refs[m['selectedId']];n=len(chosen)+1
        args=[m['foodIds'][0],r['recipeId'],r['name'],r['compositionKind']]
        prefix+='    @Test fun publishedMapping%02d() { verified(%s) }\n'%(n,','.join(json.dumps(x,ensure_ascii=False) for x in args))
        chosen.append(m|dict(compositionKind=r['compositionKind'],testMethod='publishedMapping%02d'%n))
    prefix+='}\n'
    source.with_name('ReferenceFinalizationSamsungTest.kt').write_text(prefix,encoding='utf-8')
    (OUT/'newly-published-device-fixtures.json').write_text(json.dumps(chosen,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Prepared',len(chosen),'new mapping tests with every ingredient rendered.')
if __name__=='__main__':run()
