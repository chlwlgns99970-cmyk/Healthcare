"""Create targeted tests only for the 20 explicitly reviewed followup mappings."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-final-residual-followup'
NAMES=['메추리알조림','숙주나물무침','배추들깨국','취나물된장무침','두부양념조림','북어맑은국','오징어무국','꼬치어묵국','달걀야채오믈렛','고구마줄기볶음','밀가루수제비(수제비, 밀가루자베기)','만둣국(병시)','두부김치','주꾸미볶음','달래오이무침','건새우아욱국','도라지오이무침','마늘쫑새우볶음','콩나물겨자채','콩나물겨자냉채']
def run():
    source=ROOT/'app/src/androidTest/java/com/example/healthcare/FinalRecipeResidualSamsungTest.kt'
    prefix=source.read_text(encoding='utf-8').split('    @Test fun originalCompleteRemainsSeparate()')[0]
    prefix=prefix.replace('class FinalRecipeResidualSamsungTest','class FollowupRecipeResidualSamsungTest').replace('final-residual-$name.png','followup-residual-$name.png')
    prefix+='''    private fun verified(id:String,rid:String,name:String,kind:String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind==kind && it.amountGrams>0 })
        assertEquals(name,rows.first().recipeName)
        show(id);visible("$name · ${rows.first().recipeBasis}")
        val last=rows.last()
        visible("${last.ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(last.amountGrams)}g · 약 ${kotlin.math.round(last.estimatedKcal).toInt()} kcal")
        if(kind=="SURVEY_AVERAGE") visible("식이 조사 평균 자료로, 음식 조리를 위한 정보로 사용하기에는 적절하지 않습니다.")
        visible("$name · ${rows.first().recipeBasis}");capture(rid)
    }
'''
    maps=json.loads((ROOT/'data-source/recipe-final-residual/food-mapping-decisions.json').read_text(encoding='utf-8'))
    refs={r['recipeId']:r for r in json.loads((ROOT/'data-source/recipe-final-residual/validated-reference-compositions.json').read_text(encoding='utf-8'))}
    chosen=[]
    for index,name in enumerate(NAMES):
        m=next(x for x in maps if x['originalName']==name);r=refs[m['selectedId']]
        args=[m['foodIds'][0],r['recipeId'],r['name'],r['compositionKind']]
        prefix+='    @Test fun reviewedMapping%02d() { verified(%s) }\n'%(index+1,','.join(json.dumps(x,ensure_ascii=False) for x in args))
        chosen.append(m|dict(compositionKind=r['compositionKind'],sourceSha256=r['sourceSha256'],ingredientCount=len(r['inputs']),testMethod='reviewedMapping%02d'%(index+1)))
    prefix+='}\n'
    target=source.with_name('FollowupRecipeResidualSamsungTest.kt');target.write_text(prefix,encoding='utf-8')
    (OUT/'newly-reviewed-mappings.json').write_text(json.dumps(chosen,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Prepared 20 named recipe/foodId targeted Samsung tests.')
if __name__=='__main__':run()
