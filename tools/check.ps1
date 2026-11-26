# Standalone compile check for the Augustus UI work.
#
# Compiles individual Java sources against the already-built OpenAugustus classes plus the resolved
# dependency classpath, instead of invoking Gradle. This lets several contributors compile-check at
# the same time without contending for the Gradle build directory or the daemon lock, and it is fast.
#
# Two Windows limits are handled here:
#   * the workspace path contains spaces, so nothing may be passed unquoted on a command line;
#   * the resolved classpath is well past the ~8k cmd.exe limit, so every argument travels inside a
#     javac @argfile, where javac also treats '\' as an escape character -- hence forward slashes.
#
# `--release 8` pins the platform API to Java 8, which turns any Java 9+ API use
# (List.of, var, String.isBlank, records, ...) into a hard compile error.
#
# Usage (run from the OpenAugustus directory):
#   & .\tools\check.ps1                                                    # all of net/augustus/ui
#   & .\tools\check.ps1 -Path src\main\java\net\augustus\clickgui
#   & .\tools\check.ps1 -Files path\to\A.java,path\to\B.java

param(
    [string[]]$Files = @(),
    [string]$Path,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
if (-not $Path) { $Path = 'src\main\java\net\augustus\ui' }
$pathFull = Join-Path $repo $Path
$classes  = Join-Path $repo 'build\classes\java\main'
$cpFile   = Join-Path $repo 'build\compile-classpath.txt'
$buildDir = Join-Path $repo 'build'
$outDir   = Join-Path $buildDir 'probe'
$argFile  = Join-Path $buildDir 'javac-check.arg'

if (-not (Test-Path $cpFile)) {
    Write-Host "Missing $cpFile -- run: .\gradlew.bat dumpCompileClasspath" -ForegroundColor Red
    exit 2
}
if (-not (Test-Path $classes)) {
    Write-Host "Missing $classes -- run: .\gradlew.bat compileJava" -ForegroundColor Red
    exit 2
}

# Locate a javac. A modern JDK is preferred because only it supports --release 8.
$javac = $null
$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\javac.exe') }
$candidates += @(
    'C:\Program Files\Java\jdk-21.0.11\bin\javac.exe',
    'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot\bin\javac.exe',
    'C:\Program Files\BellSoft\LibericaJDK-8-Full\bin\javac.exe'
)
foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { $javac = $c; break } }
if (-not $javac) { Write-Host 'No javac found' -ForegroundColor Red; exit 2 }

$supportsRelease = ((& $javac -version 2>&1) -notmatch '^javac 1\.8')

# Resolve the sources to check.
if ($Files.Count -eq 0) {
    $resolved = @(Get-ChildItem -Path $pathFull -Recurse -Filter *.java -File -ErrorAction SilentlyContinue |
                  ForEach-Object { $_.FullName })
} else {
    $resolved = @($Files | ForEach-Object {
        $p = if (Test-Path $_) { $_ } else { Join-Path $repo $_ }
        (Resolve-Path $p).Path
    })
}

if ($resolved.Count -eq 0) {
    Write-Host "No .java files to check under $pathFull" -ForegroundColor Yellow
    exit 0
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$depCp = (Get-Content $cpFile -Raw).Trim()
$cp = "$classes;$depCp"

$fwd = { param($p) $p.Replace('\', '/') }
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('-encoding')
[void]$sb.AppendLine('UTF-8')
if ($supportsRelease) { [void]$sb.AppendLine('--release'); [void]$sb.AppendLine('8') }
else { [void]$sb.AppendLine('-source'); [void]$sb.AppendLine('1.8'); [void]$sb.AppendLine('-target'); [void]$sb.AppendLine('1.8') }
[void]$sb.AppendLine('-nowarn')
[void]$sb.AppendLine('-cp')
[void]$sb.AppendLine('"' + (& $fwd $cp) + '"')
[void]$sb.AppendLine('-d')
[void]$sb.AppendLine('"' + (& $fwd $outDir) + '"')
foreach ($f in $resolved) { [void]$sb.AppendLine('"' + (& $fwd $f) + '"') }
# UTF8Encoding(false): javac's argument-file parser rejects a leading byte-order mark.
[System.IO.File]::WriteAllText($argFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))

if (-not $Quiet) {
    $mode = if ($supportsRelease) { '--release 8' } else { '-target 1.8' }
    Write-Host "javac $mode  ($($resolved.Count) file(s))" -ForegroundColor Cyan
}

$logFile = Join-Path $buildDir 'probe-log.txt'
$cmdLine = '"' + $javac + '" @"' + $argFile + '" > "' + $logFile + '" 2>&1'
cmd /c $cmdLine
$code = $LASTEXITCODE
$log = if (Test-Path $logFile) { Get-Content $logFile -Raw } else { '' }

if ($code -eq 0) {
    if (-not $Quiet) { Write-Host "OK  $($resolved.Count) file(s) compile on Java 8" -ForegroundColor Green }
    exit 0
}

Write-Host $log
Write-Host "FAILED ($($resolved.Count) file(s) checked)" -ForegroundColor Red
exit $code
