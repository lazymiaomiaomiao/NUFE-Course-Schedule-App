$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$env:JAVA_HOME = Join-Path $Root ".tools\jdk17\jdk-17.0.16+8"
$env:ANDROID_HOME = Join-Path $Root ".tools\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

Push-Location $Root
try {
    & ".tools\gradle\gradle-8.10.2\bin\gradle.bat" --no-daemon assembleDebug
} finally {
    Pop-Location
}
