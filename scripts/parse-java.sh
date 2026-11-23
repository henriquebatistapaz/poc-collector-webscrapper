#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root/scraper-java"
exec mvn -q exec:java -Dexec.mainClass=dev.henrique.webscrapper.Main -Dexec.args="$*"
