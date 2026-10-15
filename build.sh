#!/bin/bash
set -e
cd "$(dirname "$0")"
[ -d /opt/jdk17 ] && export PATH=/opt/jdk17/bin:$PATH
rm -rf out && mkdir -p out/classes
javac --release 17 -d out/classes $(find src -name '*.java')
printf 'Main-Class: dev.nebula.updater.Updater\n' > out/manifest.txt
jar cfm out/nebula-updater.jar out/manifest.txt -C out/classes .
cp updater.json.example out/updater.json
cp "Nebula Updater.bat" out/
cp README.md out/
echo built out/nebula-updater.jar
