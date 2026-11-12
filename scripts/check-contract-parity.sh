#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
java_module="$root/scraper-java"
python_module="$root/scraper-python"
python_bin="$python_module/.venv/bin/python"
fixture="$root/contracts/fixtures"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

for source in hacker-news wordpress-news; do
  if [ "$source" = "hacker-news" ]; then
    url='https://news.ycombinator.com/item?id=123456'
    page="$fixture/hacker-news-item.html"
  else
    url='https://wordpress.org/news/2026/11/exemplo/'
    page="$fixture/wordpress-news-item.html"
  fi
  (cd "$java_module" && mvn -q exec:java \
    -Dexec.mainClass=com.henrique.webscrapper.Main \
    -Dexec.args="parse $source $url ../contracts/fixtures/$(basename "$page")") > "$tmp/java-$source.jsonl"
  (cd "$python_module" && .venv/bin/python -m scraper_python.main \
    --source "$source" --url "$url" --input "$page") > "$tmp/python-$source.jsonl"
  "$python_bin" - "$tmp/java-$source.jsonl" "$tmp/java-normalized.json" <<'PY'
import json, sys
value = json.loads(open(sys.argv[1], encoding="utf-8").read())
value.pop("collectedAt", None)
open(sys.argv[2], "w", encoding="utf-8").write(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n")
PY
  "$python_bin" - "$tmp/python-$source.jsonl" "$tmp/python-normalized.json" <<'PY'
import json, sys
value = json.loads(open(sys.argv[1], encoding="utf-8").read())
value.pop("collectedAt", None)
open(sys.argv[2], "w", encoding="utf-8").write(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n")
PY
  diff -u "$tmp/java-normalized.json" "$tmp/python-normalized.json"
done

echo "Java/Python fixtures match the shared JSON contract."
