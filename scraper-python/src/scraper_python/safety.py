from ipaddress import ip_address
from socket import SOCK_STREAM, getaddrinfo
from urllib.parse import urlsplit


def validate_output_url(value: str) -> str:
    if not value or len(value) > 2000:
        raise ValueError("URL ausente ou acima de 2000 caracteres")
    parsed = urlsplit(value)
    if parsed.scheme.lower() not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("URL insegura")
    return value


def validate_source_page_url(source: str, value: str) -> str:
    validate_output_url(value)
    parsed = urlsplit(value)
    if parsed.scheme.lower() != "https" or parsed.port not in (None, 443):
        raise ValueError("Página de origem precisa usar HTTPS na porta padrão")
    host = (parsed.hostname or "").lower()
    allowed = (
        source == "hacker-news" and host == "news.ycombinator.com" and parsed.path == "/item"
    ) or (
        source == "wordpress-news" and host == "wordpress.org" and parsed.path.startswith("/news/")
    )
    if not allowed:
        raise ValueError("Página de origem fora da allowlist")
    return value


def validate_redirect(source: str, destination: str) -> str:
    return validate_source_page_url(source, destination)


def validate_resolved_address(value: str) -> None:
    results = getaddrinfo(value, None, type=SOCK_STREAM)
    addresses = {result[4][0] for result in results}
    validate_resolved_addresses(addresses)


def validate_resolved_addresses(values: set[str]) -> None:
    if not values:
        raise ValueError("Host não resolveu para nenhum endereço")
    for value in values:
        address = ip_address(value)
        if not address.is_global or address.is_multicast or address.is_reserved or address.is_unspecified:
            raise ValueError("Destino resolvido não é público")
