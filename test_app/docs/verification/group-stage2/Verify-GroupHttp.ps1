param(
    [string]$StudioRoot = 'C:/Program Files/Android/Android Studio'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$runtimeDir = Join-Path $projectRoot 'build/group-json'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
# Reuse Android's JSON runtime already bundled with Studio. No download or app dependency change.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$source = [IO.Compression.ZipFile]::OpenRead((Join-Path $StudioRoot 'plugins/design-tools/lib/layoutlib.jar'))
$jsonPath = Join-Path $runtimeDir 'json-runtime.jar'
if (Test-Path -LiteralPath $jsonPath) { Remove-Item -LiteralPath $jsonPath }
$target = [IO.Compression.ZipFile]::Open($jsonPath, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $source.Entries) {
        if ($entry.FullName.StartsWith('org/json/') -and $entry.FullName.EndsWith('.class')) {
            $output = $target.CreateEntry($entry.FullName).Open()
            $input = $entry.Open()
            try { $input.CopyTo($output) } finally { $input.Dispose(); $output.Dispose() }
        }
    }
} finally { $source.Dispose(); $target.Dispose() }
$initPath = Join-Path $runtimeDir 'json-tests.init.gradle'
@'
gradle.projectsEvaluated {
    def app = gradle.rootProject.project(':app')
    app.tasks.named('compileDebugUnitTestKotlin').configure {
        source(gradle.rootProject.file('docs/verification/group-stage2/GroupHttpContractTest.kt'))
    }
    app.tasks.withType(Test).configureEach {
        classpath = app.files(gradle.rootProject.file('build/group-json/json-runtime.jar')) + classpath
    }
}
'@ | Set-Content -LiteralPath $initPath -Encoding UTF8
$previousJavaHome = $env:JAVA_HOME
$env:JAVA_HOME = Join-Path $StudioRoot 'jbr'
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:testDebugUnitTest --offline --init-script $initPath
    if ($LASTEXITCODE -ne 0) { throw 'Group HTTP contract tests failed.' }
} finally { Pop-Location; $env:JAVA_HOME = $previousJavaHome }
