import hashlib
import re
import unicodedata
from urllib.parse import unquote_plus, urlsplit, urlunsplit

from .safety import validate_output_url


def _normalize(value: str | None) -> str:
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", value or "").strip())


def canonical_url(value: str) -> str:
    validate_output_url(value)
    parts = urlsplit(value)
    excluded = {"fbclid", "gclid", "mc_cid", "mc_eid"}
    query = []
    parameters = parts.query.split("&") if parts.query else []
    while parameters and not parameters[-1]:
        parameters.pop()
    for parameter in parameters:
        key = unquote_plus(parameter.split("=", 1)[0]).lower()
        if not key.startswith("utm_") and key not in excluded:
            query.append(parameter)
    host = (parts.hostname or "").lower()
    netloc = host + (f":{parts.port}" if parts.port else "")
    return urlunsplit((parts.scheme.lower(), netloc, parts.path, "&".join(query), ""))


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
