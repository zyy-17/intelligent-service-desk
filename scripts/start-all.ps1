# Open both services in two separate PowerShell windows (UTF-8 already set
# inside each child script). desk-core starts first so it can register in Nacos
# before the gateway resolves lb://desk-core.
$ErrorActionPreference = 'Stop'

$coreScript = Join-Path $PSScriptRoot 'start-core.ps1'
$gwScript = Join-Path $PSScriptRoot 'start-gateway.ps1'

Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', "`"$coreScript`""
Start-Sleep -Seconds 3
Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', "`"$gwScript`""

Write-Host "Two windows opened: desk-core (8081) and isd-gateway (8080)." -ForegroundColor Green
Write-Host "Open http://localhost:8080 after desk-core logs 'register finished'." -ForegroundColor Green
