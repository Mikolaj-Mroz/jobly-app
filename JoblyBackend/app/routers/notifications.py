from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.models import NotificationModel, utc_now
from app.schemas import (
    DailyDigestResultDto,
    DailyDigestStatusDto,
    NotificationDto,
    NotificationReadResponseDto,
    NotificationsResponseDto,
)
from app.services import notification_service

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.get("", response_model=NotificationsResponseDto)
@router.get("/", response_model=NotificationsResponseDto, include_in_schema=False)
async def list_notifications(
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> NotificationsResponseDto:
    settings = request.app.state.settings
    rows = (
        await db.execute(
            select(NotificationModel)
            .where(NotificationModel.user_id == settings.default_user_id)
            .order_by(NotificationModel.created_at.desc())
        )
    ).scalars().all()
    return NotificationsResponseDto(notifications=[NotificationDto.from_model(r) for r in rows])


@router.patch("/{notification_id}/read", response_model=NotificationReadResponseDto)
async def mark_notification_read(
    notification_id: str,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> NotificationReadResponseDto:
    settings = request.app.state.settings
    row = (
        await db.execute(
            select(NotificationModel).where(
                NotificationModel.id == notification_id,
                NotificationModel.user_id == settings.default_user_id,
            )
        )
    ).scalar_one_or_none()
    if row is None:
        raise HTTPException(status_code=404, detail=f"Notification '{notification_id}' not found")

    if not row.is_read:
        row.is_read = True
        await db.commit()

    return NotificationReadResponseDto(id=row.id, is_read=True)


@router.post("/daily-digest", response_model=DailyDigestResultDto)
async def trigger_daily_digest(
    request: Request,
    force: bool = Query(default=False),
    db: AsyncSession = Depends(get_db),
) -> DailyDigestResultDto:
    settings = request.app.state.settings
    pushed, message, notif = await notification_service.push_daily_best_match_notification(
        session=db,
        user_id=settings.default_user_id,
        settings=settings,
        force=force,
    )
    if pushed and notif is not None:
        request.app.state.last_digest_at = utc_now().isoformat()
        request.app.state.last_digest_offer_id = notif.offer_id

    return DailyDigestResultDto(
        pushed=pushed,
        message=message,
        notification=NotificationDto.from_model(notif) if notif is not None else None,
    )


@router.get("/digest/status", response_model=DailyDigestStatusDto)
async def get_daily_digest_status(request: Request) -> DailyDigestStatusDto:
    settings = request.app.state.settings
    scheduler = getattr(request.app.state, "scheduler", None)
    next_run_at: str | None = None
    if scheduler is not None:
        job = scheduler.get_job("daily_best_offer_notification")
        if job is not None and job.next_run_time is not None:
            next_run_at = job.next_run_time.isoformat()

    return DailyDigestStatusDto(
        scheduler_enabled=settings.enable_scheduler,
        daily_enabled=settings.enable_daily_notification,
        interval_hours=settings.daily_notification_interval_hours,
        next_run_at=next_run_at,
        last_run_at=getattr(request.app.state, "last_digest_at", None),
        last_offer_id=getattr(request.app.state, "last_digest_offer_id", None),
    )
