[CmdletBinding()]
param(
    [switch]$Offline,
    [string]$BuildRoot = (Join-Path $env:TEMP 'PTrack-auto-minimal-build')
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$javaCandidates = @($env:JAVA_HOME)
$javaCandidates += @(Get-ChildItem "$env:USERPROFILE/.jdks/*" -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
$javaCandidates += @(Get-ChildItem 'C:/Program Files/Android/Android Studio*/jbr' -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
$testJava = $javaCandidates | Where-Object {
    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin/javac.exe')) -and
    (Test-Path -LiteralPath (Join-Path $_ 'release')) -and
    ((Get-Content -LiteralPath (Join-Path $_ 'release')) -match '^JAVA_VERSION="17[.]')
} | Select-Object -First 1
if (-not $testJava) { throw 'A complete JDK 17 is required. Set JAVA_HOME to its directory.' }
$env:JAVA_HOME = $testJava
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle' }
$taskArguments = @('--no-daemon', "-PptrackBuildRoot=$BuildRoot", ':app:testDebugUnitTest')
if ($Offline) { $taskArguments = @('--offline') + $taskArguments }
Push-Location $taskRoot
try {
    & (Join-Path $taskRoot 'gradlew.bat') @taskArguments
    if ($LASTEXITCODE -ne 0) { throw 'Kotlin core tests failed; inspect the Gradle test report.' }
} finally { Pop-Location }
