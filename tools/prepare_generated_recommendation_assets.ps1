param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath,
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [int]$FoodSize = 960,
    [int]$JpegQuality = 84,
    [string]$AuditPath = 'app/build/generated-recommendation-image-audit.csv'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$destination = Join-Path $ProjectRoot 'app/src/main/res/drawable-nodpi'
New-Item -ItemType Directory -Force -Path $destination | Out-Null

$jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
    Where-Object MimeType -eq 'image/jpeg'
$qualityEncoder = [System.Drawing.Imaging.Encoder]::Quality

$auditRows = Import-Csv -LiteralPath $ManifestPath | ForEach-Object {
    $source = $_.sourcePath
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Generated source does not exist: $source"
    }

    $resourceName = 'rec_' + ($_.templateId.ToLowerInvariant() -replace '[^a-z0-9_]', '_')
    $target = Join-Path $destination "$resourceName.jpg"
    $input = [System.Drawing.Image]::FromFile($source)
    try {
        $sourceResolution = "$($input.Width)x$($input.Height)"
        if ($input.Width -lt $FoodSize -or $input.Height -lt $FoodSize) {
            throw "Upscaling is not allowed: $source ($($input.Width)x$($input.Height))"
        }
        $output = New-Object System.Drawing.Bitmap($FoodSize, $FoodSize)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($output)
            try {
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.DrawImage($input, 0, 0, $FoodSize, $FoodSize)
            } finally {
                $graphics.Dispose()
            }
            $parameters = New-Object System.Drawing.Imaging.EncoderParameters(1)
            $parameters.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter($qualityEncoder, [long]$JpegQuality)
            $output.Save($target, $jpegCodec, $parameters)
            $parameters.Dispose()
        } finally {
            $output.Dispose()
        }
    } finally {
        $input.Dispose()
    }

    [pscustomobject]@{
        templateId = $_.templateId
        menuName = $_.menuName
        resourceName = $resourceName
        imageFilename = "$resourceName.jpg"
        sourceResolution = $sourceResolution
        appAssetResolution = "${FoodSize}x${FoodSize}"
        exactMatch = 'GENERATED_EXACT'
        reusedCookingGroup = 'false'
        sourceReference = 'OpenAI built-in image generation; project-local optimized derivative'
    }
}

$auditPath = if ([System.IO.Path]::IsPathRooted($AuditPath)) { $AuditPath } else { Join-Path $ProjectRoot $AuditPath }
$replacedIds = @($auditRows.templateId)
$existingRows = if (Test-Path -LiteralPath $auditPath) {
    @(Import-Csv -LiteralPath $auditPath | Where-Object templateId -NotIn $replacedIds)
} else {
    @()
}
@($existingRows) + @($auditRows) |
    Sort-Object templateId |
    Export-Csv -LiteralPath $auditPath -NoTypeInformation -Encoding UTF8
