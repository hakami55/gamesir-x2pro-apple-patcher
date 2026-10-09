param([string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA 'GameSirX2PatcherSigning'))
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $SigningDirectory 'release.jks'
$credentialFile = Join-Path $SigningDirectory 'signing-password.xml'
New-Item -ItemType Directory -Force -Path $SigningDirectory | Out-Null
if (-not (Test-Path -LiteralPath $credentialFile)) {
    if (Test-Path -LiteralPath $keystore) { throw 'Keystore exists but password record is missing. Restore your signing credentials.' }
    $randomBytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($randomBytes)
    $rng.Dispose()
    $secure = ConvertTo-SecureString ([Convert]::ToBase64String($randomBytes)) -AsPlainText -Force
    $secure | Export-Clixml -LiteralPath $credentialFile
}
$secure = Import-Clixml -LiteralPath $credentialFile
$password = (New-Object System.Net.NetworkCredential('', $secure)).Password
$env:PATCHER_KEYSTORE = $keystore
$env:PATCHER_STORE_PASSWORD = $password
$env:PATCHER_KEY_PASSWORD = $password
try {
    if (-not (Test-Path -LiteralPath $keystore)) {
        & keytool -genkeypair -keystore $keystore -storepass:env PATCHER_STORE_PASSWORD -keypass:env PATCHER_KEY_PASSWORD -alias patcher -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=GameSir X2 Pro Community Patcher'
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed' }
    }
    Push-Location $project
    try {
        & .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Build or validation failed' }
        $destination = Join-Path $project 'dist'
        New-Item -ItemType Directory -Force -Path $destination | Out-Null
        $apk = Join-Path $destination 'GameSir-X2-Pro-Patcher-0.1.0-alpha1.apk'
        Copy-Item -LiteralPath (Join-Path $project 'app\build\outputs\apk\release\app-release.apk') -Destination $apk
        $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $(Split-Path -Leaf $apk)" | Set-Content -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Encoding ascii
        Write-Output "Signed APK: $apk"
    } finally { Pop-Location }
} finally {
    Remove-Item Env:PATCHER_KEYSTORE,Env:PATCHER_STORE_PASSWORD,Env:PATCHER_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
}
