from pathlib import Path
import json
from concurrent.futures import ThreadPoolExecutor
from threading import Lock
import time

import pytest
from jsonschema import Draft202012Validator

from scraper_python.models import CollectedEnvelope
from scraper_python.parser import parse_page
from scraper_python.safety import validate_output_url, validate_resolved_address
from scraper_python.policy import (
    FetchStatus, RobotsRules, classify, detail_pages, live_collection_enabled,
    validate_response_size,
)

FIXTURES = Path(__file__).parents[2] / "contracts" / "fixtures"


def test_hacker_news_parse_keeps_content_in_memory_and_emits_empty_entities():
    html = (FIXTURES / "hacker-news-item.html").read_text()
    envelope = parse_page("hacker-news", "https://news.ycombinator.com/item?id=123456", html)
    assert envelope.item.external_id == "123456"
    assert envelope.item.link == "https://news.example/story/1"
    assert len(envelope.page_content.comments) == 2
    assert "citation" in envelope.page_content.comments[0]
    assert envelope.entities == []
    assert "Visible comment one" not in envelope.model_dump_json()


def test_wordpress_news_parse():
    html = (FIXTURES / "wordpress-news-item.html").read_text()
    envelope = parse_page("wordpress-news", "https://wordpress.org/news/2026/11/exemplo/", html)
    assert envelope.item.external_id == "21967"
    assert envelope.item.link == "https://wordpress.org/news/2026/11/exemplo/"
    assert len(envelope.page_content.comments) == 1
    assert envelope.entities == []


def test_uses_versioned_fingerprint_when_wordpress_native_id_is_missing():
    html = (FIXTURES / "wordpress-news-item.html").read_text().replace("post-21967", "post")
    envelope = parse_page("wordpress-news", "https://wordpress.org/news/2026/11/exemplo/?utm_source=test#section", html)
    assert envelope.item.external_id == (
        "fingerprint:v1:0307fdbf8a9eef3e918e0c588c1702bd42fe4424e43e57b8faaa174c8b3e8374"
    )


@pytest.mark.parametrize("url", [
    "javascript:alert(1)", "file:///etc/passwd", "/news/relative",
    "https://user:pass@example.com/", "https://example.com/" + "x" * 2000,
])
def test_rejects_unsafe_output_urls(url):
    with pytest.raises(ValueError):
        validate_output_url(url)


@pytest.mark.parametrize("address", ["127.0.0.1", "10.0.0.1", "169.254.1.2", "224.0.0.1"])
def test_rejects_private_resolved_addresses(address):
    with pytest.raises(ValueError):
        validate_resolved_address(address)


def test_rejects_hostname_when_any_resolved_address_is_private(monkeypatch):
    import scraper_python.safety as safety

    monkeypatch.setattr(safety, "getaddrinfo", lambda *args, **kwargs: [
        (None, None, None, None, ("8.8.8.8", 0)),
        (None, None, None, None, ("10.0.0.1", 0)),
    ])
    with pytest.raises(ValueError):
        validate_resolved_address("mixed.example")


def test_fingerprint_and_source_scoped_deduplication():
    from scraper_python.identity import fingerprint, ItemRegistry, deduplicate_envelopes

    assert fingerprint("wordpress-news", "Olá Mundo", "2026-11-12T12:30:00Z", "Equipe News",
                       "https://wordpress.org/news/2026/11/exemplo/?utm_source=x#top") == (
        "fingerprint:v1:0307fdbf8a9eef3e918e0c588c1702bd42fe4424e43e57b8faaa174c8b3e8374"
    )
    registry = ItemRegistry()
    assert registry.add("a", "1")
    assert not registry.add("a", "1")
    assert registry.add("b", "1")

    wordpress = parse_page("wordpress-news", "https://wordpress.org/news/2026/11/exemplo/",
                           (FIXTURES / "wordpress-news-item.html").read_text())
    hacker_html = (FIXTURES / "hacker-news-item.html").read_text().replace("123456", "21967")
    hacker = parse_page("hacker-news", "https://news.ycombinator.com/item?id=21967", hacker_html)
    assert len(deduplicate_envelopes([wordpress, wordpress, hacker])) == 2


def test_cli_emits_duplicate_source_item_once(tmp_path, capsys, monkeypatch):
    from scraper_python.main import main
    import sys

    fixture = FIXTURES / "wordpress-news-item.html"
    monkeypatch.setattr(sys, "argv", [
        "webscrapper", "--source", "wordpress-news",
        "--url", "https://wordpress.org/news/2026/11/exemplo/", "--input", str(fixture),
        "--url", "https://wordpress.org/news/2026/11/exemplo/", "--input", str(fixture),
    ])
    assert main() == 0
    lines = capsys.readouterr().out.splitlines()
    assert len(lines) == 1
    assert CollectedEnvelope.model_validate_json(lines[0]).item.external_id == "21967"


def test_jsonl_envelope_has_exact_item_fields():
    html = (FIXTURES / "wordpress-news-item.html").read_text()
    envelope = parse_page("wordpress-news", "https://wordpress.org/news/2026/11/exemplo/", html)
    line = envelope.model_dump_json()
    assert isinstance(CollectedEnvelope.model_validate_json(line), CollectedEnvelope)
    assert set(envelope.model_dump(mode="json")["item"]) == {
        "source", "externalId", "title", "link", "publishedAt"
    }
    schema = json.loads((FIXTURES.parent / "envelope-v1.schema.json").read_text())
    Draft202012Validator(schema).validate(envelope.model_dump(mode="json", by_alias=True))


def test_sources_default_disabled_and_robots_limits_are_obeyed():
    assert not live_collection_enabled("hacker-news")
    rules = RobotsRules.parse("User-agent: *\nCrawl-delay: 30\nDisallow: /vote?\nDisallow: /login\n")
    assert rules.crawl_delay_seconds == 30
    assert not rules.allows("/vote?id=1")
    assert rules.allows("/item?id=1")
    assert detail_pages([str(i) for i in range(8)]) == ["0", "1", "2", "3", "4"]
    catalog = json.loads((FIXTURES.parent / "source-catalog.json").read_text())
    assert all(source["selectedForMvp"] and not source["liveEnabled"] for source in catalog["sources"])
    assert all(source["maxDetailPagesPerRun"] == 5 and source["maxConcurrentPerHost"] == 1
               for source in catalog["sources"])


def test_unsafe_redirect_and_oversized_response_are_rejected():
    from scraper_python.safety import validate_redirect

    with pytest.raises(ValueError):
        validate_redirect("hacker-news", "https://evil.example/item?id=1")
    with pytest.raises(ValueError):
        validate_response_size(2_000_001)


@pytest.mark.parametrize(("code", "expected"), [
    (401, FetchStatus.DENIED), (403, FetchStatus.DENIED), (429, FetchStatus.THROTTLED),
    (503, FetchStatus.UPSTREAM_ERROR), (200, FetchStatus.OK),
])
def test_http_failures_are_classified_without_retry(code, expected):
    assert classify(code) == expected
    assert classify(200, timed_out=True) == FetchStatus.TIMEOUT
    assert classify(200, markup_valid=False) == FetchStatus.INVALID_RESPONSE


def test_per_host_request_gate_allows_at_most_one_in_flight_request():
    from scraper_python.request_gate import HostRequestGate

    gate = HostRequestGate()
    active = 0
    maximum = 0
    lock = Lock()

    def run(_):
        nonlocal active, maximum
        permit = gate.acquire("news.ycombinator.com")
        try:
            with lock:
                active += 1
                maximum = max(maximum, active)
            time.sleep(0.01)
            with lock:
                active -= 1
        finally:
            permit.release()

    with ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(run, range(6)))
    assert maximum == 1
