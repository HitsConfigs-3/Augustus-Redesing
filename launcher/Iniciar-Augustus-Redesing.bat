@echo off
rem ---------------------------------------------------------------------------
rem  Augustus Redesing - lanzador de Windows
rem
rem  Doble clic aqui. Toda la logica esta en launcher.ps1 (esta carpeta), para
rem  poder buscar Java 8, los nativos y el cliente sin malabares de sintaxis.
rem ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

set "PS=powershell"
where pwsh >nul 2>nul && set "PS=pwsh"

%PS% -NoProfile -ExecutionPolicy Bypass -File "%~dp0launcher.ps1" %*
set "CODE=%ERRORLEVEL%"

if not "%CODE%"=="0" (
   echo.
   echo El lanzador termino con codigo %CODE%.
   echo.
   echo Pulsa una tecla para cerrar esta ventana...
   pause >nul
)
endlocal
