# Start the backend with the JDK in JAVA_HOME.
# Usage (project root): .\scripts\run-backend.cmd
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$javaHome = $env:JAVA_HOME

if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\java.exe'))) {
    throw 'JAVA_HOME is not set to a valid JDK. Install JDK 17 and set JAVA_HOME.'
}

$javaExe = Join-Path $javaHome 'bin\java.exe'

$major = 0
try {
    $versionOutput = cmd.exe /c "`"$javaExe`" -version 2>&1" | Out-String
    if ($versionOutput -match 'version "(\d+)') {
        $major = [int]$Matches[1]
    }
} catch {
    $major = 0
}

if ($major -gt 0 -and $major -lt 17) {
    throw "JAVA_HOME points to Java $major, but this project requires Java 17+."
}

if ($major -eq 0) {
    Write-Warning 'Could not detect the Java version. Make sure JAVA_HOME points to JDK 17+.'
} else {
    Write-Output "Using Java $major from $javaExe"
}

$localConfig = Join-Path $repoRoot 'agent\src\main\resources\application-local.yml'
if (-not (Test-Path $localConfig)) {
    Write-Warning "Local config not found: $localConfig"
    Write-Warning 'Copy application-local.yml.example and fill in the database password.'
}

& (Join-Path $repoRoot 'agent\mvnw.cmd') -f (Join-Path $repoRoot 'agent\pom.xml') spring-boot:run
exit $LASTEXITCODE
