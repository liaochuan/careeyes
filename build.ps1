param(
    [string]$Abi = "arm64-v8a",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot

function Get-ConfigValue([string]$Path, [string]$Key) {
    if (-not (Test-Path $Path)) { return $null }
    $line = Get-Content -LiteralPath $Path -Encoding UTF8 |
        Where-Object { $_.TrimStart().StartsWith("$Key=") } |
        Select-Object -First 1
    if (-not $line) { return $null }
    return $line.Substring($line.IndexOf('=') + 1).Trim()
}

function UnescapePropertyPath([string]$Value) {
    if (-not $Value) { return $null }
    return $Value.Replace('\:', ':').Replace('\\', '\')
}

Push-Location $projectRoot
try {
    if (-not $env:JAVA_HOME) {
        $jdk = Get-ConfigValue (Join-Path $projectRoot "local.properties") "build.javaHome"
        if (-not $jdk) {
            $jdk = Get-ConfigValue (Join-Path $projectRoot "gradle.properties") "org.gradle.java.home"
        }
        if ($jdk -and (Test-Path $jdk)) {
            $env:JAVA_HOME = $jdk
        }
    }

    if (-not $env:ANDROID_HOME) {
        $sdk = UnescapePropertyPath (Get-ConfigValue (Join-Path $projectRoot "local.properties") "sdk.dir")
        if ($sdk) {
            $env:ANDROID_HOME = $sdk
        }
    }
    if (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) {
        $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    }
    if (-not $env:ANDROID_HOME) {
        throw "ANDROID_HOME is not set and sdk.dir was not found in local.properties."
    }

    Write-Host "Building CareEyes release for ABI: $Abi"
    if ($Clean) {
        Write-Host "Clean build enabled"
    }

    $gradleArgs = @(":app:distRelease", "-Pabi=$Abi", "--console=plain")
    if ($Clean) {
        $gradleArgs = @("clean") + $gradleArgs
    }

    & (Join-Path $projectRoot "gradlew.bat") @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $distDir = Join-Path $projectRoot "dist"
    $abiSuffix = (($Abi -split ',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }) -join '-'
    $apk = Get-ChildItem $distDir -Filter "careeyes-*-$abiSuffix.apk" -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $apk) {
        throw "No APK for ABI $Abi was found in $distDir"
    }

    Write-Host ""
    Write-Host "APK : $($apk.FullName)"
    Write-Host "Size: $([Math]::Round($apk.Length / 1MB, 2)) MB"

    $buildTools = Get-ChildItem (Join-Path $env:ANDROID_HOME "build-tools") -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($buildTools) {
        $apksigner = Join-Path $buildTools.FullName "apksigner.bat"
        if (Test-Path $apksigner) {
            & $apksigner verify --verbose $apk.FullName
            if ($LASTEXITCODE -ne 0) {
                throw "APK signature verification failed"
            }
        }
    }
} finally {
    Pop-Location
}
