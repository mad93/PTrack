param(
    [Parameter(Mandatory=$true)][string]$DebugApk,
    [Parameter(Mandatory=$true)][string]$ReleaseApk,
    [string]$SdkPath = 'C:/Users/mad93/AppData/Local/Android/Sdk'
)
$ErrorActionPreference = 'Stop'
$aaptPath = Join-Path $SdkPath 'build-tools/34.0.0/aapt.exe'
function Dump-Apk([string]$Apk, [string]$Resource) {
    $dump = & $aaptPath dump xmltree $Apk $Resource
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect $Apk / $Resource" }
    return ($dump -join "`n")
}
function Require-Text([string]$Text, [string]$Expected) {
    if (-not $Text.Contains($Expected)) { throw "Missing APK configuration: $Expected" }
}
$debugManifest = Dump-Apk $DebugApk 'AndroidManifest.xml'
foreach ($expected in @('cat.ptrack.car.PTrackCarAppService',
        'androidx.car.app.CarAppService', 'androidx.car.app.category.IOT',
        'com.google.android.gms.car.application', 'androidx.car.app.minCarApiLevel',
        'cat.ptrack.MainActivity', 'cat.ptrack.RecordingService',
        'android.intent.category.LAUNCHER')) {
    Require-Text $debugManifest $expected
}
$serviceBlock = [regex]::Match($debugManifest,
    '(?s)E: service[^\r\n]*\r?\n(?:(?!E: service).)*cat\.ptrack\.car\.PTrackCarAppService(?:(?!E: service|E: activity).)*').Value
if ($serviceBlock -notmatch 'android:exported[^\r\n]*0xffffffff') {
    throw 'CarAppService is not exported in the APK'
}
$descriptor = Dump-Apk $DebugApk 'res/xml/automotive_app_desc.xml'
Require-Text $descriptor 'E: automotiveApp'
Require-Text $descriptor 'name="template"'
$releaseManifest = Dump-Apk $ReleaseApk 'AndroidManifest.xml'
if ($releaseManifest -match 'PTrackCarAppService|CarTestReceiver|com.google.android.gms.car.application') {
    throw 'Experimental car entry point leaked into release'
}
Require-Text $releaseManifest 'cat.ptrack.MainActivity'
Write-Output 'PASS: APK contains exported CarAppService, IOT, template descriptor, API metadata and mobile entry points; release has no experimental car entry point.'
Write-Output 'Static package validation only. This does not test host discovery, trust, rendering or the vehicle.'
