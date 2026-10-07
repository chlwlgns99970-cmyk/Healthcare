"""Separate amount/portion/source review from original ingredient decisions."""
import collections,hashlib,json,re
from lxml import html
from relink_recipe_strategy import ROOT,OUT,load,save,key,EXCLUDED,DATE

def run():
    decisions=load(OUT/'ingredient-decisions.json');baseline=load(OUT/'baseline-ingredient-decisions.json')
    attempts=load(OUT/'residual-attempts.json');bykey={key(d):d for d in decisions}
    captures=load(OUT/'source-captures.json')
    # Newly downloaded official manufacturer manual: read visually because its
    # embedded Korean font produces corrupt extracted ingredient text.
    manual=next(c for c in captures if c.get('rawFile','').endswith('7ffa67dccd14552ae79b'))
    assert hashlib.sha256((ROOT/manual['rawFile']).read_bytes()).hexdigest()==manual['sha256']
    museum=next(c for c in captures if c.get('rawFile','').endswith('a3327250da7f749205ed'))
    doc=html.fromstring((ROOT/museum['rawFile']).read_text(encoding='utf-8'))
    text=' '.join(doc.text_content().split());assert '밥 200g' in text and '김치 30g' in text
    alternates=[dict(recipeId='ZOJIRUSHI-ESGWH26K-JAM-P95',name='딸기잼',
        sourceUrl=manual['url'],sourceSha256=manual['sha256'],pdfPage=48,printedPage=95,
        visualEvidence='data-source/recipe-linkage-strategy/raw/manufacturer-page-48.png',
        scope='SEPARATE_MANUFACTURER_REFERENCE_NOT_MFDS_223',published=False,
        inputs=[dict(ingredient='딸기(먹는 부분)',grams=300,amountEvidenceStatus='OFFICIAL_ALTERNATE_RECIPE_AMOUNT'),
                dict(ingredient='설탕',grams=120,amountEvidenceStatus='OFFICIAL_ALTERNATE_RECIPE_AMOUNT'),
                dict(ingredient='레몬즙',quantity=1,unit='큰술',grams=None,amountEvidenceStatus='NO_SAFE_AMOUNT',reason='원문 부피만 있음; 농축/생즙을 선택하거나 밀도를 추정하지 않음')]),
        dict(recipeId='SEOUL-MUSEUM-833',name='접어 만든 김밥',sourceUrl=museum['url'],sourceSha256=museum['sha256'],
             scope='SEPARATE_PUBLIC_RECIPE_NOT_ORIGINAL_RICE_OR_PRODUCT_FORMULATION',published=False,
             inputs=[dict(ingredient='김밥용 밥',grams=200,amountEvidenceStatus='OFFICIAL_ALTERNATE_RECIPE_AMOUNT'),
                     dict(ingredient='볶음 김치',grams=30,amountEvidenceStatus='OFFICIAL_ALTERNATE_RECIPE_AMOUNT')],
             unresolvedOriginalText='김 2장, 지단 2장, 오이·당근 각 1/6개, 스팸 2쪽, 치즈 1장; 식초·깨·소금 조금씩')]
    save('alternate-official-reference-review.json',alternates)
    # Audit every missing amount separately. Other recipes and seasoning phases
    # cannot supply an original row's amount, even under the same dish name.
    format_review={};amount_review=[]
    for a in attempts:
        d=bykey[key(a)];rid=d['recipeId']
        if rid not in format_review:
            p=ROOT/'app/build/food-quality-qa/recipe-source'/(rid.lower().replace('rda-diet-','nongsaro-diet-')+'.html')
            doc=html.fromstring(p.read_bytes(),parser=html.HTMLParser(encoding='utf-8'))
            downloadable=[v for v in doc.xpath('//a/@href|//iframe/@src|//script/@src') if re.search(r'\.pdf|\.xlsx?|\.csv|\.json|download',v,re.I)]
            embedded=doc.xpath('//script[@type="application/ld+json" or @type="application/json"]/text()')
            format_review[rid]=dict(originalHtmlHash=d['sourceSha256'],downloadLinks=downloadable,embeddedRecipeJsonCount=len(embedded),
                cachedOfficialCookbook='data-source/recipe-linkage-residual/mfds-cookbook-pages.json',
                result='NO_ADDITIONAL_SAME_ID_QUANTITY_FROM_AVAILABLE_FORMATS',
                limitation='인증이 필요한 API 및 미확보 원전 책자까지 부재를 증명한 것은 아님')
        if d['amountGrams'] is not None:continue
        amount_review.append(dict(recipeId=rid,ingredientIndex=d['ingredientIndex'],ingredient=d['ingredient'],
            amountEvidenceStatus='NO_SAFE_AMOUNT',originalSpan=d['originalSpan'],checkedAt=DATE,
            checkedSource=d['sourceUrl'],query=d['ingredient'],formatReview=rid,
            result='NO_EXACT_ORIGINAL_ROW_MASS_OR_AGREED_INGREDIENT_UNIT',
            sameIngredientOtherPhaseRejected=rid=='RDA-DIET-89289-0' and d['ingredient']=='참기름'))
    save('same-recipe-format-review.json',format_review);save('amount-fallback-review.json',amount_review)
    units=load(ROOT/'data-source/recipe-linkage-maximization/official-unit-equivalences.json')
    unit_index={(re.sub(r'\s+','',u['ingredient']),u['unit']):u for u in units}
    save('ingredient-specific-unit-review.json',[dict(recipeId=d['recipeId'],ingredientIndex=d['ingredientIndex'],ingredient=d['ingredient'],unit=d['unit'],
        query=d['ingredient']+' '+str(d['unit']),checkedAt=DATE,
        reviewedOfficialEquivalence=unit_index.get((re.sub(r'\s+','',d['ingredient']),d['unit'])),
        result='EXPLICIT_SAME_FORM_RDA_REFERENCE_PORTION' if (bykey[key(d)].get('conversionProvenance') or {}).get('source')=='RDA_EXPLICIT_SAME_FORM_REFERENCE_PORTION' else 'NO_UNIQUE_MATCHING_FORM_PORTION',checkedSource='data-source/recipe-linkage-maximization/official-unit-equivalences.json')
        for d in baseline if d['status'] not in ('LINKED',EXCLUDED) and d['amountGrams'] is None and d['quantity'] is not None and d['unit'] is not None and not d['quantityRange']])
    nifs=next(c for c in captures if c.get('rawFile','').endswith('67b41a8376623c00645e'))
    rows=load(ROOT/nifs['rawFile'])['retList'];assert len(rows)==37 and rows[0]['allCnt']==37
    save('known-source-gaps.json',dict(webfootOctopusInk=dict(status='NO_OFFICIAL_NUTRITION_MATCH',
        newSource=nifs,structuredCephalopodRows=37,inkRows=[r for r in rows if '먹물' in r['fimKorName']],
        previousSourceReview='data-source/recipe-linkage-residual/source-captures.json',
        rejection='몸통/다리/전체 또는 Octopus vulgaris 영양값을 주꾸미 먹물에 대입하지 않음'),
        originalJam=dict(recipeId='MFDS-223',status='RECIPE_SOURCE_INCOMPLETE',
        alternateId=alternates[0]['recipeId'],originalAmountOverridden=False,
        rejection='완제품 81g 및 다른 제조사 레시피에서 원본 배합을 역산하지 않음')))
    # Correct disjoint report counts, keeping compound identity failures in the
    # separate axis artifact rather than double-counting totals.
    def causes(rows):
        result=collections.Counter()
        for d in rows:
            if d['status'] in ('LINKED',EXCLUDED):continue
            if d['status']=='RECIPE_SOURCE_INCOMPLETE':cause='recipeSource'
            elif d['status']=='NO_OFFICIAL_NUTRITION_MATCH':cause='nutrition'
            elif d['amountGrams'] is not None:cause='identity'
            elif d['quantity'] is None or d['unit'] is None or d['quantityRange']:cause='amount'
            else:cause='unit'
            result[cause]+=1
        return dict(result)
    linked=load(OUT/'newly-linked-ingredients.json');new_complete=load(OUT/'newly-complete-recipes.json')
    save('strategy-results.json',dict(beforeCauses=causes(baseline),afterCauses=causes(decisions),newLinked=len(linked),newComplete=len(new_complete),
        newIdentity=sum(a['identityChanged'] for a in attempts),newAmount=0,
        newUnit=sum(a['amountChanged'] for a in attempts),newNutrition=0,sourceContributions=dict(collections.Counter(d['nutritionProvenance'].get('sourceName') or ('REUSED_USDA' if d['nutrientId'].isdigit() else 'REUSED_RDA_10.4') for d in linked)),
        separateAlternateQuantifiedRows=4,originalCompleteInflation=0))
    print(json.dumps(load(OUT/'strategy-results.json'),ensure_ascii=False))
if __name__=='__main__':run()
