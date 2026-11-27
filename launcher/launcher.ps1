<#
  Augustus Redesing - lanzador.

  Modos, en este orden de preferencia:
    1. jar         : hay un openaugustus-*-all.jar cerca  -> java -jar con los nativos en el library path
    2. codigo      : hay un gradlew.bat cerca            -> gradlew runClient (extrae nativos y assets solo)
    3. ayuda       : nada de lo anterior                 -> explica como conseguir el cliente

  Java: se exige Java 8, porque Minecraft 1.8.9 y LWJGL 2 no funcionan en JVMs modernas. Se busca en
  JAVA_HOME, en las rutas tipicas de instalacion y, por ultimo, en el PATH; el primero que responda
  "1.8" gana. Nunca se arranca con otra version: es mejor un error claro que un crash confuso.
#>

param(
   [switch]$DryRun   # solo informa de lo que ha encontrado y sale, sin arrancar nada
)

# 'Continue' y no 'Stop' a proposito: java.exe escribe su version (y el cliente su log) por stderr, y en
# PowerShell 5.1 un programa nativo que escribe en stderr genera ErrorRecords; con 'Stop' eso aborta el
# lanzador incluso cuando el programa ha funcionado. Aqui cada paso comprueba su resultado explicitamente.
$ErrorActionPreference = 'Continue'
$script:BaseDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Step($text) { Write-Host "  $text" -ForegroundColor Gray }
function Write-Ok($text) { Write-Host "  $text" -ForegroundColor Green }
function Write-Warn2($text) { Write-Host "  $text" -ForegroundColor Yellow }

function Test-Java8($javaExe) {
   if (-not (Test-Path $javaExe)) { return $false }
   $output = ''
   try {
      # Silenciar el ErrorRecord de stderr sin cambiar la preferencia global: sin esto, la salida de
      # `java -version` (que va a stderr) no se puede leer como texto de forma fiable.
      $output = (& $javaExe -version 2>&1 | Out-String)
   } catch {
      return $false
   }
   return ($output -match '1\.8\.')
}

function Find-Java8 {
   $candidates = New-Object System.Collections.Generic.List[string]

   if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }

   $roots = @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\BellSoft',
              'C:\Program Files\Zulu', 'C:\Program Files\Microsoft', 'C:\Program Files (x86)\Java')
   foreach ($root in $roots) {
      if (Test-Path $root) {
         foreach ($dir in Get-ChildItem $root -Directory -ErrorAction SilentlyContinue) {
            $candidates.Add((Join-Path $dir.FullName 'bin\java.exe'))
            $candidates.Add((Join-Path $dir.FullName 'jre\bin\java.exe'))
         }
      }
   }

   $onPath = Get-Command java -ErrorAction SilentlyContinue
   if ($onPath) { $candidates.Add($onPath.Source) }

   foreach ($candidate in $candidates) {
      if (Test-Java8 $candidate) { return $candidate }
   }
   return $null
}

function Find-Jar {
   <# El fat jar se llama augustus-redesing-<version>-all.jar (antes openaugustus-<version>-all.jar; se
      sigue aceptando el nombre viejo para quien tenga una descarga anterior). Se busca donde un usuario lo
      dejaria al descomprimir el release, y tambien en el arbol del proyecto. #>
   $dirs = @($script:BaseDir, (Join-Path $script:BaseDir 'libs'), (Join-Path $script:BaseDir 'build\libs'),
             (Join-Path $script:BaseDir '..'), (Join-Path $script:BaseDir '..\build\libs'),
             (Join-Path $script:BaseDir '..\..\build\libs'))
   foreach ($dir in $dirs) {
      if (-not (Test-Path $dir)) { continue }
      $all = Get-ChildItem $dir -Filter 'augustus-redesing-*-all.jar' -File -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTime -Descending | Select-Object -First 1
      if ($all) { return $all.FullName }
      $legacy = Get-ChildItem $dir -Filter 'openaugustus-*-all.jar' -File -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTime -Descending | Select-Object -First 1
      if ($legacy) { return $legacy.FullName }
   }
   foreach ($dir in $dirs) {
      if (-not (Test-Path $dir)) { continue }
      $any = Get-ChildItem -Path $dir -File -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -like 'augustus-redesing*.jar' -or $_.Name -like 'openaugustus*.jar' } |
             Sort-Object LastWriteTime -Descending | Select-Object -First 1
      if ($any) { return $any.FullName }
   }
   return $null
}

function Find-GradleWrapper {
   $dirs = @($script:BaseDir, (Join-Path $script:BaseDir '..'), (Join-Path $script:BaseDir '..\..'))
   foreach ($dir in $dirs) {
      $wrapper = Join-Path $dir 'gradlew.bat'
      $buildFile = Join-Path $dir 'build.gradle'
      if ((Test-Path $wrapper) -and (Test-Path $buildFile)) { return (Resolve-Path $wrapper).Path }
   }
   return $null
}

function Find-Natives($jarPath) {
   <# Los nativos de LWJGL/OpenAL/jinput son obligatorios para el modo jar. Se aceptan tanto la carpeta
      del release como la que genera Gradle. #>
   $candidates = @()
   if ($jarPath) { $candidates += (Join-Path (Split-Path -Parent $jarPath) 'natives') }
   $candidates += (Join-Path $script:BaseDir 'natives')
   $candidates += (Join-Path $script:BaseDir '..\build\natives')
   $candidates += (Join-Path $script:BaseDir '..\..\build\natives')
   foreach ($dir in $candidates) {
      if (Test-Path $dir) {
         $dll = Get-ChildItem $dir -Filter 'lwjgl*.dll' -File -ErrorAction SilentlyContinue | Select-Object -First 1
         if ($dll) { return (Resolve-Path $dir).Path }
      }
   }
   return $null
}

Write-Host ''
Write-Host '  ==============================================' -ForegroundColor Magenta
Write-Host '   Augustus Redesing  -  Minecraft 1.8.9' -ForegroundColor Magenta
Write-Host '  ==============================================' -ForegroundColor Magenta
Write-Host ''

$jar = Find-Jar
$wrapper = Find-GradleWrapper
$java = Find-Java8

Write-Step ("Modo: " + $(if ($jar) { 'jar del cliente' } elseif ($wrapper) { 'codigo fuente (Gradle)' } else { 'no determinado' }))
if ($jar) { Write-Step ("Cliente: " + $jar) }

if (-not $java) {
   Write-Warn2 'No se ha encontrado un Java 8.'
   Write-Host ''
   Write-Host '  Minecraft 1.8.9 necesita Java 8 (1.8.0_xxx). Instala, por ejemplo, Eclipse Temurin 8 o'
   Write-Host '  BellSoft Liberica 8 y vuelve a ejecutar este lanzador.'
   Write-Host ''
   Write-Host '  Descarga: https://adoptium.net/temurin/releases/?version=8'
   Write-Host ''
   exit 2
}

Write-Ok ("Java 8: " + $java)

if ($DryRun) {
   Write-Host ''
   if ($jar) {
      $nativesDir = Find-Natives $jar
      if ($nativesDir) { Write-Ok ("Nativos: " + $nativesDir) } else { Write-Warn2 'Nativos: no encontrados' }
   }
   if ($wrapper) { Write-Ok ("gradlew: " + $wrapper) }
   Write-Step 'DryRun: no se arranca nada.'
   exit 0
}

if ($jar) {
   $natives = Find-Natives $jar
   $arguments = @()
   if ($natives) {
      $arguments += "-Djava.library.path=$natives"
      $arguments += "-Dorg.lwjgl.librarypath=$natives"
      $arguments += "-Dnet.java.games.input.librarypath=$natives"
   } else {
      Write-Warn2 'No se han encontrado los nativos (lwjgl.dll...). El cliente puede fallar al arrancar.'
   }

   $arguments += @('-Xms512m', '-Xmx2g', '-jar', $jar)

   Write-Step 'Arrancando el cliente...'
   Write-Host ''
   Push-Location (Split-Path -Parent $jar)
   try {
      & $java @arguments
      exit $LASTEXITCODE
   } finally {
      Pop-Location
   }
}

if ($wrapper) {
   Write-Step 'Arrancando con Gradle (extrae nativos y descarga los assets de 1.8 la primera vez)...'
   Write-Host ''
   Push-Location (Split-Path -Parent $wrapper)
   try {
      & $wrapper 'runClient'
      exit $LASTEXITCODE
   } finally {
      Pop-Location
   }
}

Write-Warn2 'No se ha encontrado ni el cliente compilado ni el proyecto (gradlew.bat).'
Write-Host ''
Write-Host '  Opciones:'
Write-Host '   1. Descarga el ZIP del release completo y descomprimelo entero en una carpeta,'
Write-Host '      y ejecuta este lanzador desde ahi.'
Write-Host '   2. O clona el repositorio y ejecuta:  cd OpenAugustus && .\gradlew.bat runClient'
Write-Host ''
exit 3
