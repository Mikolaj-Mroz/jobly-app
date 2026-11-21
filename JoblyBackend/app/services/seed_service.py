import json
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.config import Settings
from app.models import ApplicationModel, CvModel, JobOfferModel, NotificationModel, UserModel
from app.schemas import CvStructuredData
from app.services import pdf_service


_SEED_URLS: dict[str, str] = {
    "close_1": "https://justjoin.it/job-offers/krakow/testing?offer=seed_close_1",
    "close_2": "https://justjoin.it/job-offers/krakow/testing?offer=seed_close_2",
    "close_3": "https://justjoin.it/job-offers/krakow/testing?offer=seed_close_3",
    "best_1": "https://justjoin.it/job-offers/warszawa/testing?offer=seed_best_1",
    "best_2": "https://justjoin.it/job-offers/wroclaw/testing?offer=seed_best_2",
    "best_3": "https://justjoin.it/job-offers/gdansk/testing?offer=seed_best_3",
}


async def seed_initial_data(session: AsyncSession, settings: Settings) -> None:
    existing_user = (
        await session.execute(select(UserModel).where(UserModel.id == settings.default_user_id))
    ).scalar_one_or_none()
    if existing_user is not None:
        # Backfill external_url for already-seeded offers on existing local DBs
        rows = (
            await session.execute(select(JobOfferModel).where(JobOfferModel.id.in_(_SEED_URLS)))
        ).scalars().all()
        updated = False
        for row in rows:
            if not row.external_url:
                row.external_url = _SEED_URLS[row.id]
                updated = True
        if updated:
            await session.commit()
        return

    user = UserModel(
        id=settings.default_user_id,
        name="John Workman",
        phone="+1 123 456 789",
        email="jworkman@mail.com",
        latitude=settings.default_user_lat,
        longitude=settings.default_user_lon,
    )
    session.add(user)

    offers = [
        JobOfferModel(
            id="close_1",
            initial="A",
            title="Quality Assurance Engineer",
            salary_range="16 800 - 22 680",
            salary_unit="PLN/month",
            skills_json=json.dumps(["Kubernetes", "Elk", "Linux"]),
            company_name="IT DEV COMPANY sp. z o.o.",
            level="MID",
            contract_type="B2B",
            work_schedule="Full-Time",
            accent="YELLOW",
            description="Automate end-to-end tests across Kubernetes microservices and ELK observability pipelines in Krakow.",
            source="seed",
            external_url=_SEED_URLS["close_1"],
            latitude=50.0647,
            longitude=19.9450,
        ),
        JobOfferModel(
            id="close_2",
            initial="A",
            title="Quality Assurance Engineer",
            salary_range="16 800 - 22 680",
            salary_unit="PLN/month",
            skills_json=json.dumps(["Kubernetes", "Elk", "Linux"]),
            company_name="IT DEV COMPANY sp. z o.o.",
            level="MID",
            contract_type="B2B",
            work_schedule="Full-Time",
            accent="BLUE",
            description="Design resilient CI quality gates and integration harnesses for cloud-native Linux services.",
            source="seed",
            external_url=_SEED_URLS["close_2"],
            latitude=50.0700,
            longitude=19.9500,
        ),
        JobOfferModel(
            id="close_3",
            initial="A",
            title="Quality Assurance Engineer",
            salary_range="16 800 - 22 680",
            salary_unit="PLN/month",
            skills_json=json.dumps(["Kubernetes", "Elk", "Linux"]),
            company_name="IT DEV COMPANY sp. z o.o.",
            level="MID",
            contract_type="B2B",
            work_schedule="Full-Time",
            accent="YELLOW_ALT",
            description="Lead regression verification and performance profiling for high-throughput enterprise APIs.",
            source="seed",
            external_url=_SEED_URLS["close_3"],
            latitude=50.0800,
            longitude=19.9600,
        ),
        JobOfferModel(
            id="best_1",
            initial="A",
            title="Quality Assurance Engineer",
            salary_range="16 800 - 22 680",
            salary_unit="PLN/month",
            skills_json=json.dumps(["Kubernetes", "Elk", "Linux"]),
            company_name="IT DEV COMPANY sp. z o.o.",
            level="MID",
            contract_type="B2B",
            work_schedule="Full-Time",
            accent="PINK",
            description="High-match remote-hybrid QA role aligned with your Kubernetes and Linux testing experience.",
            source="seed",
            external_url=_SEED_URLS["best_1"],
            latitude=52.2297,
            longitude=21.0122,
        ),
        JobOfferModel(
            id="best_2",
            initial="A",
            title="Quality Assurance Engineer",
            salary_range="16 800 - 22 680",
            salary_unit="PLN/month",
            skills_json=json.dumps(["Kubernetes", "Elk", "Linux"]),
            company_name="IT DEV COMPANY sp. z o.o.",
            level="MID",
            contract_type="B2B",
            work_schedule="Full-Time",
            accent="GREEN",
            description="Collaborate with platform engineers to validate cluster deployments and log alerting.",
            source="seed",
            external_url=_SEED_URLS["best_2"],
            latitude=51.1079,
            longitude=17.0385,
        ),
        JobOfferModel(
            id="best_3",
            initial="A",
            title="Quality Assurance Engineer",
            salary_range="16 800 - 22 680",
            salary_unit="PLN/month",
            skills_json=json.dumps(["Kubernetes", "Elk", "Linux"]),
            company_name="IT DEV COMPANY sp. z o.o.",
            level="MID",
            contract_type="B2B",
            work_schedule="Full-Time",
            accent="YELLOW_ALT",
            description="Own release sign-off workflows and automated test suites for core customer products.",
            source="seed",
            external_url=_SEED_URLS["best_3"],
            latitude=54.3520,
            longitude=18.6466,
        ),
    ]
    session.add_all(offers)

    cv_data = CvStructuredData(
        full_name=user.name,
        phone=user.phone,
        email=user.email,
    )
    cv_path = settings.cvs_dir / "cv_1_My-new-cv.pdf"
    pdf_service.render_cv_pdf(cv_data, cv_path)
    cv = CvModel(
        id="cv_1",
        user_id=user.id,
        file_name="My-new-cv.pdf",
        updated_label="Updated today",
        file_path=str(cv_path),
        raw_text=pdf_service.build_plain_text(cv_data),
        structured_json=cv_data.model_dump_json(),
    )
    session.add(cv)

    session.add(
        ApplicationModel(
            id="app_1",
            user_id=user.id,
            offer_id="close_1",
            cv_id=cv.id,
        )
    )

    session.add_all(
        [
            NotificationModel(
                id="notif_1",
                user_id=user.id,
                heading="New job offer for you",
                offer_id="close_1",
                is_read=False,
            ),
            NotificationModel(
                id="notif_2",
                user_id=user.id,
                heading="New job offer for you",
                offer_id="best_1",
                is_read=False,
            ),
            NotificationModel(
                id="notif_3",
                user_id=user.id,
                heading="New job offer for you",
                offer_id="close_2",
                is_read=False,
            ),
        ]
    )

    await session.commit()
