param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [int]$Columns = 5,
    [int]$RowsPerSheet = 6
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$catalog = Import-Csv -LiteralPath (Join-Path $ProjectRoot 'app/src/main/assets/fooddata/meal_templates.csv')
$drawables = Join-Path $ProjectRoot 'app/src/main/res/drawable-nodpi'
$outputDirectory = Join-Path $ProjectRoot 'app/build/recommendation-image-contact-sheets'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$resolverText = Get-Content -LiteralPath (Join-Path $ProjectRoot 'app/src/main/java/com/example/healthcare/ui/RecommendationImageResolver.kt')
$legacyMap = @{}
$resolverText | ForEach-Object {
    if ($_ -match '^\s*"([^"]+)"\s+to\s+R\.drawable\.([a-z0-9_]+),?\s*$') {
        $legacyMap[$Matches[1]] = $Matches[2]
    }
}

$items = foreach ($row in $catalog) {
    $generatedResourceName = 'rec_' + ($row.id.ToLowerInvariant() -replace '[^a-z0-9_]', '_')
    $generatedFile = Get-ChildItem -LiteralPath $drawables -File |
        Where-Object BaseName -EQ $generatedResourceName | Select-Object -First 1
    $resourceName = if ($generatedFile) { $generatedResourceName } else { $legacyMap[$row.id] }
    $file = Get-ChildItem -LiteralPath $drawables -File | Where-Object BaseName -EQ $resourceName | Select-Object -First 1
    if ($file) { [pscustomobject]@{ id = $row.id; name = $row.name; file = $file.FullName } }
}

$cellWidth = 240
$imageHeight = 190
$labelHeight = 54
$cellHeight = $imageHeight + $labelHeight
$perSheet = $Columns * $RowsPerSheet
$font = New-Object System.Drawing.Font('Malgun Gothic', 12, [System.Drawing.FontStyle]::Bold)
$smallFont = New-Object System.Drawing.Font('Malgun Gothic', 8)

try {
    for ($offset = 0; $offset -lt $items.Count; $offset += $perSheet) {
        $page = [int]([math]::Floor($offset / $perSheet) + 1)
        $bitmap = [System.Drawing.Bitmap]::new(
            [int]($cellWidth * $Columns),
            [int]($cellHeight * $RowsPerSheet)
        )
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([System.Drawing.Color]::FromArgb(250, 247, 240))
                $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
                for ($index = 0; $index -lt $perSheet -and ($offset + $index) -lt $items.Count; $index++) {
                    $item = $items[$offset + $index]
                    $x = ($index % $Columns) * $cellWidth
                    $y = [math]::Floor($index / $Columns) * $cellHeight
                    $image = [System.Drawing.Image]::FromFile($item.file)
                    try { $graphics.DrawImage($image, $x, $y, $cellWidth, $imageHeight) } finally { $image.Dispose() }
                    $graphics.DrawString($item.name, $font, [System.Drawing.Brushes]::Black, $x + 5, $y + $imageHeight + 3)
                    $graphics.DrawString($item.id, $smallFont, [System.Drawing.Brushes]::DimGray, $x + 5, $y + $imageHeight + 29)
                }
            } finally { $graphics.Dispose() }
            $path = Join-Path $outputDirectory ('recommendation-images-{0:D2}.jpg' -f $page)
            $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Jpeg)
        } finally { $bitmap.Dispose() }
    }
} finally {
    $font.Dispose()
    $smallFont.Dispose()
}

Write-Output "Created $([math]::Ceiling($items.Count / $perSheet)) sheets for $($items.Count) mapped templates."
