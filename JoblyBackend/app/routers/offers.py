from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.models import ApplicationModel, CvModel, JobOfferModel, UserModel, utc_now
from app.schemas import (
    ApplyOfferRequestDto,
    ApplyOfferResponseDto,
    JobOfferDto,
    OffersResponseDto,
    ScrapeStatusDto,
    ScrapeSummaryDto,
    SearchOffersResponseDto,
)
from app.services import recommendation_service, search_service

router = APIRouter(prefix="/offers", tags=["offers"])


@router.get("/around-you", response_model=OffersResponseDto)
async def get_offers_around_you(
    request: Request,
    query: str = Query(default=""),
    lat: float | None = Query(default=None),
    lon: float | None = Query(default=None),
    radius_km: float | None = Query(default=None),
    db: AsyncSession = Depends(get_db),
) -> OffersResponseDto:
    settings = request.app.state.settings
    user = (
        await db.execute(select(UserModel).where(UserModel.id == settings.default_user_id))
    ).scalar_one_or_none()
    user_lat = lat if lat is not None else (user.latitude if user else settings.default_user_lat)
    user_lon = lon if lon is not None else (user.longitude if user else settings.default_user_lon)

    offers = list((await db.execute(select(JobOfferModel))).scalars().all())
    ranked = recommendation_service.score_around_you(
        offers=offers,
        user_lat=user_lat,
        user_lon=user_lon,
        radius_km=radius_km,
        query=query,
    )
    return OffersResponseDto(
        offers=[JobOfferDto.from_model(s.offer, match_reasons=s.match_reasons) for s in ranked]
    )


@router.get("/best-for-you", response_model=OffersResponseDto)
async def get_best_offers_for_you(
    request: Request,
    query: str = Query(default=""),
    cv_id: str | None = Query(default=None),
    db: AsyncSession = Depends(get_db),
) -> OffersResponseDto:
    settings = request.app.state.settings
    user = (
        await db.execute(select(UserModel).where(UserModel.id == settings.default_user_id))
    ).scalar_one_or_none()
    if cv_id:
        cv = (
            await db.execute(
                select(CvModel).where(CvModel.id == cv_id, CvModel.user_id == settings.default_user_id)
            )
        ).scalar_one_or_none()
    else:
        cv = (
            await db.execute(
                select(CvModel)
                .where(CvModel.user_id == settings.default_user_id)
                .order_by(CvModel.updated_at.desc())
            )
        ).scalars().first()

    app_rows = (
        await db.execute(
            select(ApplicationModel).where(ApplicationModel.user_id == settings.default_user_id)
        )
    ).scalars().all()
    applied_offers = [row.offer for row in app_rows if row.offer is not None]

    offers = list((await db.execute(select(JobOfferModel))).scalars().all())
    scored = recommendation_service.score_best_for_you(
        offers=offers,
        cv=cv,
        query=query,
        user_lat=user.latitude if user else settings.default_user_lat,
        user_lon=user.longitude if user else settings.default_user_lon,
        applied_offers=applied_offers,
    )
    return OffersResponseDto(
        offers=[
            JobOfferDto.from_model(
                s.offer,
                match_score=s.match_score,
                match_reasons=s.match_reasons,
            )
            for s in scored
        ]
    )


@router.get("/search", response_model=SearchOffersResponseDto)
async def search_job_offers(
    request: Request,
    q: str = Query(default=""),
    level: str | None = Query(default=None),
    contract_type: str | None = Query(default=None),
    work_mode: str | None = Query(default=None),
    city: str | None = Query(default=None),
    min_salary: int | None = Query(default=None, ge=0),
    sort: str = Query(default="relevance"),
    cv_id: str | None = Query(default=None),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: AsyncSession = Depends(get_db),
) -> SearchOffersResponseDto:
    settings = request.app.state.settings
    user = (
        await db.execute(select(UserModel).where(UserModel.id == settings.default_user_id))
    ).scalar_one_or_none()
    if cv_id:
        cv = (
            await db.execute(
                select(CvModel).where(CvModel.id == cv_id, CvModel.user_id == settings.default_user_id)
            )
        ).scalar_one_or_none()
    else:
        cv = (
            await db.execute(
                select(CvModel)
                .where(CvModel.user_id == settings.default_user_id)
                .order_by(CvModel.updated_at.desc())
            )
        ).scalars().first()

    app_rows = (
        await db.execute(
            select(ApplicationModel).where(ApplicationModel.user_id == settings.default_user_id)
        )
    ).scalars().all()
    applied_offers = [row.offer for row in app_rows if row.offer is not None]

    offers = list((await db.execute(select(JobOfferModel))).scalars().all())
    total, results = search_service.search_offers(
        offers=offers,
        cv=cv,
        query=q,
        level=level,
        contract_type=contract_type,
        work_mode=work_mode,
        city=city,
        min_salary=min_salary,
        sort=sort,
        user_lat=user.latitude if user else settings.default_user_lat,
        user_lon=user.longitude if user else settings.default_user_lon,
        applied_offers=applied_offers,
        limit=limit,
        offset=offset,
    )
    return SearchOffersResponseDto(
        query=q.strip(),
        total=total,
        offers=[
            JobOfferDto.from_model(
                s.offer,
                match_score=s.match_score,
                match_reasons=s.match_reasons,
            )
            for s in results
        ],
    )


@router.post("/{offer_id}/apply", response_model=ApplyOfferResponseDto)
async def apply_to_offer(
    offer_id: str,
    body: ApplyOfferRequestDto,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> ApplyOfferResponseDto:
    settings = request.app.state.settings
    offer = (
        await db.execute(select(JobOfferModel).where(JobOfferModel.id == offer_id))
    ).scalar_one_or_none()
    if offer is None:
        raise HTTPException(status_code=404, detail=f"Offer '{offer_id}' not found")

    existing = (
        await db.execute(
            select(ApplicationModel).where(
                ApplicationModel.user_id == settings.default_user_id,
                ApplicationModel.offer_id == offer_id,
            )
        )
    ).scalar_one_or_none()
    if existing is not None:
        return ApplyOfferResponseDto(
            applied=False,
            message="Already applied to this offer",
            external_url=offer.external_url,
        )

    app_row = ApplicationModel(
        id=f"app_{int(utc_now().timestamp() * 1000)}_{offer_id}",
        user_id=settings.default_user_id,
        offer_id=offer_id,
        cv_id=body.cv_id,
    )
    db.add(app_row)
    await db.commit()
    return ApplyOfferResponseDto(
        applied=True,
        message=f"Applied to {offer.title}",
        external_url=offer.external_url,
    )


@router.post("/scrape", response_model=ScrapeSummaryDto)
async def trigger_scrape(
    request: Request,
    source: str | None = Query(default=None),
    db: AsyncSession = Depends(get_db),
) -> ScrapeSummaryDto:
    settings = request.app.state.settings
    ingestion_service = request.app.state.ingestion_service

    if source is not None:
        normalized = source.strip().lower()
        valid_sources = {name.lower() for name in ingestion_service.source_names}
        if not normalized or normalized not in valid_sources:
            raise HTTPException(
                status_code=400,
                detail=f"Unknown scrape source '{source}'. Available sources: {ingestion_service.source_names}",
            )

    sources_checked, fetched, inserted, notifs = await ingestion_service.sync_all(
        session=db,
        user_id=settings.default_user_id,
        source_filter=source,
    )
    summary = ScrapeSummaryDto(
        sources_checked=sources_checked,
        fetched_count=fetched,
        inserted_count=inserted,
        notifications_created=notifs,
    )
    request.app.state.last_scrape_at = utc_now().isoformat()
    request.app.state.last_scrape_summary = summary
    return summary


@router.get("/scrape/status", response_model=ScrapeStatusDto)
async def get_scrape_status(request: Request) -> ScrapeStatusDto:
    settings = request.app.state.settings
    ingestion_service = request.app.state.ingestion_service
    scheduler = getattr(request.app.state, "scheduler", None)
    next_run_at: str | None = None
    if scheduler is not None:
        job = scheduler.get_job("scrape_job_offers")
        if job is not None and job.next_run_time is not None:
            next_run_at = job.next_run_time.isoformat()

    return ScrapeStatusDto(
        scheduler_enabled=settings.enable_scheduler,
        interval_minutes=settings.scrape_interval_minutes,
        scrape_on_startup=settings.scrape_on_startup,
        next_run_at=next_run_at,
        last_run_at=getattr(request.app.state, "last_scrape_at", None),
        last_summary=getattr(request.app.state, "last_scrape_summary", None),
        sources=ingestion_service.source_names,
    )
