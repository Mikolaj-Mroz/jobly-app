from contextlib import asynccontextmanager
from datetime import datetime, timezone
import logging
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import Settings, get_settings
from app.database import create_engine_and_sessionmaker, init_db
from app.routers import assistant, notifications, offers, user
from app.schemas import ScrapeSummaryDto
from app.services import notification_service
from app.services.llm_service import OpenAiCompatibleClient
from app.services.scraper_service import (
    ArbeitnowScraper,
    JobIngestionService,
    JustJoinItScraper,
    RemoteOkScraper,
)
from app.services.seed_service import seed_initial_data

logger = logging.getLogger(__name__)


def create_app(
    settings_override: Settings | None = None,
    llm_client_override: OpenAiCompatibleClient | None = None,
    ingestion_service_override: JobIngestionService | None = None,
) -> FastAPI:
    settings = settings_override or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        engine, session_maker = create_engine_and_sessionmaker(settings.database_url)
        app.state.settings = settings
        app.state.engine = engine
        app.state.session_maker = session_maker
        app.state.llm_client = llm_client_override or OpenAiCompatibleClient(settings)
        app.state.ingestion_service = ingestion_service_override or JobIngestionService(
            scrapers=[
                JustJoinItScraper(
                    items_per_page=settings.justjoin_items_per_page,
                    max_pages=settings.justjoin_max_pages,
                ),
                ArbeitnowScraper(),
                RemoteOkScraper(),
            ]
        )
        app.state.scheduler = None
        app.state.last_scrape_at = None
        app.state.last_scrape_summary = None
        app.state.last_digest_at = None
        app.state.last_digest_offer_id = None

        await init_db(engine)
        async with session_maker() as session:
            await seed_initial_data(session, settings)

        scheduler: AsyncIOScheduler | None = None
        if settings.enable_scheduler:
            scheduler = AsyncIOScheduler(timezone=timezone.utc)

            async def scheduled_scrape() -> None:
                try:
                    async with session_maker() as session:
                        sources_checked, fetched, inserted, notifs = (
                            await app.state.ingestion_service.sync_all(
                                session=session,
                                user_id=settings.default_user_id,
                            )
                        )
                    summary = ScrapeSummaryDto(
                        sources_checked=sources_checked,
                        fetched_count=fetched,
                        inserted_count=inserted,
                        notifications_created=notifs,
                    )
                    app.state.last_scrape_at = datetime.now(timezone.utc).isoformat()
                    app.state.last_scrape_summary = summary
                    logger.info(
                        "Scheduled job scrape finished: sources=%d fetched=%d inserted=%d notifications=%d",
                        sources_checked,
                        fetched,
                        inserted,
                        notifs,
                    )
                except Exception as exc:
                    logger.warning("Scheduled job scrape failed: %s", exc)

            async def scheduled_daily_digest() -> None:
                try:
                    async with session_maker() as session:
                        pushed, msg, notif = await notification_service.push_daily_best_match_notification(
                            session=session,
                            user_id=settings.default_user_id,
                            settings=settings,
                            force=False,
                        )
                    if pushed and notif is not None:
                        app.state.last_digest_at = datetime.now(timezone.utc).isoformat()
                        app.state.last_digest_offer_id = notif.offer_id
                    logger.info("Scheduled daily digest run: pushed=%s msg=%s", pushed, msg)
                except Exception as exc:
                    logger.warning("Scheduled daily digest failed: %s", exc)

            job_kwargs: dict[str, object] = {
                "minutes": settings.scrape_interval_minutes,
                "id": "scrape_job_offers",
                "replace_existing": True,
                "coalesce": True,
                "max_instances": 1,
            }
            if settings.scrape_on_startup:
                job_kwargs["next_run_time"] = datetime.now(timezone.utc)

            scheduler.add_job(scheduled_scrape, "interval", **job_kwargs)
            if settings.enable_daily_notification:
                scheduler.add_job(
                    scheduled_daily_digest,
                    "interval",
                    hours=settings.daily_notification_interval_hours,
                    id="daily_best_offer_notification",
                    replace_existing=True,
                    coalesce=True,
                    max_instances=1,
                )
            scheduler.start()
            app.state.scheduler = scheduler

        try:
            yield
        finally:
            if scheduler is not None:
                scheduler.shutdown(wait=False)
            await engine.dispose()

    application = FastAPI(
        title="Jobly Backend",
        version="0.1.0",
        description="FastAPI backend for Jobly: Offers, Recommendations, Notifications, User/PDF CVs, and AI Tool Calling.",
        lifespan=lifespan,
    )

    application.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @application.get("/health", tags=["health"])
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    application.include_router(offers.router)
    application.include_router(notifications.router)
    application.include_router(user.router)
    application.include_router(assistant.router)

    return application


app = create_app()
