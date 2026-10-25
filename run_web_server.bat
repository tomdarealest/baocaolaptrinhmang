@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
echo Starting Java web file server on http://127.0.0.1:8080
if not exist "build\classes" mkdir "build\classes"
javac -encoding UTF-8 -d build/classes src/Btapbaocao/*.java
if errorlevel 1 goto failed
java -cp build/classes Btapbaocao.WebFileServer
goto end
:failed
echo Compilation failed. Install a JDK and ensure javac is on PATH.
pause
:end
