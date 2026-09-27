$ErrorActionPreference = 'Stop'

$userHome = [Environment]::GetFolderPath('UserProfile')
$androidDirectory = Join-Path $userHome '.android'
$gradleDirectory = Join-Path $userHome '.gradle'
$keyStoreFile = Join-Path $androidDirectory 'today-what-to-eat-release.jks'
$gradlePropertiesFile = Join-Path $gradleDirectory 'gradle.properties'
$keytool = 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'

if (Test-Path -LiteralPath $keyStoreFile) {
    throw 'Release keystore already exists; refusing to replace it.'
}
if (-not (Test-Path -LiteralPath $keytool)) {
    throw 'Android Studio keytool was not found.'
}

$existingProperties = if (Test-Path -LiteralPath $gradlePropertiesFile) {
    [System.IO.File]::ReadAllText($gradlePropertiesFile)
} else {
    ''
}
if ($existingProperties -match '(?m)^TODAY_RELEASE_(STORE_FILE|STORE_PASSWORD|KEY_PASSWORD)\s*=') {
    throw 'Release signing properties already exist; refusing to overwrite them.'
}

[System.IO.Directory]::CreateDirectory($androidDirectory) | Out-Null
[System.IO.Directory]::CreateDirectory($gradleDirectory) | Out-Null

$storePassword = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$keyPassword = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:TODAY_RELEASE_STORE_PASSWORD = $storePassword
$env:TODAY_RELEASE_KEY_PASSWORD = $keyPassword

try {
    & $keytool -genkeypair -noprompt -storetype JKS `
        -keystore $keyStoreFile -alias 'today-what-to-eat' `
        -keyalg RSA -keysize 3072 -validity 10000 `
        -dname 'CN=Today What To Eat, OU=Android, O=Personal, L=Seoul, ST=Seoul, C=KR' `
        -storepass:env TODAY_RELEASE_STORE_PASSWORD `
        -keypass:env TODAY_RELEASE_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $keyStoreFile)) {
        throw 'Release keystore generation failed.'
    }

    $prefix = if ($existingProperties.Length -gt 0 -and -not $existingProperties.EndsWith("`n")) { "`r`n" } else { '' }
    $propertyPath = $keyStoreFile.Replace('\', '/')
    $newProperties = $prefix + "TODAY_RELEASE_STORE_FILE=$propertyPath`r`n" +
        "TODAY_RELEASE_STORE_PASSWORD=$storePassword`r`n" +
        "TODAY_RELEASE_KEY_PASSWORD=$keyPassword`r`n"
    [System.IO.File]::AppendAllText(
        $gradlePropertiesFile,
        $newProperties,
        [System.Text.UTF8Encoding]::new($false)
    )

    if ($existingProperties.Length -eq 0) {
        $acl = Get-Acl -LiteralPath $gradlePropertiesFile
        $acl.SetAccessRuleProtection($true, $false)
        $user = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
        $userRule = [System.Security.AccessControl.FileSystemAccessRule]::new(
            $user, 'FullControl', 'Allow'
        )
        $systemRule = [System.Security.AccessControl.FileSystemAccessRule]::new(
            'NT AUTHORITY\SYSTEM', 'FullControl', 'Allow'
        )
        $acl.AddAccessRule($userRule)
        $acl.AddAccessRule($systemRule)
        Set-Acl -LiteralPath $gradlePropertiesFile -AclObject $acl
    }
    Write-Output 'Release keystore and private user Gradle properties created.'
} finally {
    Remove-Item Env:\TODAY_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\TODAY_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $storePassword = $null
    $keyPassword = $null
}
