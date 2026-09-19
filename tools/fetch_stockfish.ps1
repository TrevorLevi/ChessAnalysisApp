<#
.SYNOPSIS
    Ajoute le moteur Stockfish natif a ChessForge (etape optionnelle).

.DESCRIPTION
    ChessForge fonctionne sans Stockfish : son moteur Kotlin integre suffit a reperer
    les gaffes et a fabriquer des puzzles. Stockfish rend l'analyse nettement plus
    rapide et plus fiable sur les positions calmes.

    Ce script :
      1. verifie que CMake du SDK Android est installe (sinon il l'installe) ;
      2. clone les sources de Stockfish dans third_party/stockfish ;
      3. lit le nom des reseaux NNUE attendus par cette version ;
      4. telecharge ces reseaux dans app/src/main/assets/nnue/ ;
      5. ecrit le manifeste que l'appli lit au premier demarrage.

    Ensuite, `gradlew assembleDebug` compile automatiquement la bibliotheque native :
    la presence des sources suffit a activer la compilation (voir app/build.gradle.kts).

    Attention a la licence : Stockfish est distribue sous GPL v3. Un APK qui l'embarque
    doit respecter cette licence. Pour un usage personnel sur votre telephone, cela ne
    pose aucun probleme ; pour une redistribution, il faut publier les sources.

.PARAMETER Tag
    Etiquette ou branche Git de Stockfish a utiliser. Par defaut : sf_19.

.PARAMETER SkipNets
    Ne telecharge pas les reseaux NNUE (l'appli restera sur le moteur integre tant
    qu'ils manquent).
#>
[CmdletBinding()]
param(
    [string]$Tag = "sf_19",
    [switch]$SkipNets
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$sfDir = Join-Path $root "third_party\stockfish"
$assetDir = Join-Path $root "app\src\main\assets\nnue"

function Write-Step($message) { Write-Host "==> $message" -ForegroundColor Cyan }
function Write-Warn($message) { Write-Host "    $message" -ForegroundColor Yellow }

# --- 1. Outils ---------------------------------------------------------------

Write-Step "Verification des outils"
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "git est introuvable. Installez Git puis relancez ce script."
}

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "C:\Android\Sdk" }
if (-not (Test-Path $sdk)) { throw "SDK Android introuvable. Definissez ANDROID_HOME." }

$cmakeDir = Join-Path $sdk "cmake"
$hasCmake = (Test-Path $cmakeDir) -and (Get-ChildItem $cmakeDir -Directory -ErrorAction SilentlyContinue)
if (-not $hasCmake) {
    Write-Warn "CMake du SDK absent, installation via sdkmanager..."
    $sdkmanager = Join-Path $sdk "cmdline-tools\bin\sdkmanager.bat"
    if (-not (Test-Path $sdkmanager)) {
        throw "sdkmanager introuvable ($sdkmanager). Installez le paquet 'cmake;3.22.1' depuis Android Studio."
    }
    & $sdkmanager --sdk_root=$sdk "cmake;3.22.1"
    if ($LASTEXITCODE -ne 0) { throw "Installation de CMake echouee." }
}

$ndkDir = Join-Path $sdk "ndk"
if (-not (Test-Path $ndkDir)) {
    throw "NDK introuvable dans $ndkDir. Installez-le depuis Android Studio (SDK Tools > NDK)."
}
$ndkVersion = (Get-ChildItem $ndkDir -Directory | Sort-Object Name -Descending | Select-Object -First 1).Name
Write-Host "    NDK detecte : $ndkVersion"
Write-Warn "Si ce numero differe de ndkVersion dans app/build.gradle.kts, alignez-le."

# --- 2. Sources Stockfish ----------------------------------------------------

Write-Step "Recuperation des sources Stockfish ($Tag)"
if (Test-Path (Join-Path $sfDir "src\uci.cpp")) {
    Write-Host "    Sources deja presentes, mise a jour ignoree."
} else {
    New-Item -ItemType Directory -Force (Split-Path -Parent $sfDir) | Out-Null
    if (Test-Path $sfDir) { Remove-Item -Recurse -Force $sfDir }
    git clone --depth 1 --branch $Tag https://github.com/official-stockfish/Stockfish.git $sfDir
    if ($LASTEXITCODE -ne 0) {
        throw "Clonage echoue. Verifiez le nom de l'etiquette ($Tag) ou votre connexion."
    }
}

# --- 3. Noms des reseaux NNUE ------------------------------------------------

Write-Step "Lecture des reseaux NNUE attendus"
$evaluateHeader = Join-Path $sfDir "src\evaluate.h"
if (-not (Test-Path $evaluateHeader)) { throw "Fichier $evaluateHeader introuvable." }

$content = Get-Content $evaluateHeader -Raw
$netNames = [ordered]@{}

# Stockfish 19 n'utilise plus qu'un seul reseau (EvalFileDefaultName) ; les versions
# 16 a 18 en avaient deux (Big et Small). On reconnait les deux formes, et les
# definitions par #define comme par constante.
$patterns = [ordered]@{
    "big"   = @("EvalFileDefaultName(?!Small)")
    "small" = @("EvalFileDefaultNameSmall")
}
foreach ($key in $patterns.Keys) {
    foreach ($symbol in $patterns[$key]) {
        $match = [regex]::Match($content, "$symbol\s*(?:\[\s*\])?\s*(?:=|\s)\s*""([^""]+\.nnue)""")
        if ($match.Success) {
            $netNames[$key] = $match.Groups[1].Value
            Write-Host "    reseau '$key' : $($match.Groups[1].Value)"
            break
        }
    }
}
if (-not $netNames.Contains("small")) {
    Write-Host "    pas de second reseau : cette version de Stockfish n'en utilise qu'un."
}
if ($netNames.Count -eq 0) { throw "Aucun nom de reseau trouve : version de Stockfish non reconnue." }

# --- 4. Telechargement des reseaux ------------------------------------------

if ($SkipNets) {
    Write-Warn "Telechargement des reseaux ignore (-SkipNets)."
} else {
    Write-Step "Telechargement des reseaux NNUE (plusieurs dizaines de Mo)"
    New-Item -ItemType Directory -Force $assetDir | Out-Null
    foreach ($key in $netNames.Keys) {
        $name = $netNames[$key]
        $target = Join-Path $assetDir $name
        if ((Test-Path $target) -and ((Get-Item $target).Length -gt 1024)) {
            Write-Host "    $name deja present."
            continue
        }
        $url = "https://tests.stockfishchess.org/api/nn/$name"
        Write-Host "    Telechargement de $name..."
        try {
            Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing -TimeoutSec 600
        } catch {
            Remove-Item $target -ErrorAction SilentlyContinue
            throw "Telechargement de $name echoue : $($_.Exception.Message)"
        }
        $sizeMb = [math]::Round((Get-Item $target).Length / 1MB, 1)
        Write-Host "    $name recupere ($sizeMb Mo)."
    }

    $manifest = Join-Path $assetDir "manifest.properties"
    $lines = @("# Genere par tools/fetch_stockfish.ps1 - ne pas modifier a la main")
    foreach ($key in $netNames.Keys) { $lines += "$key=$($netNames[$key])" }
    Set-Content -Path $manifest -Value $lines -Encoding utf8
    Write-Host "    Manifeste ecrit : $manifest"
}

# --- 5. Suite --------------------------------------------------------------

Write-Step "Termine"
Write-Host ""
Write-Host "Compilez maintenant l'application :" -ForegroundColor Green
Write-Host "    .\gradlew.bat :app:assembleDebug"
Write-Host ""
Write-Host "La premiere compilation native prend plusieurs minutes. Dans l'appli, l'ecran"
Write-Host "Reglages indiquera 'Stockfish natif detecte' une fois l'installation reussie."
Write-Host ""
Write-Warn "Rappel de licence : Stockfish est sous GPL v3 (voir third_party/stockfish/Copying.txt)."
