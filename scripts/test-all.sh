#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$root/scripts/test-java.sh"
"$root/scripts/test-python.sh"
"$root/scripts/check-contract-parity.sh"
