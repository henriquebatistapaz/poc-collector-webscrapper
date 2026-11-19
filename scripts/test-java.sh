#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root/scraper-java"
exec "$root/mvnw" -f "$root/scraper-java/pom.xml" -B -ntp verify
