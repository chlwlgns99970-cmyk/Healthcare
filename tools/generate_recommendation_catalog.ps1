param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$assetRoot = Join-Path $ProjectRoot 'app/src/main/assets/fooddata'
$foodPath = Join-Path $assetRoot 'food_items.csv'
$templatePath = Join-Path $assetRoot 'meal_templates.csv'
$ingredientPath = Join-Path $assetRoot 'meal_template_ingredients.csv'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$baseTemplates = Import-Csv -LiteralPath $templatePath |
    Where-Object { $_.id -notlike 'kfind-catalog-*' }
$baseIngredients = Import-Csv -LiteralPath $ingredientPath |
    Where-Object { $_.mealTemplateId -notlike 'kfind-catalog-*' }
$baseNames = @{}
$baseTemplates | ForEach-Object {
    $normalized = ($_.name.ToLowerInvariant() -replace '[^0-9a-z가-힣]', '')
    $baseNames[$normalized] = $true
    if ($_.tags -notmatch '\|INGREDIENTS_COMPLETE\|') {
        $_.tags = ($_.tags.TrimEnd('|') + '|INGREDIENTS_COMPLETE|').Replace('||', '|')
        if (-not $_.tags.StartsWith('|')) { $_.tags = '|' + $_.tags }
    }
}

$excludedNamePattern = '개고기|고래|자라|뱀|메뚜기|번데기|개소주'
$foods = Import-Csv -LiteralPath $foodPath |
    Where-Object {
        $_.sourceType -eq 'K-FIND' -and
        $_.unit -eq 'g' -and
        [double]$_.energyKcal -gt 0 -and
        $_.name -notmatch '_' -and
        $_.name -notmatch $excludedNamePattern -and
        -not [string]::IsNullOrWhiteSpace($_.carbohydrateGrams) -and
        -not [string]::IsNullOrWhiteSpace($_.proteinGrams) -and
        -not [string]::IsNullOrWhiteSpace($_.fatGrams)
    } |
    Sort-Object normalizedName, id -Unique

$quotas = [ordered]@{
    '밥류' = 28
    '죽 및 스프류' = 15
    '면 및 만두류' = 18
    '국 및 탕류' = 28
    '찌개 및 전골류' = 22
    '구이류' = 20
    '볶음류' = 25
    '조림류' = 15
    '찜류' = 12
    '전·적 및 부침류' = 15
    '튀김류' = 8
    '빵 및 과자류' = 8
    '생채·무침류' = 6
}

$selected = New-Object System.Collections.Generic.List[object]
$seenNames = @{}
$baseNames.Keys | ForEach-Object { $seenNames[$_] = $true }
foreach ($category in $quotas.Keys) {
    $foods | Where-Object { $_.category -eq $category } | ForEach-Object {
        if (($selected | Where-Object category -eq $category).Count -ge $quotas[$category]) { return }
        if (-not $seenNames.ContainsKey($_.normalizedName)) {
            $selected.Add($_)
            $seenNames[$_.normalizedName] = $true
        }
    }
}

# Add official composite dishes from underrepresented meal families. Their source names use
# K-FIND taxonomy prefixes; only that prefix is removed for the user-facing menu name.
$supplementalSpecs = @(
    @{ Pattern = '^샐러드_'; Count = 8; Kind = '샐러드' },
    @{ Pattern = '^샌드위치_'; Count = 6; Kind = '샌드위치' },
    @{ Pattern = '^또띠아_.*랩'; Count = 4; Kind = '랩' },
    @{ Pattern = '^토스트_'; Count = 4; Kind = '토스트' },
    @{ Pattern = '^스파게티_간편조리세트_.*파스타'; Count = 6; Kind = '파스타' },
    @{ Pattern = '^기타음료_마시는 요거트'; Count = 4; Kind = '요거트' },
    @{ Pattern = '^과ㆍ채주스_.*생과일'; Count = 4; Kind = '과일' }
)
$supplementalFoods = Import-Csv -LiteralPath $foodPath | Where-Object {
    $_.sourceType -eq 'K-FIND' -and $_.unit -eq 'g' -and [double]$_.energyKcal -gt 0 -and
    $_.name -notmatch '전여친'
}
foreach ($spec in $supplementalSpecs) {
    $added = 0
    foreach ($food in ($supplementalFoods | Where-Object { $_.name -match $spec.Pattern } | Sort-Object normalizedName, id)) {
        if ($added -ge $spec.Count) { break }
        $displayName = $food.name -replace '^[^_]+_간편조리세트_', '' -replace '^[^_]+_', ''
        if ($spec.Kind -eq '샐러드' -and $displayName -notmatch '샐러드') { $displayName += ' 샐러드' }
        if ($spec.Kind -eq '랩' -and $displayName -notmatch '랩') { $displayName += ' 랩' }
        $displayName = ($displayName -replace '\s+', ' ').Trim()
        $normalizedDisplay = $displayName.ToLowerInvariant() -replace '[^0-9a-z가-힣]', ''
        if (-not $seenNames.ContainsKey($normalizedDisplay)) {
            $food | Add-Member -NotePropertyName recommendationDisplayName -NotePropertyValue $displayName -Force
            $selected.Add($food)
            $seenNames[$normalizedDisplay] = $true
            $added++
        }
    }
}

# Keep the catalog above the requested floor even if a future official dataset removes a row.
if ($selected.Count -lt 220) {
    $foods | Where-Object { $quotas.Contains($_.category) } | ForEach-Object {
        if ($selected.Count -ge 220) { return }
        if (-not $seenNames.ContainsKey($_.normalizedName)) {
            $selected.Add($_)
            $seenNames[$_.normalizedName] = $true
        }
    }
}
if ($selected.Count -lt 220) { throw "Only $($selected.Count) distinct official dishes are available." }

$now = '1787842800000'
$templates = New-Object System.Collections.Generic.List[object]
$ingredients = New-Object System.Collections.Generic.List[object]
$baseTemplates | ForEach-Object { $templates.Add($_) }
$baseIngredients | ForEach-Object { $ingredients.Add($_) }

foreach ($food in $selected) {
    $stableSuffix = ($food.sourceFoodCode.ToLowerInvariant() -replace '[^0-9a-z-]', '-')
    $templateId = "kfind-catalog-$stableSuffix"
    $category = $food.category
    $displayName = if ($food.recommendationDisplayName) { $food.recommendationDisplayName } else { $food.name }
    $mealTypes = switch ($category) {
        '죽 및 스프류' { '|BREAKFAST|LUNCH|DINNER|' }
        '빵 및 과자류' { '|BREAKFAST|SNACK|' }
        '밥류' { '|BREAKFAST|LUNCH|DINNER|' }
        '국 및 탕류' { '|BREAKFAST|LUNCH|DINNER|' }
        '튀김류' { '|LUNCH|DINNER|SNACK|' }
        default { '|LUNCH|DINNER|' }
    }
    if ($displayName -match '떡|과일|요거트|토스트|샌드위치|랩') { $mealTypes = '|BREAKFAST|SNACK|' }

    $hash = [Math]::Abs($food.sourceFoodCode.GetHashCode()) % 3
    $mode = @('COOK', 'DINING_OUT', 'CONVENIENCE')[$hash]
    $minutes = if ($mode -eq 'COOK') { 25 } elseif ($mode -eq 'DINING_OUT') { 10 } else { 5 }
    $cost = if ($category -match '구이|찜|전골') { 'HIGH' } elseif ($category -match '국|죽|밥') { 'LOW' } else { 'MEDIUM' }
    $amount = switch -Regex ($category) {
        '^밥류$' { 300; break }
        '죽' { 400; break }
        '면' { 450; break }
        '국|찌개|전골' { 400; break }
        '빵' { 150; break }
        default { 250 }
    }
    $minimum = [Math]::Round($amount * 0.5, 1)
    $maximum = [Math]::Round($amount * 1.5, 1)

    $templates.Add([pscustomobject]@{
        id = $templateId
        name = $displayName
        supportedMealTypes = $mealTypes
        preparationMinutes = $minutes
        costLevel = $cost
        tags = "|$mode|INGREDIENTS_INCOMPLETE|"
        allergens = '|UNKNOWN|'
        excludedDietTypes = ''
        cuisineType = 'KOREAN'
        source = '식품영양성분 데이터베이스 (K-FIND) 2026-08-28'
        createdAt = $now
        updatedAt = $now
    })
    $ingredients.Add([pscustomobject]@{
        id = ''
        mealTemplateId = $templateId
        foodItemId = $food.id
        amount = $amount
        unit = 'g'
        adjustable = 'true'
        minimumAmount = $minimum
        maximumAmount = $maximum
        adjustmentStep = 10
    })
}

function Write-Utf8Csv([string]$Path, [object[]]$Rows) {
    $lines = $Rows | ConvertTo-Csv -NoTypeInformation
    [System.IO.File]::WriteAllLines($Path, $lines, $utf8NoBom)
}

Write-Utf8Csv $templatePath $templates
Write-Utf8Csv $ingredientPath $ingredients
Write-Output "templates=$($templates.Count); ingredients=$($ingredients.Count); generated=$($selected.Count)"
