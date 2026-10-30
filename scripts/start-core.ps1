# Start desk-core (business service, port 8081) with a UTF-8 console.
# Without the UTF-8 switch below, Java writes UTF-8 bytes but the Windows
# console decodes them as GBK (code page 936), so Chinese logs look garbled.
$ErrorActionPreference = 'Stop'

# 1) Switch this PowerShell window to UTF-8 before starting the JVM.
try {
    chcp 65001 > $null
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
    Write-Warning "Could not switch console to UTF-8: $_"
}

# 2) Always run from the project root. application-local.yml and the uploads
#    directory are resolved relative to the process working directory.
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$jar = Join-Path $projectRoot 'desk-core\target\desk-core-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) {
    Write-Host "Jar not found: $jar" -ForegroundColor Red
    Write-Host "Build it first: mvn -DskipTests package" -ForegroundColor Yellow
    exit 1
}

Write-Host "Starting desk-core on port 8081 (workdir: $projectRoot)" -ForegroundColor Cyan
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar $jar --server.port=8081
