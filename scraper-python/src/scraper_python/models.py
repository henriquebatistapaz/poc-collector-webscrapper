from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, PrivateAttr, field_validator

from .safety import validate_output_url


class CollectedItemInput(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, serialize_by_alias=True)

    source: str = Field(min_length=1, max_length=40)
    external_id: str = Field(alias="externalId", min_length=1, max_length=2000)
    title: str = Field(min_length=1, max_length=1000)
    link: str = Field(max_length=2000)
    published_at: datetime | None = Field(alias="publishedAt")

    @field_validator("link")
    @classmethod
    def link_must_be_absolute_http_url(cls, value: str) -> str:
        return validate_output_url(value)


class PageContent(BaseModel):
    main_text: str
    comments: list[str]


class CollectedEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, serialize_by_alias=True)

    schema_version: str = Field(alias="schemaVersion", pattern=r"^1\.0$")
    collected_at: datetime = Field(alias="collectedAt")
    source_page_url: str = Field(alias="sourcePageUrl", max_length=2000)
    item: CollectedItemInput
    entities: list[dict[str, Any]]
    _page_content: PageContent = PrivateAttr()

    def set_page_content(self, content: PageContent) -> None:
        self._page_content = content

    @property
    def page_content(self) -> PageContent:
        return self._page_content
