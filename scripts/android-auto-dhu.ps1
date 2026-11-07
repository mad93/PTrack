[CmdletBinding()]
param(
    [ValidateSet('Doctor','Build','Install','Start','Run','Logs','Stop','Simulate')]
    [string]$Action = 'Doctor',
    [string]$Serial,
    [string]$SdkPath,
    [string]$JavaHome,
    [string]$BuildRoot = (Join-Path $env:TEMP 'PTrack-auto-minimal-build'),
    [ValidateRange(1024,65535)][int]$Port = 5277,
    [ValidateSet('drive','regen','charge','standstill','freeze','disconnect','reconnect','finish','real')]
    [string]$Scenario = 'real',
    [ValidateSet('Normal','Wide','Narrow')][string]$Profile = 'Normal',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$artifactDir = Join-Path $taskRoot 'artifacts/android-auto-dhu'
New-Item -ItemType Directory -Force $artifactDir | Out-Null

if (-not $SdkPath) {
    $localProperties = Join-Path $taskRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $SdkPath = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
    if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
    if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
}
if (-not $SdkPath -or -not (Test-Path -LiteralPath $SdkPath)) {
    throw 'Android SDK not found. Set sdk.dir in local.properties or pass -SdkPath.'
}
$adbPath = Join-Path $SdkPath 'platform-tools/adb.exe'
$dhuPath = Join-Path $SdkPath 'extras/google/auto/desktop-head-unit.exe'
$emulatorPath = Join-Path $SdkPath 'emulator/emulator.exe'
$taskConfigName = switch($Profile){'Wide'{'dhu/wide.ini'}'Narrow'{'dhu/narrow.ini'}default{'dhu/ptrack.ini'}}
$dhuConfig = Join-Path $PSScriptRoot $taskConfigName
if (-not (Test-Path -LiteralPath $adbPath)) { throw "ADB missing: $adbPath" }
# Use the SDK owner's existing ADB keys when invoked by a local automation account.
# No keys, Android security settings or global environment variables are changed.
$sdkOwnerProfile = $env:USERPROFILE
if ($SdkPath -match '^(.*)[\\/]AppData[\\/]Local[\\/]Android[\\/]Sdk[\\/]?$') {
    $sdkOwnerProfile = $Matches[1]
}
if (-not $env:ANDROID_USER_HOME) {
    $existingAndroidHome = Join-Path $sdkOwnerProfile '.android'
    if (Test-Path -LiteralPath $existingAndroidHome) { $env:ANDROID_USER_HOME = $existingAndroidHome }
}
function Invoke-Adb {
    param([string[]]$Arguments)
    $result = & $adbPath @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')" }
    return $result
}
function Select-Device {
    $rows = @(Invoke-Adb -Arguments @('devices','-l'))
    $rows | ForEach-Object { Write-Host $_ }
    $ready = @($rows | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($Serial) {
        if ($Serial -notin $ready) { throw "Device $Serial is not ready. Unlock it and authorize USB debugging (RSA)." }
        return $Serial
    }
    if ($ready.Count -eq 0) { throw 'No authorized Android device. Connect/unlock the Pixel, enable USB debugging and accept its RSA prompt.' }
    if ($ready.Count -ne 1) { throw 'Multiple devices: pass -Serial with the intended Pixel serial.' }
    return $ready[0]
}
function Build-App {
    $candidates = @($JavaHome, $env:JAVA_HOME)
    $candidates += @(Get-ChildItem "$sdkOwnerProfile/.jdks/*" -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
    $candidates += @(Get-ChildItem 'C:/Program Files/Android/Android Studio*/jbr' -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
    # Match gradle/gradle-daemon-jvm.properties (JDK 17).
    $selectedJava = $candidates | Where-Object {
        $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin/java.exe')) -and
        (Test-Path -LiteralPath (Join-Path $_ 'lib/jvm.cfg')) -and
        (Test-Path -LiteralPath (Join-Path $_ 'release')) -and
        ((Get-Content -LiteralPath (Join-Path $_ 'release')) -match '^JAVA_VERSION="17[.]')
    } | Select-Object -First 1
    if (-not $selectedJava) { throw 'A complete JDK 17 is required by gradle/gradle-daemon-jvm.properties. Pass -JavaHome.' }
    $env:JAVA_HOME = $selectedJava
    if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $sdkOwnerProfile '.gradle' }
    Write-Host "JAVA_HOME=$selectedJava"
    $gradleArguments = @('--no-daemon', "-PptrackBuildRoot=$BuildRoot", ':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:lintDebug')
    if ($Offline) { $gradleArguments = @('--offline') + $gradleArguments }
    Push-Location $taskRoot
    try {
        & (Join-Path $taskRoot 'gradlew.bat') @gradleArguments
        if ($LASTEXITCODE -ne 0) { throw 'Gradle failed; APK not installed.' }
    } finally { Pop-Location }
    Copy-Item -LiteralPath (Join-Path $BuildRoot 'app/outputs/apk/debug/app-debug.apk') -Destination (Join-Path $artifactDir 'PTrack-dhu-debug.apk')
    Copy-Item -LiteralPath (Join-Path $BuildRoot 'app/reports/lint-results-debug.html') -Destination $artifactDir
}
function Install-App([string]$DeviceSerial) {
    $apkPath = Join-Path $artifactDir 'PTrack-dhu-debug.apk'
    if (-not (Test-Path -LiteralPath $apkPath)) { throw 'Run -Action Build first.' }
    # Updating preserves data. Never uninstall to work around a signature mismatch.
    Invoke-Adb -Arguments @('-s',$DeviceSerial,'install','-r',$apkPath) | Write-Host
    Write-Host 'Local install for DHU development. Unknown sources does not authorize Car App Library apps in a real vehicle; see docs/ANDROID-AUTO-VEHICLE.md.'
    $services = Invoke-Adb -Arguments @('-s',$DeviceSerial,'shell','cmd','package','query-services','--components','-a','androidx.car.app.CarAppService','-p','cat.ptrack')
    $services | Write-Host
    if (($services -join "`n") -notmatch 'PTrackCarAppService') { throw 'PTrack car service not discoverable by Android PackageManager.' }
}
function Start-Dhu([string]$DeviceSerial) {
    if (-not (Test-Path -LiteralPath $dhuPath)) {
        throw 'Install Android Auto Desktop Head Unit Emulator in Android Studio > SDK Manager > SDK Tools (extras;google;auto).'
    }
    $aaPackage = Invoke-Adb -Arguments @('-s',$DeviceSerial,'shell','pm','path','com.google.android.projection.gearhead')
    if (($aaPackage -join '') -notmatch 'package:') { throw 'Android Auto is not installed on this device. A plain AVD is not an Android Auto host.' }
    $mappings = Invoke-Adb -Arguments @('forward','--list')
    $conflicts = @($mappings | Where-Object { $_ -match "^\S+\s+tcp:$Port\s" -and $_ -notmatch "^$([regex]::Escape($DeviceSerial))\s+tcp:$Port\s+tcp:5277$" })
    if ($conflicts.Count -gt 0) { throw "Local port $Port already belongs to another ADB forward. Choose -Port instead of replacing it." }
    Invoke-Adb -Arguments @('-s',$DeviceSerial,'forward',"tcp:$Port",'tcp:5277') | Write-Host
    Write-Host 'On the Pixel: Android Auto developer menu > Start head unit server. Keep phone unlocked; accept first-run dialogs yourself.'
    Write-Host "Launching official DHU using ADB port $Port. Close DHU to end this command."
    $logPath = Join-Path $artifactDir ("dhu-{0}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Push-Location (Split-Path -Parent $dhuPath)
    $previousErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue' # DHU writes normal startup diagnostics to stderr.
        & $dhuPath "--adb=$Port" "--config=$dhuConfig" 2>&1 | Tee-Object -FilePath $logPath
        $dhuExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
        Pop-Location
    }
    if ($dhuExit -ne 0) { throw "DHU exited with code $dhuExit. Check $logPath and the Pixel head unit server." }
    # DHU 2.0 can return exit code zero even when no link was established.
    if (Select-String -LiteralPath $logPath -Quiet -Pattern 'Failed to start Google Automotive Link') {
        throw "DHU did not establish a projection link. Start the Pixel head unit server and check $logPath."
    }
    if (Select-String -LiteralPath $logPath -Quiet -Pattern 'Failed to read from transport') {
        throw "Android Auto disconnected during negotiation. Check $logPath and -Action Logs; restart the Pixel head unit server before retrying. A TCP connection alone is not a successful projection."
    }
}

switch ($Action) {
    'Doctor' {
        Write-Host "SDK: $SdkPath"
        Invoke-Adb -Arguments @('version') | Write-Host
        Invoke-Adb -Arguments @('devices','-l') | Write-Host
        if (Test-Path -LiteralPath $dhuPath) { & $dhuPath --version } else { Write-Host 'DHU MISSING (SDK Manager > SDK Tools > Android Auto Desktop Head Unit Emulator)' }
        if (Test-Path -LiteralPath $emulatorPath) {
            $avds = @(& $emulatorPath -list-avds)
            if ($avds.Count) { $avds | Write-Host } else { Write-Host 'No AVD configured. Use the Pixel for the Android Auto host; DHU is the head unit.' }
        }
    }
    'Build' { Build-App }
    'Install' { $device = Select-Device; Install-App $device }
    'Start' { $device = Select-Device; Start-Dhu $device }
    'Run' { $device = Select-Device; Build-App; Install-App $device; Start-Dhu $device }
    'Logs' {
        $device = Select-Device
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        Invoke-Adb -Arguments @('-s',$device,'shell','dumpsys','package','cat.ptrack') | Set-Content (Join-Path $artifactDir "package-$stamp.txt")
        Invoke-Adb -Arguments @('-s',$device,'shell','dumpsys','package','com.google.android.projection.gearhead') | Set-Content (Join-Path $artifactDir "android-auto-$stamp.txt")
        Invoke-Adb -Arguments @('-s',$device,'logcat','-d','-v','threadtime') | Set-Content (Join-Path $artifactDir "logcat-$stamp.txt")
        Invoke-Adb -Arguments @('-s',$device,'logcat','-d','-s','PTrackCar:I','*:S') | Write-Host
        Write-Host "Logs saved locally in $artifactDir. Review private information before sharing."
    }
    'Simulate' {
        $device = Select-Device
        $result = Invoke-Adb -Arguments @('-s',$device,'shell','am','broadcast','-n','cat.ptrack/.car.CarTestReceiver','--es','scenario',$Scenario)
        $result | Write-Host
        if (($result -join "`n") -notmatch 'result=-1') { throw 'Test receiver did not accept the command. Install the dashboard debug APK; shell DUMP permission is required.' }
        Write-Host 'Display fixture only; Bluetooth, LIVE telemetry and trip database are unchanged. Use -Scenario real to restore OBD display.'
    }
    'Stop' {
        $device = Select-Device
        $mappings = Invoke-Adb -Arguments @('forward','--list')
        if (@($mappings | Where-Object { $_ -match "^$([regex]::Escape($device))\s+tcp:$Port\s+tcp:5277$" }).Count) {
            Invoke-Adb -Arguments @('-s',$device,'forward','--remove',"tcp:$Port") | Write-Host
        }
        Write-Host 'Close the DHU window and stop the head unit server on the Pixel. PTrack recording has not been stopped.'
    }
}
