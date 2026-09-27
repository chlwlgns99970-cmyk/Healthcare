param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$catalogPath = Join-Path $ProjectRoot 'app/src/main/assets/fooddata/meal_templates.csv'
$resolverPath = Join-Path $ProjectRoot 'app/src/main/java/com/example/healthcare/ui/RecommendationImageResolver.kt'
$drawablePath = Join-Path $ProjectRoot 'app/src/main/res/drawable-nodpi'
$outputPath = Join-Path $ProjectRoot 'app/build/recommendation-image-audit-final.csv'

$generatedAudit = @{}
Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'app/build') -Filter 'generated-recommendation-image-audit*.csv' |
    ForEach-Object { Import-Csv -LiteralPath $_.FullName } |
    ForEach-Object { $generatedAudit[$_.templateId] = $_ }

$legacyMap = @{}
Get-Content -LiteralPath $resolverPath | ForEach-Object {
    if ($_ -match '^\s*"([^"]+)"\s+to\s+R\.drawable\.([a-z0-9_]+),?\s*$') {
        $legacyMap[$Matches[1]] = $Matches[2]
    }
}

$rows = foreach ($template in Import-Csv -LiteralPath $catalogPath) {
    $generatedResourceName = 'rec_' + ($template.id.ToLowerInvariant() -replace '[^a-z0-9_]', '_')
    $generatedAsset = Get-ChildItem -LiteralPath $drawablePath -File |
        Where-Object BaseName -EQ $generatedResourceName | Select-Object -First 1
    $resourceName = if ($generatedAsset) { $generatedResourceName } else { $legacyMap[$template.id] }
    if (-not $resourceName) { throw "No image mapping for $($template.id)" }
    $asset = Get-ChildItem -LiteralPath $drawablePath -File | Where-Object BaseName -EQ $resourceName | Select-Object -First 1
    if (-not $asset) { throw "No drawable file for $($template.id): $resourceName" }
    $image = [System.Drawing.Image]::FromFile($asset.FullName)
    try { $resolution = "$($image.Width)x$($image.Height)" } finally { $image.Dispose() }
    $generated = $generatedAudit[$template.id]
    [pscustomobject]@{
        templateId = $template.id
        menuName = $template.name
        mealTypes = $template.supportedMealTypes
        imageFilename = $asset.Name
        sourceResolution = if ($generated) { $generated.sourceResolution } else { $resolution }
        appAssetResolution = $resolution
        exactMatch = if ($generatedAsset) { 'GENERATED_EXACT' } else { 'CURATED_EXACT' }
        reusedCookingGroup = 'false'
        sourceReference = if ($generatedAsset) { $generated.sourceReference } else { 'Existing curated project asset' }
        assetBytes = $asset.Length
    }
}

if ($rows.Count -ne 292) { throw "Unexpected template count: $($rows.Count)" }
if (($rows | Where-Object { -not $_.imageFilename }).Count -ne 0) { throw 'Missing image filename in audit' }
$rows | Export-Csv -LiteralPath $outputPath -NoTypeInformation -Encoding UTF8
Write-Output "Audit complete: $($rows.Count) templates, $((($rows | Measure-Object assetBytes -Sum).Sum)) bytes"
Write-Output $outputPath
