param(
    [string]$AndroidStudioHome = 'C:\Program Files\Android\Android Studio'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$supportRoot = Join-Path $projectRoot '.gradle/group-join-requests-contract'
$layoutlib = Join-Path $AndroidStudioHome 'plugins/design-tools/lib/layoutlib.jar'
if (-not (Test-Path -LiteralPath $layoutlib)) { throw "Android Studio layoutlib not found: $layoutlib" }
New-Item -ItemType Directory -Path $supportRoot -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
# Reuse only Android Studio's bundled org.json implementation. No app dependency is added.
$jsonJar = Join-Path $supportRoot 'json-runtime.jar'
$source = [IO.Compression.ZipFile]::OpenRead($layoutlib)
$stream = [IO.File]::Open($jsonJar, [IO.FileMode]::Create)
$destination = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $source.Entries) {
        if ($entry.FullName.StartsWith('org/json/') -and $entry.FullName.EndsWith('.class')) {
            $target = $destination.CreateEntry($entry.FullName)
            $inputStream = $entry.Open(); $outputStream = $target.Open()
            try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
        }
    }
} finally { $destination.Dispose(); $stream.Dispose(); $source.Dispose() }
$jarForGradle = $jsonJar.Replace('\', '/').Replace("'", "\'")
$initScript = Join-Path $supportRoot 'contract.init.gradle'
$script = @"
allprojects {
    afterEvaluate { project ->
        if (project.path == ':app') {
            project.android.sourceSets.getByName('test').kotlin.srcDir('src/contractTest/java')
            project.tasks.withType(Test).configureEach {
                doFirst { classpath = project.files('$jarForGradle') + classpath }
            }
        }
    }
}
"@
[IO.File]::WriteAllText($initScript, $script, [Text.UTF8Encoding]::new($false))
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:testDebugUnitTest --tests com.example.ridemate_api_test.GroupJoinRequestsHttpTest --offline --init-script $initScript
    if ($LASTEXITCODE -ne 0) { throw "HTTP contract tests failed: $LASTEXITCODE" }
} finally { Pop-Location }
