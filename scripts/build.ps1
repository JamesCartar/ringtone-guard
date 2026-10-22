param([switch]$Install)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
$Jdk = Get-ChildItem "$Root/.tools/java" -Directory | Select-Object -First 1
if (-not $Jdk) { throw 'Missing JDK in .tools/java. See README.md.' }
$JavaBin = Join-Path $Jdk.FullName 'bin'
$Sdk = Join-Path $Root '.tools/android-sdk'
$Tools = Join-Path $Sdk 'build-tools/34.0.0'
$AndroidJar = Join-Path $Sdk 'platforms/android-32/android.jar'
function Run([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed with exit code $LASTEXITCODE" }
}
if (-not (Test-Path $AndroidJar)) { throw 'Missing Android SDK platform 32. See README.md.' }
$Build = Join-Path $Root 'build'
if (Test-Path $Build) { Remove-Item $Build -Recurse -Force }
New-Item "$Build/classes", "$Build/dex", "$Build/tests" -ItemType Directory -Force | Out-Null
Run "$JavaBin/javac.exe" @('-encoding', 'UTF-8', '-d', "$Build/tests", "$Root/app/src/main/java/com/family/ringtoneguard/GuardPolicy.java", "$Root/tests/com/family/ringtoneguard/GuardPolicyTest.java")
Run "$JavaBin/java.exe" @('-cp', "$Build/tests", 'com.family.ringtoneguard.GuardPolicyTest')
Run "$Tools/aapt2.exe" @('link', '-I', $AndroidJar, '--manifest', "$Root/app/src/main/AndroidManifest.xml", '--version-code', '1', '--version-name', '0.1', '-o', "$Build/resources.apk")
$Sources = @(Get-ChildItem "$Root/app/src/main/java" -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
Run "$JavaBin/javac.exe" (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', $AndroidJar, '-d', "$Build/classes") + $Sources)
$Classes = @(Get-ChildItem "$Build/classes" -Filter '*.class' -Recurse | ForEach-Object { $_.FullName })
Run "$JavaBin/java.exe" (@('-cp', "$Tools/lib/d8.jar", 'com.android.tools.r8.D8', '--lib', $AndroidJar, '--min-api', '26', '--output', "$Build/dex") + $Classes)
Run "$JavaBin/jar.exe" @('uf', "$Build/resources.apk", '-C', "$Build/dex", 'classes.dex')
Run "$Tools/zipalign.exe" @('-f', '4', "$Build/resources.apk", "$Build/aligned.apk")
$Key = Join-Path $Root '.tools/debug.keystore'
if (-not (Test-Path $Key)) {
    Run "$JavaBin/keytool.exe" @('-genkeypair', '-keystore', $Key, '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Android Debug,O=Development,C=US')
}
Run "$JavaBin/java.exe" @('-jar', "$Tools/lib/apksigner.jar", 'sign', '--ks', $Key, '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', "$Build/ringtone-guard-debug.apk", "$Build/aligned.apk")
Run "$JavaBin/java.exe" @('-jar', "$Tools/lib/apksigner.jar", 'verify', '--verbose', "$Build/ringtone-guard-debug.apk")
Write-Host "Built: $Build/ringtone-guard-debug.apk"
if ($Install) {
    Run "$Root/platform-tools/adb.exe" @('-d', 'install', '-r', "$Build/ringtone-guard-debug.apk")
    Run "$Root/platform-tools/adb.exe" @('-d', 'shell', 'am', 'start', '-n', 'com.family.ringtoneguard/.MainActivity')
}
