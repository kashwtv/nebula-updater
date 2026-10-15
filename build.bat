@echo off
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out\classes
dir /s /b src\*.java > out\sources.txt
javac --release 17 -d out\classes @out\sources.txt || exit /b 1
echo Main-Class: dev.nebula.updater.Updater> out\manifest.txt
jar cfm out\nebula-updater.jar out\manifest.txt -C out\classes .
copy updater.json.example out\updater.json >nul
copy "Nebula Updater.bat" out\ >nul
copy README.md out\ >nul
echo built out\nebula-updater.jar
