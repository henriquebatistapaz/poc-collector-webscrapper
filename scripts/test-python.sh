#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$root/scraper-python/.venv/bin/python" -m pytest -q "$root/scraper-python/tests"
