from threading import BoundedSemaphore, Lock


class HostRequestGate:
    """Serializes requests to the same host; callers still enforce source permission."""

    def __init__(self) -> None:
        self._lock = Lock()
        self._gates: dict[str, BoundedSemaphore] = {}

    def acquire(self, host: str) -> BoundedSemaphore:
        with self._lock:
            gate = self._gates.setdefault(host.lower(), BoundedSemaphore(1))
        gate.acquire()
        return gate
