from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.config import Settings
from app.models import (
    ApplicationModel,
    CvModel,
    JobOfferModel,
    NotificationModel,
    UserModel,
    UserSettingsModel,
    utc_now,
)
from app.services import recommendation_service


async def push_daily_best_match_notification(
    session: AsyncSession,
    user_id: str,
    settings: Settings,
    force: bool = False,
) -> tuple[bool, str, NotificationModel | None]:
    """Select the top-matched job offer and create an unread daily digest notification."""
    user = (
        await session.execute(select(UserModel).where(UserModel.id == user_id))
    ).scalar_one_or_none()
    if user is None:
        return False, "User not found", None

    user_settings = (
        await session.execute(select(UserSettingsModel).where(UserSettingsModel.user_id == user_id))
    ).scalar_one_or_none()
    if not force and user_settings is not None:
        if not user_settings.job_alerts_enabled or not user_settings.daily_digest_enabled:
            return False, "Daily best-match alerts are disabled in settings", None

    cv = (
        await session.execute(
            select(CvModel)
            .where(CvModel.user_id == user_id)
            .order_by(CvModel.updated_at.desc())
        )
    ).scalars().first()

    app_rows = (
        await session.execute(
            select(ApplicationModel).where(ApplicationModel.user_id == user_id)
        )
    ).scalars().all()
    applied_offers = [row.offer for row in app_rows if row.offer is not None]
    applied_ids = {row.offer_id for row in app_rows}

    existing_notifs = (
        await session.execute(
            select(NotificationModel.offer_id).where(NotificationModel.user_id == user_id)
        )
    ).scalars().all()
    notified_ids = set(existing_notifs)

    offers = list((await session.execute(select(JobOfferModel))).scalars().all())
    if not offers:
        return False, "No job offers available", None

    scored = recommendation_service.score_best_for_you(
        offers=offers,
        cv=cv,
        user_lat=user.latitude if user else settings.default_user_lat,
        user_lon=user.longitude if user else settings.default_user_lon,
        applied_offers=applied_offers,
    )
    if not scored:
        return False, "No job offers available", None

    chosen = (
        next((s for s in scored if s.offer.id not in applied_ids and s.offer.id not in notified_ids), None)
        or next((s for s in scored if s.offer.id not in applied_ids), None)
        or scored[0]
    )

    score_pct = chosen.match_score if chosen.match_score is not None else 50
    offer = chosen.offer
    notif = NotificationModel(
        id=f"daily_{int(utc_now().timestamp() * 1000)}_{offer.id[:12]}",
        user_id=user.id,
        heading=f"Daily best match ({score_pct}% match)",
        offer_id=offer.id,
        is_read=False,
    )
    session.add(notif)
    await session.commit()

    reloaded = (
        await session.execute(select(NotificationModel).where(NotificationModel.id == notif.id))
    ).scalar_one_or_none()
    return True, f"Pushed daily best match: {offer.title}", reloaded
