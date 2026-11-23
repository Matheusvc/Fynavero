# Gera um único arquivo executável: backend/target/fynavero.jar (front-end embutido).
$ErrorActionPreference = "Stop"
Push-Location "$PSScriptRoot/frontend"
npm install
npm run build
Pop-Location
Push-Location "$PSScriptRoot/backend"
mvn -q package
Pop-Location
Write-Host "Pronto: java -jar backend/target/fynavero.jar  ->  http://localhost:8080"
