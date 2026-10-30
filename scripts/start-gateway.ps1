# Start isd-gateway (Spring Cloud Gateway, port 8080) with a UTF-8 console.
# Start desk-core first, otherwise requests will return 503 until the
# desk-core instance shows up in Nacos.
$ErrorActionPreference = 'Stop'

# 1) Switch this PowerShell window to UTF-8 before starting the JVM.
try {
    chcp 65001 > $null
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
    Write-Warning "Could not switch console to UTF-8: $_"
}

# 2) Always run from the project root. application-local.yml (Nacos account)
#    is resolved relative to the process working directory.
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$jar = Join-Path $projectRoot 'isd-gateway\target\isd-gateway-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) {
    Write-Host "Jar not found: $jar" -ForegroundColor Red
    Write-Host "Build it first: mvn -DskipTests package" -ForegroundColor Yellow
    exit 1
}

Write-Host "Starting isd-gateway on port 8080 (workdir: $projectRoot)" -ForegroundColor Cyan
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar $jar --server.port=8080
