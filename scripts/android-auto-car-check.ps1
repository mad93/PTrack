[CmdletBinding()]
param(
    [string]$Serial,
    [string]$SdkPath,
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')]
    [string]$PackageName = 'cat.ptrack'
)

# Read-only device diagnostics. This script never installs an APK, clears logs,
# changes an installer, stops recording, or changes Android Auto settings.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$reportDir = Join-Path $taskRoot "artifacts/android-auto-car-check/$stamp"
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$failures = New-Object 'System.Collections.Generic.List[string]'
$summary = New-Object 'System.Collections.Generic.List[string]'
$summary.Add("PTrack Android Auto diagnostics: $PackageName")
$summary.Add("Captured: $(Get-Date -Format o)")

function Invoke-DiagnosticAdb {
    param([string[]]$Arguments)
    # Windows PowerShell 5.1 represents native stderr as ErrorRecord objects.
    # Preserve diagnostics and the exit code without aborting the other captures.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& $adbPath @Arguments 2>&1 | ForEach-Object { "$_" })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{ Lines = $lines; ExitCode = $exitCode }
}

function Save-DeviceCapture {
    param([string]$Name, [string[]]$Arguments, [string]$LinePattern)
    $capture = Invoke-DiagnosticAdb -Arguments (@('-s', $device) + $Arguments)
    $savedLines = @($capture.Lines)
    if ($capture.ExitCode -ne 0) {
        $failures.Add("$Name failed (ADB exit $($capture.ExitCode)); see $Name.txt")
    }
    if ($LinePattern) {
        # Only retain relevant lines, not the complete device log buffer.
        # Apply this even if logcat fails after returning some buffered output.
        $savedLines = @($savedLines | Where-Object { $_ -match $LinePattern -or $_ -match '^(?:adb(?:\.exe)?:|error:|logcat:)' })
    }
    if ($savedLines.Count -eq 0) { $savedLines = @('(No matching output captured.)') }
    $savedLines | Set-Content -LiteralPath (Join-Path $reportDir "$Name.txt") -Encoding UTF8
    return $capture
}

function Get-PackageVersion {
    param([string[]]$Lines)
    $version = @($Lines | Where-Object { $_ -match '^\s*version(?:Name|Code)=' } | ForEach-Object { $_.Trim() })
    if ($version.Count -eq 0) { return 'not available (package missing or capture failed)' }
    return (($version | Select-Object -Unique) -join '; ')
}

try {
    if (-not $SdkPath) {
        $localProperties = Join-Path $taskRoot 'local.properties'
        if (Test-Path -LiteralPath $localProperties) {
            $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($sdkLine) { $SdkPath = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
        }
        if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
        if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
    }
    if (-not $SdkPath) { throw 'Android SDK not found. Set sdk.dir in local.properties or pass -SdkPath.' }
    $adbPath = Join-Path $SdkPath 'platform-tools/adb.exe'
    if (-not (Test-Path -LiteralPath $adbPath)) { throw "ADB missing: $adbPath" }

    $devicesCapture = Invoke-DiagnosticAdb -Arguments @('devices')
    if ($devicesCapture.ExitCode -ne 0) {
        $devicesCapture.Lines | Set-Content -LiteralPath (Join-Path $reportDir 'adb-error.txt') -Encoding UTF8
        throw 'ADB could not list devices. See adb-error.txt; run from your normal Windows user session.'
    }
    $ready = @($devicesCapture.Lines | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($Serial) {
        if ($Serial -notin $ready) { throw 'The selected device is not ready. Unlock it and authorize USB debugging (RSA).' }
        $device = $Serial
    } else {
        if ($ready.Count -eq 0) { throw 'No authorized device. Connect/unlock the phone and authorize USB debugging (RSA).' }
        if ($ready.Count -ne 1) { throw 'Multiple authorized devices. Run adb devices and pass -Serial for the intended phone.' }
        $device = $ready[0]
    }

    $androidVersion = Save-DeviceCapture -Name 'android-version' -Arguments @('shell', 'getprop', 'ro.build.version.release')
    $androidApi = Save-DeviceCapture -Name 'android-api' -Arguments @('shell', 'getprop', 'ro.build.version.sdk')
    $app = Save-DeviceCapture -Name 'app-package' -Arguments @('shell', 'dumpsys', 'package', $PackageName)
    $androidAuto = Save-DeviceCapture -Name 'android-auto-package' -Arguments @('shell', 'dumpsys', 'package', 'com.google.android.projection.gearhead')
    $installer = Save-DeviceCapture -Name 'installer' -Arguments @('shell', 'pm', 'list', 'packages', '-i', $PackageName)
    $services = Save-DeviceCapture -Name 'car-service-discovery' -Arguments @('shell', 'cmd', 'package', 'query-services', '--components', '-a', 'androidx.car.app.CarAppService', '-p', $PackageName)
    $null = Save-DeviceCapture -Name 'app-running-services' -Arguments @('shell', 'dumpsys', 'activity', 'services', $PackageName)
    $null = Save-DeviceCapture -Name 'android-auto-running-services' -Arguments @('shell', 'dumpsys', 'activity', 'services', 'com.google.android.projection.gearhead')
    $logPattern = [regex]::Escape($PackageName) + '|PTrackCar|CAR[._ ]VALIDATOR|AndroidAuto|gearhead|CarAppService|CarAppHost|androidx\.car\.app'
    $null = Save-DeviceCapture -Name 'car-logcat' -Arguments @('logcat', '-d', '-v', 'threadtime', '-t', '5000') -LinePattern $logPattern

    $summary.Add("Android: $($androidVersion.Lines -join ' '); API: $($androidApi.Lines -join ' ')")
    $summary.Add("App: $(Get-PackageVersion -Lines $app.Lines)")
    $summary.Add("Android Auto: $(Get-PackageVersion -Lines $androidAuto.Lines)")
    $exactPackage = '^package:' + [regex]::Escape($PackageName) + '(?:\s|$)'
    $installerLine = @($installer.Lines | Where-Object { $_ -match $exactPackage })
    if ($installerLine.Count -gt 0) {
        $summary.Add("Installer reported by PackageManager: $($installerLine -join '; ')")
    } else {
        $summary.Add('Installer: not available for the exact package.')
    }
    $installSource = @($app.Lines | Where-Object {
        $_ -match 'installerPackageName=|initiatingPackageName=|originatingPackageName='
    } | ForEach-Object { $_.Trim() })
    if ($installSource.Count -gt 0) {
        $summary.Add("Install source details: $($installSource -join '; ')")
    } else {
        $summary.Add('Initiating installer: not exposed by this package dump; do not infer it from installerPackageName.')
    }
    if ($services.ExitCode -ne 0) {
        $summary.Add('CarAppService discovery: capture failed.')
    } elseif (($services.Lines -join "`n") -match '/\S+') {
        $summary.Add('CarAppService discovery: PackageManager returned a component; see car-service-discovery.txt.')
    } else {
        $summary.Add('CarAppService discovery: no component returned. Check the installed APK and package enabled state.')
    }
} catch {
    $failures.Add($_.Exception.Message)
} finally {
    $summary.Add('The installer string and service discovery do not prove Android Auto admission or vehicle compatibility.')
    $summary.Add('For Car App Library apps, Android Auto Unknown sources does not replace a trusted installation source.')
    $summary.Add('Official vehicle testing: https://developer.android.com/training/cars/testing#test-in-real-vehicles')
    $summary.Add('No vehicle test is performed by this script. Capture soon after the failed connection; older logs may have expired.')
    $summary.Add('Raw package/service output and filtered logs are local only. Review them for private information before sharing.')
    if ($failures.Count -gt 0) {
        $summary.Add('Capture status: INCOMPLETE')
        foreach ($failure in $failures) { $summary.Add("ERROR: $failure") }
    } else {
        $summary.Add('Capture status: completed; vehicle result remains unverified.')
    }
    $summary | Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt') -Encoding UTF8
    $summary | Write-Output
    Write-Output "Local report: $reportDir"
}

if ($failures.Count -gt 0) { exit 1 }
