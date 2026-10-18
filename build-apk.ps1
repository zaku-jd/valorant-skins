param([string]$Sdk=$env:ANDROID_HOME,[string]$Jdk=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if($Sdk){$env:ANDROID_HOME=$Sdk}
if($Jdk){$env:JAVA_HOME=$Jdk}
& (Join-Path $PSScriptRoot 'gradlew.bat') -p $PSScriptRoot assembleDebug
if($LASTEXITCODE -ne 0){throw 'Android build failed.'}
$taskApk=Join-Path $PSScriptRoot 'app/build/outputs/apk/debug/app-debug.apk'
Write-Output "Built $taskApk"
