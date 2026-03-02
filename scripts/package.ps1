$ErrorActionPreference = "Stop"

# 1. Clean and Package
Write-Host "Building project..."
mvn clean package
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed."
}

# 2. Prepare dependencies
$libsDir = "target/libs"
if (-not (Test-Path $libsDir)) {
    Write-Error "libs directory not found in target."
}

# Remove non-platform-specific JavaFX jars to avoid conflicts
# JavaFX jars usually come in pairs: javafx-controls-21.0.1.jar (empty/api) and javafx-controls-21.0.1-win.jar (impl)
# We want to keep the -win ones and remove the others if they exist.
# Actually, jpackage handles modules well, but sometimes duplicate modules on path cause issues.
# A safer bet is to remove the api jars if the win jars exist.
$javafxJars = Get-ChildItem $libsDir -Filter "javafx-*.jar"
foreach ($jar in $javafxJars) {
    if ($jar.Name -notmatch "-win" -and $jar.Name -notmatch "-linux" -and $jar.Name -notmatch "-mac") {
        # Check if a -win version exists
        $winName = $jar.Name.Replace(".jar", "-win.jar")
        if (Test-Path (Join-Path $libsDir $winName)) {
            Write-Host "Removing stub jar: $($jar.Name)"
            Remove-Item $jar.FullName
        }
    }
}

# Copy the main jar to libs as well, so we can just point to libs
$version = "1.6.0"
Copy-Item "target/gold-price-tracker-$version.jar" $libsDir

# 3. Run jpackage
Write-Host "Running jpackage..."
$destDir = "release/1.6"
if (Test-Path $destDir) {
    Remove-Item $destDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $destDir | Out-Null

# We use --input target/libs which now contains all jars including main jar
# We use --main-jar gold-price-tracker-$version.jar
# We use --java-options to fix the modularity/reflection issues
jpackage `
  --type app-image `
  --input $libsDir `
  --main-jar gold-price-tracker-$version.jar `
  --main-class com.goldpricetracker.Launcher `
  --name "gold-price-tracker" `
  --dest $destDir `
  --java-options "--add-opens javafx.graphics/javafx.scene.input=ALL-UNNAMED --add-opens javafx.graphics/javafx.scene=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.scene.input=ALL-UNNAMED --Dfile.encoding=UTF-8"

if ($LASTEXITCODE -ne 0) {
    Write-Error "jpackage failed."
}

Write-Host "Packaging complete. Output in $destDir/gold-price-tracker"
