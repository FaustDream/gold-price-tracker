param(
  [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

Write-Host "Starting dev run (JavaFX)..."
if ($SkipTests) {
  mvn -q clean compile
} else {
  mvn -q clean compile test
}

if ($LASTEXITCODE -ne 0) {
  Write-Error "Build failed."
}

Write-Host "Launching JavaFX app..."
mvn -q javafx:run
