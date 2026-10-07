param([switch]$Details)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$recipeCache = Join-Path $workspace 'app\build\food-quality-qa\recipe-source'
New-Item -ItemType Directory -Path $recipeCache -Force | Out-Null
$encoding = [System.Text.UTF8Encoding]::new($false)
if (-not $Details) {
    # Public catalogue reports 970 foods. Finite pagination, no guessed content IDs.
    1..10 | ForEach-Object {
        $page = $_
        $target = Join-Path $recipeCache "kto-index-$page.json"
        if (-not (Test-Path -LiteralPath $target)) {
            $url = "https://english.visitkorea.or.kr/svc/sp/food/ext/getFoodsList.do?page=$page&pageSize=100&contsGubun=SE4&bFirst=true"
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 25
            [System.IO.File]::WriteAllText($target, $response.Content, $encoding)
        }
    }
    Write-Output 'KTO public food catalogue: 10 pages captured'
} else {
    $requests = Get-Content -LiteralPath (Join-Path $recipeCache 'kto-detail-requests.json') -Encoding UTF8 | ConvertFrom-Json
    $count = 0
    foreach ($item in $requests) {
        $target = Join-Path $recipeCache ("kto-food-" + $item.sourceId + '.html')
        if (-not (Test-Path -LiteralPath $target)) {
            try {
                $response = Invoke-WebRequest -Uri $item.sourceUrl -UseBasicParsing -TimeoutSec 25
                [System.IO.File]::WriteAllText($target, $response.Content, $encoding)
            } catch {
                Write-Output ("KTO " + $item.sourceId + ': ' + $_.Exception.Message)
            }
        }
        $count++
        if ($count % 25 -eq 0) { Write-Output "KTO exact-name pages: $count / $($requests.Count)" }
    }
    Write-Output "KTO exact-name pages done: $count"
}
