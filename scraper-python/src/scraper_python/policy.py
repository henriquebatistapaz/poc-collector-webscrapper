from dataclasses import dataclass
from enum import StrEnum
from math import ceil

from .safety import validate_source_page_url

MAX_RESPONSE_BYTES = 2_000_000


class FetchStatus(StrEnum):
    OK = "ok"
    DENIED = "denied"
    THROTTLED = "throttled"
    TIMEOUT = "timeout"
    UPSTREAM_ERROR = "upstream_error"
    INVALID_RESPONSE = "invalid_response"


@dataclass
class RobotsRules:
    disallowed: list[str]
    crawl_delay_seconds: int

    @classmethod
    def parse(cls, text: str) -> "RobotsRules":
        applies = False
        denied: list[str] = []
        delay = 0
        for raw in text.splitlines():
            key, separator, value = raw.partition(":")
            if not separator:
                continue
            key, value = key.strip().lower(), value.strip()
            if key == "user-agent":
                applies = value == "*"
            elif applies and key == "disallow" and value:
                denied.append(value)
            elif applies and key == "crawl-delay":
                try:
                    delay = ceil(float(value))
                except ValueError:
                    delay = 0
        return cls(denied, delay)

    def allows(self, path: str) -> bool:
        return not any(path.startswith(prefix) for prefix in self.disallowed)


def live_collection_enabled(source: str) -> bool:
    return False


def detail_pages(candidates: list[str], configured_limit: int = 5) -> list[str]:
    return candidates[: max(0, min(5, configured_limit))]


def validate_response_size(size: int) -> None:
    if size < 0 or size > MAX_RESPONSE_BYTES:
        raise ValueError("Response exceeds size limit")


def same_origin_redirect(source: str, original: str, destination: str) -> bool:
    validate_source_page_url(source, original)
    validate_source_page_url(source, destination)
    return True


def classify(status_code: int, timed_out: bool = False, markup_valid: bool = True) -> FetchStatus:
    if timed_out:
        return FetchStatus.TIMEOUT
    if status_code in (401, 403):
        return FetchStatus.DENIED
    if status_code == 429:
        return FetchStatus.THROTTLED
    if status_code >= 500:
        return FetchStatus.UPSTREAM_ERROR
    if not 200 <= status_code < 300 or not markup_valid:
        return FetchStatus.INVALID_RESPONSE
    return FetchStatus.OK
