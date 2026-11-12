import hashlib
import re
import unicodedata
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from .safety import validate_output_url


def _normalize(value: str | None) -> str:
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", value or "").strip())


def canonical_url(value: str) -> str:
    validate_output_url(value)
    parts = urlsplit(value)
    excluded = {"fbclid", "gclid", "mc_cid", "mc_eid"}
    query = [(key, item) for key, item in parse_qsl(parts.query, keep_blank_values=True)
             if not key.lower().startswith("utm_") and key.lower() not in excluded]
    host = (parts.hostname or "").lower()
    netloc = host + (f":{parts.port}" if parts.port else "")
    return urlunsplit((parts.scheme.lower(), netloc, parts.path, urlencode(query), ""))


def fingerprint(source: str, title: str, published_at: str | None, author: str, url: str) -> str:
    values = [source, _normalize(title), published_at or "", _normalize(author), canonical_url(url)]
    digest = hashlib.sha256("\0".join(values).encode("utf-8")).hexdigest()
    return f"fingerprint:v1:{digest}"


class ItemRegistry:
    def __init__(self) -> None:
        self._keys: set[tuple[str, str]] = set()

    def add(self, source: str, external_id: str) -> bool:
        key = (source, external_id)
        if key in self._keys:
            return False
        self._keys.add(key)
        return True


def deduplicate_envelopes(envelopes):
    registry = ItemRegistry()
    unique = []
    for envelope in envelopes:
        if registry.add(envelope.item.source, envelope.item.external_id):
            unique.append(envelope)
    return unique
