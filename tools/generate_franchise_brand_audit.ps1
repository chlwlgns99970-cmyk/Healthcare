param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$Python = "python"
)
$ErrorActionPreference = "Stop"
& $Python -X utf8 (Join-Path $PSScriptRoot "generate_franchise_brand_audit.py") --project-root $ProjectRoot
if ($LASTEXITCODE -ne 0) { throw "Franchise audit failed: $LASTEXITCODE" }
