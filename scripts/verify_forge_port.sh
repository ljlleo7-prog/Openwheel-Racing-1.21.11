#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if rg -n 'net\.neoforged|neoforge\.mods\.toml|"neoforge:' \
    build.gradle settings.gradle gradle.properties src/main/java src/main/resources; then
    echo 'Forge verification failed: stale NeoForge references found.' >&2
    exit 1
fi

test -f src/main/resources/META-INF/mods.toml
test ! -e src/main/resources/META-INF/neoforge.mods.toml
test -d src/main/resources/data/openwheelracing/forge

./gradlew build
