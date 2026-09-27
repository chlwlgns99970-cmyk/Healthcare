from __future__ import annotations


class FoodAnalysisError(Exception):
    def __init__(
        self,
        code: str,
        message: str,
        *,
        status_code: int,
        retryable: bool = False,
        request_id: str | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.retryable = retryable
        self.request_id = request_id


class ProviderNotConfiguredError(Exception):
    pass


class ProviderTimeoutError(Exception):
    pass


class ProviderRateLimitError(Exception):
    pass


class ProviderInvalidResponseError(Exception):
    pass


class ProviderIncompleteResponseError(Exception):
    pass


class ProviderRefusalError(Exception):
    pass


class ProviderAuthenticationError(Exception):
    pass


class ProviderModelAccessError(Exception):
    pass


class ProviderUpstreamError(Exception):
    pass
