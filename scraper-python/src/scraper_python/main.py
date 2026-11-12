import argparse
import sys
from pathlib import Path

from .identity import deduplicate_envelopes
from .parser import parse_page


def main() -> int:
    parser = argparse.ArgumentParser(description="Parse local HTML pages and emit deduplicated JSONL envelopes")
    parser.add_argument("--source", choices=["hacker-news", "wordpress-news"], required=True)
    parser.add_argument("--url", action="append", required=True, help="Source page URL; repeat with each --input")
    parser.add_argument("--input", action="append", type=Path, required=True, help="Local HTML file; repeat with each --url. No network fetch is performed")
    args = parser.parse_args()
    if len(args.url) != len(args.input):
        parser.error("cada --url precisa corresponder a um --input")
    try:
        envelopes = [parse_page(args.source, url, path.read_text())
                     for url, path in zip(args.url, args.input)]
    except Exception as exc:
        print(f"parse failed: {type(exc).__name__}", file=sys.stderr)
        return 2
    for envelope in deduplicate_envelopes(envelopes):
        print(envelope.model_dump_json(by_alias=True, exclude={"_page_content"}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
