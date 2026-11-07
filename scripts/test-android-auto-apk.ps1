[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$DebugApk,
    [Parameter(Mandatory=$true)][string]$ReleaseApk,
    [string]$CarTestApk,
    [string]$SdkPath
)
$ErrorActionPreference = 'Stop'
if (-not $SdkPath) {
    $properties = Join-Path (Split-Path -Parent $PSScriptRoot) 'local.properties'
    if (Test-Path -LiteralPath $properties) {
        $sdkLine = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $SdkPath = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
    if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
    if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
}
if (-not $SdkPath) { throw 'Set sdk.dir in local.properties, ANDROID_HOME or -SdkPath.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkPath 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe')) } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No installed stable Android build-tools with aapt.exe in $SdkPath" }
$aaptPath = Join-Path $buildTools.FullName 'aapt.exe'

function Dump-Apk([string]$Apk, [string]$Mode, [string]$Resource) {
    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK missing: $Apk" }
    $arguments = @('dump')
    if ($Mode -eq 'resources') { $arguments += '--values' }
    $arguments += @($Mode, $Apk)
    if ($Resource) { $arguments += $Resource }
    $dump = & $aaptPath @arguments
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect $Apk ($Mode $Resource)" }
    return ($dump -join "`n")
}

# Limit every component check to its own XML element, not a neighbouring service.
function Get-Element([string]$Dump, [string]$Element, [string]$Name) {
    $lines = $Dump -split "`n"
    $found = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -notmatch ('^( *)E: ' + [regex]::Escape($Element) + '(?: |$)')) { continue }
        $indent = $Matches[1].Length
        $end = $i + 1
        while ($end -lt $lines.Count) {
            if ($lines[$end] -match '^( *)E:' -and $Matches[1].Length -le $indent) { break }
            $end++
        }
        $block = $lines[$i..($end - 1)] -join "`n"
        if (-not $Name -or (Get-Attribute $block 'android:name') -match ('^"' + [regex]::Escape($Name) + '"')) {
            $found += $block
        }
    }
    if ($found.Count -ne 1) { throw "Expected exactly one $Element '$Name', found $($found.Count)" }
    return $found[0]
}

function Get-Attribute([string]$Element, [string]$Name) {
    $lines = $Element -split "`n"
    for ($i = 1; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^ *E:') { break }
        if ($lines[$i] -match ('^ *A: ' + [regex]::Escape($Name) + '(?:\(0x[0-9a-f]+\))?=(.*)$')) {
            return $Matches[1]
        }
    }
    return ''
}

function Require-String([string]$Element, [string]$Attribute, [string]$Expected) {
    $value = Get-Attribute $Element $Attribute
    if ($value -notmatch ('^"' + [regex]::Escape($Expected) + '"')) {
        throw "Expected $Attribute='$Expected', got '$value'"
    }
}

function Require-Bool([string]$Element, [string]$Attribute, [bool]$Expected,
        [string]$Resources, [switch]$DefaultFalse) {
    $value = Get-Attribute $Element $Attribute
    if (-not $value -and $DefaultFalse) {
        if ($Expected) { throw "$Attribute is absent (defaults to false)" }
        return
    }
    $values = @()
    if ($value -match '^\(type 0x12\)(0x[0-9a-f]+)$') {
        $values += [Convert]::ToUInt32($Matches[1].Substring(2), 16)
    } elseif ($value -match '^@(0x[0-9a-f]+)$') {
        $resourceId = $Matches[1]
        # Inspect every configuration so qualified resources cannot silently
        # enable the simulation receiver in release.
        $entries = [regex]::Matches($Resources, '(?m)^ *resource ' + $resourceId + ' [^\r\n]*t=0x12 d=(0x[0-9a-f]+)')
        if ($entries.Count -eq 0) { throw "Cannot resolve boolean $Attribute=$value" }
        foreach ($entry in $entries) { $values += [Convert]::ToUInt32($entry.Groups[1].Value.Substring(2), 16) }
    } else { throw "Expected boolean $Attribute, got '$value'" }
    foreach ($number in $values) {
        if (($number -ne 0) -ne $Expected) { throw "$Attribute must be $Expected in every resource configuration" }
    }
}

function Test-CarApk([string]$Apk, [string]$PackageName, [string]$Label, [bool]$Debuggable) {
    $manifest = Dump-Apk $Apk 'xmltree' 'AndroidManifest.xml'
    $resources = Dump-Apk $Apk 'resources'
    Require-String (Get-Element $manifest 'manifest') 'package' $PackageName
    $application = Get-Element $manifest 'application'
    Require-String $application 'android:label' $Label
    Require-Bool $application 'android:debuggable' $Debuggable $resources -DefaultFalse

    $service = Get-Element $manifest 'service' 'cat.ptrack.car.PTrackCarAppService'
    Require-String $service 'android:label' $Label
    Require-Bool $service 'android:exported' $true $resources
    $null = Get-Element $service 'action' 'androidx.car.app.CarAppService'
    $null = Get-Element $service 'category' 'androidx.car.app.category.IOT'
    if ($manifest.Contains('androidx.car.app.category.POI')) { throw 'PTrack must not declare a POI category without POI functionality' }

    $metadata = Get-Element $manifest 'meta-data' 'com.google.android.gms.car.application'
    $descriptorId = Get-Attribute $metadata 'android:resource'
    if ($descriptorId -notmatch '^@(0x[0-9a-f]+)$') { throw 'Missing Android Auto descriptor resource reference' }
    $descriptorResourceId = $Matches[1]
    if ($resources -notmatch ('resource ' + $descriptorResourceId + ' [^\s]+:xml/automotive_app_desc:')) {
        throw 'Android Auto metadata does not reference automotive_app_desc'
    }
    $apiMetadata = Get-Element $manifest 'meta-data' 'androidx.car.app.minCarApiLevel'
    if ((Get-Attribute $apiMetadata 'android:value') -notmatch '^\(type 0x10\)0x1$') { throw 'Expected minimum Car API level 1' }
    # AGP can shorten release resource paths (for example res/oc.xml).
    # Follow the compiled resource table instead of assuming the source path.
    $descriptorPaths = [regex]::Matches($resources,
        '(?m)^ *resource ' + $descriptorResourceId + ' [^\r\n]*\r?\n\s+\(string8?\) "([^"]+)"')
    if ($descriptorPaths.Count -eq 0) { throw 'Cannot resolve compiled Android Auto descriptor path' }
    foreach ($descriptorPath in $descriptorPaths) {
        $descriptor = Dump-Apk $Apk 'xmltree' $descriptorPath.Groups[1].Value
        $null = Get-Element $descriptor 'automotiveApp'
        Require-String (Get-Element $descriptor 'uses') 'name' 'template'
    }

    $activity = Get-Element $manifest 'activity' 'cat.ptrack.MainActivity'
    $null = Get-Element $activity 'category' 'android.intent.category.LAUNCHER'
    $null = Get-Element $manifest 'service' 'cat.ptrack.RecordingService'
    $receiver = Get-Element $manifest 'receiver' 'cat.ptrack.car.CarTestReceiver'
    Require-Bool $receiver 'android:enabled' $Debuggable $resources
    Require-String $receiver 'android:permission' 'android.permission.DUMP'
    Write-Output "PASS: $PackageName / $Label; car service, template, IOT, metadata, mobile entry points; debuggable=$Debuggable, simulation receiver=$Debuggable."
}

Write-Output "Inspecting APKs with $aaptPath"
Test-CarApk $DebugApk 'cat.ptrack' 'PTrack' $true
Test-CarApk $ReleaseApk 'cat.ptrack' 'PTrack' $false
if ($CarTestApk) { Test-CarApk $CarTestApk 'cat.ptrack.cartest' 'PTrack Prova cotxe' $false }
Write-Output 'Static package validation only. This does not test host discovery, app trust, category eligibility, rendering or acceptance in a real vehicle.'
