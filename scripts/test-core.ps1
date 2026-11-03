$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { 'C:/Program Files/Android/Android Studio/jbr/bin' }
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:/Users/mad93/AppData/Local/Android/Sdk' }
$androidJar = Join-Path $sdkPath 'platforms/android-34/android.jar'
$outputPath = Join-Path $taskRoot 'test-output'
New-Item -ItemType Directory -Force $outputPath | Out-Null
$sources = 'Sample','TripStats','ElmProtocol','Profile','Streams','Labels','ConsumptionModel','ConsumptionLearner' | ForEach-Object { Join-Path $taskRoot "app/src/main/java/cat/ptrack/$_.java" }
& "$javaBin/javac.exe" -encoding UTF-8 -cp $androidJar -d $outputPath @sources "$taskRoot/core-tests/CoreTests.java" "$taskRoot/core-tests/ConsumptionTests.java"
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
& "$javaBin/java.exe" -cp "$outputPath;$androidJar" cat.ptrack.CoreTests
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
& "$javaBin/java.exe" -cp "$outputPath;$androidJar" cat.ptrack.ConsumptionTests
if ($LASTEXITCODE -ne 0) { throw 'Consumption tests failed' }
