#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/classes
javac --release 21 -encoding UTF-8 -d build/classes src/main/java/dev/ksp/osrs/*.java
java -cp build/classes dev.ksp.osrs.ServerMain config/worlds.csv
