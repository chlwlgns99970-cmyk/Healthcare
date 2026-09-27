param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$assetRoot = Join-Path $ProjectRoot 'app/src/main/assets/fooddata'
$output = Join-Path $ProjectRoot 'data-source/recommendation/official-standard-recipe-candidates.csv'
$foods = @{}
@('food_items.csv', 'product_items.csv', 'franchise_official_items.csv') | ForEach-Object {
    Import-Csv (Join-Path $assetRoot $_) | ForEach-Object { $foods[$_.id] = $_ }
}
$templates = @{}
Import-Csv (Join-Path $assetRoot 'meal_templates.csv') | ForEach-Object { $templates[$_.id] = $_ }

function Normalize-Name([string]$value) {
    ($value.ToLowerInvariant() -replace '[^0-9a-z가-힣]', '')
}

$requests = Import-Csv (Join-Path $assetRoot 'meal_template_ingredients.csv') | ForEach-Object {
    $food = $foods[$_.foodItemId]
    if ($food) {
        [pscustomobject]@{
            stableTemplateId = $_.mealTemplateId
            menuName = $templates[$_.mealTemplateId].name
            sourceFoodCode = $food.sourceFoodCode
            sourceFoodName = $food.name
            queryName = Normalize-Name $food.name
        }
    }
}

$matches = $requests | ForEach-Object -Parallel {
    $request = $_
    try {
        $encoded = [Uri]::EscapeDataString($request.queryName)
        $url = "https://openapi.foodsafetykorea.go.kr/api/sample/COOKRCP01/json/1/20/RCP_NM=$encoded"
        $response = Invoke-RestMethod -Uri $url -Method Get
        $resultCode = $response.COOKRCP01.RESULT.CODE
        if ($resultCode -and $resultCode -notin @('INFO-000', 'INFO-200')) {
            throw "COOKRCP01 returned $resultCode: $($response.COOKRCP01.RESULT.MSG)"
        }
        foreach ($row in @($response.COOKRCP01.row)) {
            if (-not $row) { continue }
            $officialNormalized = $row.RCP_NM.ToLowerInvariant() -replace '[^0-9a-z가-힣]', ''
            if ($officialNormalized -eq $request.queryName) {
                [pscustomobject]@{
                    stableTemplateId = $request.stableTemplateId
                    menuName = $request.menuName
                    sourceFoodCode = $request.sourceFoodCode
                    sourceFoodName = $request.sourceFoodName
                    officialRecipeId = $row.RCP_SEQ
                    officialRecipeName = $row.RCP_NM
                    officialIngredients = $row.RCP_PARTS_DTLS
                    officialSource = "식품의약품안전처 조리식품의 레시피 DB COOKRCP01:$($row.RCP_SEQ)"
                    verifiedAt = '2026-09-27'
                }
            }
        }
    } catch {
        Write-Warning "Official recipe lookup failed for $($request.stableTemplateId): $($_.Exception.Message)"
    }
} -ThrottleLimit 8

$sortedMatches = @($matches | Sort-Object stableTemplateId -Unique)
if ($sortedMatches.Count -eq 0) {
    '"stableTemplateId","menuName","sourceFoodCode","sourceFoodName","officialRecipeId","officialRecipeName","officialIngredients","officialSource","verifiedAt"' |
        Set-Content -Encoding UTF8 -Path $output
} else {
    $sortedMatches | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $output
}
Write-Output "Wrote $(@($matches).Count) exact official recipe candidates to $output"
