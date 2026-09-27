param([switch]$Demo)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path '.env') {
 Get-Content '.env' | ForEach-Object {
  if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
 }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Install a Java 17 JDK and add java to PATH.' }
if (Test-Path 'target/campusflow-1.0.0.jar') {
 if ($Demo) { & java -jar target/campusflow-1.0.0.jar --spring.profiles.active=demo }
 else { & java -jar target/campusflow-1.0.0.jar }
} else {
 if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Install Maven 3.9+ and add mvn to PATH.' }
 & mvn package
 if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
 if ($Demo) { & java -jar target/campusflow-1.0.0.jar --spring.profiles.active=demo }
 else { & java -jar target/campusflow-1.0.0.jar }
}
