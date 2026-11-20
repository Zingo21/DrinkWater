# Builds the server and the web app here and (re)starts them on the Docker host, see "Hosting the server" in README.md.
# Usage: .\deploy.ps1 user@192.168.1.50
param(
    [Parameter(Mandatory)]
    [string] $Target
)
$ErrorActionPreference = 'Stop'

function Invoke-Step([scriptblock] $Step) {
    & $Step
    if ($LASTEXITCODE) { throw "Failed with exit code ${LASTEXITCODE}: $Step" }
}

# Gradle runs here because configuring the project needs the Android SDK; the host only builds the image.
Invoke-Step { & "$PSScriptRoot\gradlew.bat" :server:installDist :composeApp:wasmJsBrowserDistribution }

$archive = Join-Path ([IO.Path]::GetTempPath()) 'drinkwater-deploy.tar'
Invoke-Step {
    tar -cf $archive -C $PSScriptRoot Dockerfile .dockerignore compose.yaml `
        server/build/install composeApp/build/dist/wasmJs/productionExecutable
}
Invoke-Step { scp $archive "${Target}:drinkwater-deploy.tar" }
Remove-Item $archive

# The old build is removed first because the web app's file names change with every build. .env and the
# database volume are left alone. --wait fails the deploy if the new server doesn't pass its health check.
Invoke-Step {
    ssh $Target 'mkdir -p ~/drinkwater && cd ~/drinkwater && rm -rf server composeApp && tar -xf ~/drinkwater-deploy.tar && rm ~/drinkwater-deploy.tar && docker compose up -d --build --wait --wait-timeout 120'
}
