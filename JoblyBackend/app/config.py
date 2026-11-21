from functools import lru_cache
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = "sqlite+aiosqlite:///./jobly.db"
    llm_base_url: str = "http://127.0.0.1:1234/v1"
    llm_api_key: str = "lm-studio"
    llm_timeout_seconds: float = 120.0
    storage_dir: str = "./storage"
    enable_scheduler: bool = True
    scrape_interval_minutes: int = 60
    scrape_on_startup: bool = False
    enable_daily_notification: bool = True
    daily_notification_interval_hours: int = 24
    justjoin_items_per_page: int = 50
    justjoin_max_pages: int = 2
    max_cv_file_size_bytes: int = 5 * 1024 * 1024
    max_cv_pages: int = 10
    max_cvs_per_user: int = 10
    default_user_id: str = "default_user"
    default_user_lat: float = 50.0647  # Krakow
    default_user_lon: float = 19.9450

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def cvs_dir(self) -> Path:
        path = Path(self.storage_dir) / "cvs"
        path.mkdir(parents=True, exist_ok=True)
        return path


@lru_cache
def get_settings() -> Settings:
    return Settings()
