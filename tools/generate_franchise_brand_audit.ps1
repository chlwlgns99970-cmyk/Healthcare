param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$catalogPath = Join-Path $ProjectRoot "app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt"
$kfindPath = Join-Path $ProjectRoot "app/src/main/assets/fooddata/food_items.csv"
$officialPath = Join-Path $ProjectRoot "app/src/main/assets/fooddata/franchise_official_items.csv"
$outputPath = Join-Path $ProjectRoot "data-source/franchise/brand-audit.csv"

function Get-ParenthesisDelta {
    param([string]$Line)

    $delta = 0
    $inString = $false
    $escaped = $false
    foreach ($character in $Line.ToCharArray()) {
        if ($escaped) {
            $escaped = $false
            continue
        }
        if ($character -eq '\') {
            $escaped = $true
            continue
        }
        if ($character -eq '"') {
            $inString = -not $inString
            continue
        }
        if (-not $inString) {
            if ($character -eq '(') { $delta++ }
            if ($character -eq ')') { $delta-- }
        }
    }
    return $delta
}

$blocks = [System.Collections.Generic.List[string]]::new()
$current = ""
$depth = 0
foreach ($line in Get-Content $catalogPath) {
    if (-not $current -and $line -notmatch '^\s*FranchiseBrand\(') {
        continue
    }
    if (-not $current) {
        $current = $line.Trim()
        $depth = Get-ParenthesisDelta $line
    } else {
        $current += " " + $line.Trim()
        $depth += Get-ParenthesisDelta $line
    }
    if ($depth -eq 0) {
        $blocks.Add($current.TrimEnd(','))
        $current = ""
    }
}

$kfindRows = @(Import-Csv $kfindPath | Where-Object { $_.sourceType -eq "K-FIND" -and $_.brand })
$officialRows = @(Import-Csv $officialPath)

$auditRows = foreach ($block in $blocks) {
    $head = [regex]::Match(
        $block,
        'FranchiseBrand\("(?<name>[^"]+)",\s*"(?<category>[^"]+)",\s*"(?<url>[^"]+)"'
    )
    if (-not $head.Success) { continue }

    $name = $head.Groups['name'].Value
    $category = $head.Groups['category'].Value
    $url = $head.Groups['url'].Value
    $aliasMatch = [regex]::Match($block, 'setOf\((?<values>[^)]*)\)')
    $aliases = if ($aliasMatch.Success) {
        @([regex]::Matches($aliasMatch.Groups['values'].Value, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
    } else {
        @()
    }
    $menuMatch = [regex]::Match(
        $block,
        'officialMenuNames\s*=\s*listOf\((?<values>.*?)\)',
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    $menuReferences = if ($menuMatch.Success) {
        @([regex]::Matches($menuMatch.Groups['values'].Value, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
    } else {
        @()
    }
    $machineReadableNutrition = $block -match ',\s*true\s*[,)]'

    $brandKfindRows = @($kfindRows | Where-Object { $_.brand -eq $name })
    $brandOfficialRows = @($officialRows | Where-Object { $_.brand -eq $name })
    $nutritionRows = @($brandKfindRows) + @($brandOfficialRows)
    $source = if ($brandKfindRows.Count -gt 0 -and $brandOfficialRows.Count -gt 0) {
        "K-FIND + OFFICIAL-BRAND-NUTRITION"
    } elseif ($brandKfindRows.Count -gt 0) {
        "K-FIND"
    } elseif ($brandOfficialRows.Count -gt 0) {
        "OFFICIAL-BRAND-NUTRITION"
    } else {
        "OFFICIAL-BRAND-REGISTRY"
    }

    [pscustomobject]@{
        category = $category
        officialBrandName = $name
        aliases = ($aliases -join '|')
        officialUrl = $url
        verifiedAt = "2026-09-27"
        nutritionSource = $source
        recordableMenuCount = $nutritionRows.Count
        kcalMenuCount = @($nutritionRows | Where-Object { $_.energyKcal }).Count
        macroCompleteMenuCount = @($nutritionRows | Where-Object {
            $_.carbohydrateGrams -and $_.proteinGrams -and $_.fatGrams
        }).Count
        officialMenuNameReferenceCount = $menuReferences.Count
        officialMenuNameReferences = ($menuReferences -join '|')
        officialSourceForm = if ($machineReadableNutrition) {
            "official nutrition page/file or K-FIND snapshot"
        } else {
            "official website/menu page"
        }
        runtimeAutoUpdate = "false"
        updateMode = "snapshot-only"
        snapshotOnlyReason = if ($machineReadableNutrition) {
            "No verified unauthenticated stable payload is connected; K-FIND runtime checks metadata only"
        } else {
            "No verified unauthenticated stable machine-readable nutrition feed"
        }
    }
}

$auditRows | Export-Csv $outputPath -NoTypeInformation -Encoding utf8
Write-Output "Wrote $($auditRows.Count) franchise brand audit rows to $outputPath"
