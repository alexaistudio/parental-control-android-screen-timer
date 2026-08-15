$ErrorActionPreference = 'Stop'

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    $localSdk = Join-Path $PSScriptRoot '..\.android-sdk'
    if (Test-Path -LiteralPath (Join-Path $localSdk 'platforms\android-35\android.jar')) {
        $env:ANDROID_HOME = (Resolve-Path -LiteralPath $localSdk).Path
    } else {
        throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK containing Platform 35 and Build Tools 35.0.0.'
    }
}

& "$PSScriptRoot\..\gradlew.bat" --no-daemon testDebugUnitTest lintDebug assembleDebug
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
