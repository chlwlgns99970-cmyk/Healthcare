param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$assetRoot = Join-Path $ProjectRoot 'app/src/main/assets/fooddata'
$outputRoot = Join-Path $ProjectRoot 'data-source/recommendation'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

$foods = @{}
@('food_items.csv', 'product_items.csv', 'franchise_official_items.csv') | ForEach-Object {
    Import-Csv (Join-Path $assetRoot $_) | ForEach-Object { $foods[$_.id] = $_ }
}
$ingredients = Import-Csv (Join-Path $assetRoot 'meal_template_ingredients.csv') |
    Group-Object mealTemplateId -AsHashTable -AsString
$templates = Import-Csv (Join-Path $assetRoot 'meal_templates.csv')
$official = @{}
Import-Csv (Join-Path $outputRoot 'official-ingredient-verification.csv') | ForEach-Object {
    $official[$_.stableTemplateId] = $_
}

$rows = foreach ($template in $templates) {
    $links = @($ingredients[$template.id])
    $linkedFoods = @($links | ForEach-Object { $foods[$_.foodItemId] } | Where-Object { $_ })
    function Sum-Nutrient([string]$field) {
        $sum = 0.0
        for ($i = 0; $i -lt $links.Count; $i++) {
            $food = $foods[$links[$i].foodItemId]
            if (-not $food -or [string]::IsNullOrWhiteSpace($food.$field)) { return $null }
            $sum += [double]$food.$field * [double]$links[$i].amount / [double]$food.referenceAmount
        }
        return [math]::Round($sum, 1)
    }

    $kcal = Sum-Nutrient 'energyKcal'
    $carb = Sum-Nutrient 'carbohydrateGrams'
    $protein = Sum-Nutrient 'proteinGrams'
    $fat = Sum-Nutrient 'fatGrams'
    $missingNutrients = @()
    if ($null -eq $kcal) { $missingNutrients += 'kcal' }
    if ($null -eq $carb) { $missingNutrients += 'carbohydrate' }
    if ($null -eq $protein) { $missingNutrients += 'protein' }
    if ($null -eq $fat) { $missingNutrients += 'fat' }

    $allergenTokens = @($template.allergens -split '\|' | Where-Object { $_ })
    $knownAllergens = @($allergenTokens | Where-Object { $_ -ne 'UNKNOWN' })
    $verified = $official[$template.id]
    $ingredientCompleteness = if ($verified) {
        $verified.ingredientCompleteness
    } elseif ($template.tags -like '*INGREDIENTS_COMPLETE*') {
        'COMPLETE'
    } else {
        'UNKNOWN'
    }
    $allergenCompleteness = if ($verified) {
        $verified.allergenCompleteness
    } elseif ($allergenTokens -contains 'UNKNOWN') {
        if ($knownAllergens.Count -gt 0) { 'PARTIAL' } else { 'UNKNOWN' }
    } else {
        'COMPLETE'
    }
    $ingredientNames = if ($verified) {
        $verified.ingredients
    } elseif ($ingredientCompleteness -eq 'COMPLETE') {
        $linkedFoods.name -join ' | '
    } else {
        ''
    }
    $sourceType = if ($verified) {
        $verified.sourceType
    } else {
        ($linkedFoods.sourceType | Select-Object -Unique) -join ' | '
    }
    $sourceName = if ($verified) { $verified.sourceName } else { $template.source }
    $sourceIdentifier = if ($verified) {
        $verified.sourceUrlOrIdentifier
    } else {
        ($linkedFoods | ForEach-Object { "$($_.sourceType):$($_.sourceFoodCode)" }) -join ' | '
    }
    $notes = if ($verified) {
        $verified.notes
    } elseif ($ingredientCompleteness -eq 'COMPLETE') {
        '기존 검증 완료 template metadata와 K-FIND source food 연결 유지'
    } else {
        'K-FIND 영양 DB에는 구성 원재료/알레르기 전용 필드가 없어 근거 없는 값은 UNKNOWN 유지'
    }

    [pscustomobject]@{
        stableTemplateId = $template.id
        menuName = $template.name
        sourceFoodCode = ($linkedFoods.sourceFoodCode -join ' | ')
        sourceType = $sourceType
        sourceName = $sourceName
        referenceServing = ($links | ForEach-Object { "$($_.amount)$($_.unit)" }) -join ' + '
        kcal = $kcal
        carbohydrateGrams = $carb
        proteinGrams = $protein
        fatGrams = $fat
        macroCompleteness = if ($missingNutrients.Count -eq 0) { 'COMPLETE' } else { 'PARTIAL' }
        missingNutrients = $missingNutrients -join '|'
        ingredients = $ingredientNames
        ingredientCompleteness = $ingredientCompleteness
        allergenTags = if ($verified) { $verified.allergenTags } else { $template.allergens }
        allergenCompleteness = $allergenCompleteness
        sourceUrlOrIdentifier = $sourceIdentifier
        verifiedAt = if ($verified) { $verified.verifiedAt } else { '2026-09-27' }
        notes = $notes
        imageMapping = 'DEDICATED'
    }
}

$auditPath = Join-Path $outputRoot 'recommendation-292-audit.csv'
$rows | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $auditPath
$unverifiedPath = Join-Path $outputRoot 'recommendation-unverified.csv'
$rows | Where-Object {
    $_.ingredientCompleteness -ne 'COMPLETE' -or $_.allergenCompleteness -ne 'COMPLETE'
} | ForEach-Object {
    $missing = @()
    if ($_.ingredientCompleteness -ne 'COMPLETE') {
        $missing += "ingredients:$($_.ingredientCompleteness)"
    }
    if ($_.allergenCompleteness -ne 'COMPLETE') {
        $missing += "allergens:$($_.allergenCompleteness)"
    }
    [pscustomobject]@{
        stableTemplateId = $_.stableTemplateId
        menuName = $_.menuName
        missing = $missing -join '|'
        reason = $_.notes
        sourceUrlOrIdentifier = $_.sourceUrlOrIdentifier
    }
} | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $unverifiedPath

function Count-Status([string]$field, [string]$status) {
    @($rows | Where-Object { $_.$field -eq $status }).Count
}
$summary = @(
    '# 추천 메뉴 원재료·알레르기 데이터 감사',
    '',
    '- 감사일: 2026-09-27',
    "- 전체: $($rows.Count)",
    "- kcal 완전: $(@($rows | Where-Object { $null -ne $_.kcal }).Count)",
    "- 탄수화물 완전: $(@($rows | Where-Object { $null -ne $_.carbohydrateGrams }).Count)",
    "- 단백질 완전: $(@($rows | Where-Object { $null -ne $_.proteinGrams }).Count)",
    "- 지방 완전: $(@($rows | Where-Object { $null -ne $_.fatGrams }).Count)",
    "- 네 값 완전: $(@($rows | Where-Object macroCompleteness -eq 'COMPLETE').Count)",
    "- 원재료 COMPLETE: $(Count-Status 'ingredientCompleteness' 'COMPLETE')",
    "- 원재료 PARTIAL: $(Count-Status 'ingredientCompleteness' 'PARTIAL')",
    "- 원재료 UNKNOWN: $(Count-Status 'ingredientCompleteness' 'UNKNOWN')",
    "- 알레르기 COMPLETE: $(Count-Status 'allergenCompleteness' 'COMPLETE')",
    "- 알레르기 PARTIAL: $(Count-Status 'allergenCompleteness' 'PARTIAL')",
    "- 알레르기 UNKNOWN: $(Count-Status 'allergenCompleteness' 'UNKNOWN')",
    "- 전용 이미지 매핑: $($rows.Count)/$($rows.Count)",
    '',
    'COMPLETE는 공식·공공·기존 검증 metadata로 주요 구성과 알레르기 판정 정보가 충분한 경우입니다.',
    'PARTIAL은 공식 자료에서 주요 구성 일부만 확인된 경우입니다.',
    'UNKNOWN은 신뢰 가능한 구성 원재료 또는 알레르기 정보를 확보하지 못한 경우입니다.',
    'K-FIND 영양 DB의 음식명 하나만으로 구성 원재료를 추정하지 않았습니다.',
    '',
    '상세 CSV: recommendation-292-audit.csv',
    '미확인 목록: recommendation-unverified.csv'
)
$summary | Set-Content -Encoding UTF8 (Join-Path $outputRoot 'README.md')
Write-Output "Wrote $($rows.Count) rows to $auditPath"
