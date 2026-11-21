import json
import math
from pathlib import Path
from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.models import (
    ApplicationModel,
    CvModel,
    FeedbackModel,
    UserModel,
    UserSettingsModel,
    utc_now,
)
from app.schemas import (
    ApplicationsResponseDto,
    CreateCvRequestDto,
    CvDto,
    CvStructuredData,
    CvsResponseDto,
    DeleteCvResponseDto,
    FeedbackRequestDto,
    FeedbackResponseDto,
    JobOfferDto,
    UpdateCvContentRequestDto,
    UpdateProfileRequestDto,
    UpdateUserSettingsRequestDto,
    UserProfileDto,
    UserSettingsDto,
)
from app.services import pdf_service

router = APIRouter(prefix="/user", tags=["user"])

_ALLOWED_PDF_CONTENT_TYPES = {
    "",
    "application/pdf",
    "application/x-pdf",
    "application/octet-stream",
}


@router.get("/profile", response_model=UserProfileDto)
async def get_profile(
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> UserProfileDto:
    user = await _get_default_user(request, db)
    return UserProfileDto(name=user.name, phone=user.phone, email=user.email)


@router.put("/profile", response_model=UserProfileDto)
async def update_profile(
    body: UpdateProfileRequestDto,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> UserProfileDto:
    user = await _get_default_user(request, db)
    user.name = body.name.strip() or user.name
    user.phone = body.phone.strip() or user.phone
    user.email = body.email.strip() or user.email
    await db.commit()
    return UserProfileDto(name=user.name, phone=user.phone, email=user.email)


@router.get("/applications", response_model=ApplicationsResponseDto)
async def get_applications(
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> ApplicationsResponseDto:
    settings = request.app.state.settings
    rows = (
        await db.execute(
            select(ApplicationModel)
            .where(ApplicationModel.user_id == settings.default_user_id)
            .order_by(ApplicationModel.created_at.desc())
        )
    ).scalars().all()
    return ApplicationsResponseDto(applications=[JobOfferDto.from_model(r.offer) for r in rows])


@router.get("/cvs", response_model=CvsResponseDto)
async def get_cvs(
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> CvsResponseDto:
    settings = request.app.state.settings
    rows = (
        await db.execute(
            select(CvModel)
            .where(CvModel.user_id == settings.default_user_id)
            .order_by(CvModel.updated_at.desc())
        )
    ).scalars().all()
    dtos = []
    for row in rows:
        pages, size_bytes = pdf_service.inspect_pdf_file(Path(row.file_path))
        dtos.append(CvDto.from_model(row, page_count=pages, file_size_bytes=size_bytes))
    return CvsResponseDto(cvs=dtos)


@router.post("/cvs", response_model=CvDto)
async def create_cv(
    body: CreateCvRequestDto,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> CvDto:
    settings = request.app.state.settings
    user = await _get_default_user(request, db)
    await _enforce_cv_count_limit(db, user.id, settings.max_cvs_per_user)

    safe_name = pdf_service.sanitize_filename(body.file_name)
    cv_id = f"cv_{int(utc_now().timestamp() * 1000)}"

    structured = CvStructuredData(
        full_name=user.name,
        phone=user.phone,
        email=user.email,
        headline="",
        summary="",
        skills=[],
        experience=[],
        education="",
        languages=[],
        certifications=[],
        extra_sections={},
    )
    target_path = settings.cvs_dir / f"{cv_id}_{safe_name}"
    pdf_service.render_cv_pdf(structured, target_path)
    raw_text = pdf_service.build_plain_text(structured)
    embedding = await request.app.state.llm_client.embed_text(raw_text)

    cv = CvModel(
        id=cv_id,
        user_id=user.id,
        file_name=safe_name,
        updated_label="Just added",
        file_path=str(target_path),
        raw_text=raw_text,
        structured_json=structured.model_dump_json(),
        embedding_json=json.dumps(embedding) if embedding is not None else None,
    )
    db.add(cv)
    await db.commit()
    pages, size_bytes = pdf_service.inspect_pdf_file(target_path)
    return CvDto.from_model(cv, page_count=pages, file_size_bytes=size_bytes)


@router.post("/cvs/upload", response_model=CvDto)
async def upload_cv_pdf(
    request: Request,
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
) -> CvDto:
    settings = request.app.state.settings
    user = await _get_default_user(request, db)
    await _enforce_cv_count_limit(db, user.id, settings.max_cvs_per_user)

    raw_filename = (file.filename or "Uploaded-cv.pdf").strip()
    suffix = Path(raw_filename).suffix.lower()
    content_type = (file.content_type or "").split(";", 1)[0].strip().lower()
    if suffix and suffix != ".pdf":
        raise HTTPException(status_code=400, detail="Only .pdf files are supported")
    if content_type not in _ALLOWED_PDF_CONTENT_TYPES:
        raise HTTPException(status_code=400, detail="Uploaded file must be a PDF (application/pdf)")
    if not suffix and content_type not in {"application/pdf", "application/x-pdf"}:
        raise HTTPException(status_code=400, detail="Uploaded file must have a .pdf extension")

    # Bounded streaming read so oversized uploads fail fast without buffering unbounded RAM
    max_bytes = settings.max_cv_file_size_bytes
    buffer = bytearray()
    while True:
        chunk = await file.read(65536)
        if not chunk:
            break
        buffer.extend(chunk)
        if len(buffer) > max_bytes:
            limit_mb = f"{max_bytes / (1024 * 1024):.1f}".rstrip("0").rstrip(".")
            raise HTTPException(
                status_code=413,
                detail=f"PDF exceeds maximum size limit of {limit_mb} MB.",
            )

    content = bytes(buffer)
    try:
        page_count, extracted = pdf_service.validate_and_extract_pdf(
            content=content,
            max_bytes=max_bytes,
            max_pages=settings.max_cv_pages,
        )
    except pdf_service.PdfValidationError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc

    safe_name = pdf_service.sanitize_filename(raw_filename)
    cv_id = f"cv_{int(utc_now().timestamp() * 1000)}"
    target_path = settings.cvs_dir / f"{cv_id}_{safe_name}"
    target_path.write_bytes(content)

    structured = pdf_service.parse_cv_text_to_structured(
        raw_text=extracted,
        fallback_name=user.name,
        fallback_phone=user.phone,
        fallback_email=user.email,
    )
    plain_text = extracted or pdf_service.build_plain_text(structured)
    embedding = await request.app.state.llm_client.embed_text(plain_text or safe_name)

    cv = CvModel(
        id=cv_id,
        user_id=user.id,
        file_name=safe_name,
        updated_label="Uploaded just now",
        file_path=str(target_path),
        raw_text=plain_text,
        structured_json=structured.model_dump_json(),
        embedding_json=json.dumps(embedding) if embedding is not None else None,
    )
    db.add(cv)
    await pdf_service.sync_user_location_from_cv_text(db, user, plain_text)
    await db.commit()
    return CvDto.from_model(cv, page_count=page_count, file_size_bytes=len(content))


@router.put("/cvs/{cv_id}", response_model=CvDto)
async def edit_cv(
    cv_id: str,
    body: UpdateCvContentRequestDto,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> CvDto:
    settings = request.app.state.settings
    user = await _get_default_user(request, db)
    cv = (
        await db.execute(
            select(CvModel).where(CvModel.id == cv_id, CvModel.user_id == settings.default_user_id)
        )
    ).scalar_one_or_none()
    if cv is None:
        raise HTTPException(status_code=404, detail=f"CV '{cv_id}' not found")

    try:
        structured = CvStructuredData.model_validate_json(cv.structured_json)
    except Exception:
        structured = pdf_service.parse_cv_text_to_structured(
            raw_text=cv.raw_text or "",
            fallback_name=user.name,
            fallback_phone=user.phone,
            fallback_email=user.email,
        )

    if body.headline is not None:
        structured.headline = body.headline
    if body.summary is not None:
        structured.summary = body.summary
    if body.skills is not None:
        structured.skills = body.skills
    if body.experience is not None:
        structured.experience = body.experience
    if body.education is not None:
        structured.education = body.education
    if body.languages is not None:
        structured.languages = body.languages
    if body.certifications is not None:
        structured.certifications = body.certifications
    if body.file_name:
        cv.file_name = pdf_service.sanitize_filename(body.file_name)

    target_path = Path(cv.file_path)
    pdf_service.render_cv_pdf(structured, target_path)
    raw_text = pdf_service.build_plain_text(structured)
    embedding = await request.app.state.llm_client.embed_text(raw_text)

    cv.updated_label = "Updated just now"
    cv.raw_text = raw_text
    cv.structured_json = structured.model_dump_json()
    if embedding is not None:
        cv.embedding_json = json.dumps(embedding)
    cv.updated_at = utc_now()
    edited_chunks = "\n".join(
        chunk
        for chunk in [
            body.headline or "",
            body.summary or "",
            body.education or "",
            "\n".join(body.experience or []),
        ]
        if chunk.strip()
    )
    await pdf_service.sync_user_location_from_cv_text(
        db, user, raw_text, preferred_text=edited_chunks or None
    )
    await db.commit()
    pages, size_bytes = pdf_service.inspect_pdf_file(target_path)
    return CvDto.from_model(cv, page_count=pages, file_size_bytes=size_bytes)


@router.delete("/cvs/{cv_id}", response_model=DeleteCvResponseDto)
async def delete_cv(
    cv_id: str,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> DeleteCvResponseDto:
    settings = request.app.state.settings
    cv = (
        await db.execute(
            select(CvModel).where(CvModel.id == cv_id, CvModel.user_id == settings.default_user_id)
        )
    ).scalar_one_or_none()
    if cv is None:
        raise HTTPException(status_code=404, detail=f"CV '{cv_id}' not found")

    await db.execute(
        update(ApplicationModel).where(ApplicationModel.cv_id == cv_id).values(cv_id=None)
    )
    file_path = Path(cv.file_path)
    await db.delete(cv)
    await db.commit()
    file_path.unlink(missing_ok=True)
    return DeleteCvResponseDto(deleted=True, id=cv_id)


@router.get("/cvs/{cv_id}/download")
async def download_cv_pdf(
    cv_id: str,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> FileResponse:
    settings = request.app.state.settings
    cv = (
        await db.execute(
            select(CvModel).where(CvModel.id == cv_id, CvModel.user_id == settings.default_user_id)
        )
    ).scalar_one_or_none()
    if cv is None:
        raise HTTPException(status_code=404, detail=f"CV '{cv_id}' not found")

    file_path = Path(cv.file_path)
    if not file_path.exists():
        raise HTTPException(status_code=404, detail="CV PDF file missing on disk")

    return FileResponse(
        path=file_path,
        media_type="application/pdf",
        filename=cv.file_name,
        headers={"Cache-Control": "no-store"},
    )


@router.get("/settings", response_model=UserSettingsDto)
async def get_user_settings(
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> UserSettingsDto:
    user = await _get_default_user(request, db)
    row = await _get_or_create_user_settings(db, user)
    return _settings_to_dto(row, user)


@router.put("/settings", response_model=UserSettingsDto)
async def update_user_settings(
    body: UpdateUserSettingsRequestDto,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> UserSettingsDto:
    user = await _get_default_user(request, db)
    row = await _get_or_create_user_settings(db, user)

    if body.job_alerts_enabled is not None:
        row.job_alerts_enabled = body.job_alerts_enabled
    if body.daily_digest_enabled is not None:
        row.daily_digest_enabled = body.daily_digest_enabled
    if body.radius_km is not None and math.isfinite(body.radius_km):
        row.radius_km = max(5.0, min(500.0, float(body.radius_km)))
    if body.currency is not None and body.currency.strip():
        row.currency = body.currency.strip().upper()[:16]
    if body.city is not None and body.city.strip():
        row.city = body.city.strip()[:80]
    if body.onboarding_completed is not None:
        row.onboarding_completed = body.onboarding_completed
    if body.latitude is not None and math.isfinite(body.latitude):
        user.latitude = float(body.latitude)
    if body.longitude is not None and math.isfinite(body.longitude):
        user.longitude = float(body.longitude)

    row.updated_at = utc_now()
    await db.commit()
    await db.refresh(row)
    await db.refresh(user)
    return _settings_to_dto(row, user)


@router.post("/feedback", response_model=FeedbackResponseDto)
async def submit_feedback(
    body: FeedbackRequestDto,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> FeedbackResponseDto:
    user = await _get_default_user(request, db)
    trimmed = body.message.strip()
    if not trimmed:
        raise HTTPException(status_code=400, detail="Feedback message cannot be empty")
    if len(trimmed) > 2000:
        raise HTTPException(status_code=400, detail="Feedback message must be 2000 characters or fewer")

    category = (body.category.strip() or "General")[:40]
    contact_email = body.contact_email.strip()[:160] if body.contact_email else user.email
    fb_id = f"fb_{int(utc_now().timestamp() * 1000)}"
    row = FeedbackModel(
        id=fb_id,
        user_id=user.id,
        category=category,
        message=trimmed,
        contact_email=contact_email or None,
    )
    db.add(row)
    await db.commit()
    return FeedbackResponseDto(
        id=fb_id,
        category=category,
        message=trimmed,
        status="received",
    )


async def _get_or_create_user_settings(
    db: AsyncSession, user: UserModel
) -> UserSettingsModel:
    row = (
        await db.execute(select(UserSettingsModel).where(UserSettingsModel.user_id == user.id))
    ).scalar_one_or_none()
    if row is None:
        row = UserSettingsModel(user_id=user.id)
        db.add(row)
        await db.commit()
        await db.refresh(row)
    return row


def _settings_to_dto(row: UserSettingsModel, user: UserModel) -> UserSettingsDto:
    return UserSettingsDto(
        job_alerts_enabled=row.job_alerts_enabled,
        daily_digest_enabled=row.daily_digest_enabled,
        radius_km=row.radius_km,
        currency=row.currency,
        city=row.city,
        latitude=user.latitude,
        longitude=user.longitude,
        onboarding_completed=row.onboarding_completed,
    )


async def _enforce_cv_count_limit(db: AsyncSession, user_id: str, max_cvs: int) -> None:
    count = (
        await db.execute(select(func.count(CvModel.id)).where(CvModel.user_id == user_id))
    ).scalar_one()
    if count >= max_cvs:
        raise HTTPException(
            status_code=400,
            detail=f"Maximum limit of {max_cvs} CVs reached. Delete an existing CV first.",
        )


async def _get_default_user(request: Request, db: AsyncSession) -> UserModel:
    settings = request.app.state.settings
    user = (
        await db.execute(select(UserModel).where(UserModel.id == settings.default_user_id))
    ).scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=404, detail="Default user not initialized")
    return user
