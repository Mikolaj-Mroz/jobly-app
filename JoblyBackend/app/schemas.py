import json
from pydantic import BaseModel, Field


class JobOfferDto(BaseModel):
    id: str
    initial: str = "A"
    title: str
    salary_range: str
    salary_unit: str = "PLN/month"
    skills: list[str] = Field(default_factory=list)
    company_name: str
    level: str = "MID"
    contract_type: str = "B2B"
    work_schedule: str = "Full-Time"
    accent: str = "YELLOW"
    description: str = ""
    external_url: str | None = None
    match_score: int | None = None
    match_reasons: list[str] = Field(default_factory=list)

    @classmethod
    def from_model(
        cls,
        model,
        match_score: int | None = None,
        match_reasons: list[str] | None = None,
    ) -> "JobOfferDto":
        try:
            skills = json.loads(model.skills_json or "[]")
            if not isinstance(skills, list):
                skills = []
        except json.JSONDecodeError:
            skills = []
        return cls(
            id=model.id,
            initial=model.initial,
            title=model.title,
            salary_range=model.salary_range,
            salary_unit=model.salary_unit,
            skills=[str(s) for s in skills],
            company_name=model.company_name,
            level=model.level,
            contract_type=model.contract_type,
            work_schedule=model.work_schedule,
            accent=model.accent,
            description=model.description,
            external_url=getattr(model, "external_url", None),
            match_score=match_score,
            match_reasons=list(match_reasons or []),
        )


class OffersResponseDto(BaseModel):
    offers: list[JobOfferDto] = Field(default_factory=list)


class SearchOffersResponseDto(BaseModel):
    query: str = ""
    total: int = 0
    offers: list[JobOfferDto] = Field(default_factory=list)


class ApplyOfferRequestDto(BaseModel):
    cv_id: str | None = None


class ApplyOfferResponseDto(BaseModel):
    applied: bool = True
    message: str | None = None
    external_url: str | None = None


class ScrapeSummaryDto(BaseModel):
    sources_checked: int
    fetched_count: int
    inserted_count: int
    notifications_created: int


class ScrapeStatusDto(BaseModel):
    scheduler_enabled: bool
    interval_minutes: int
    scrape_on_startup: bool
    next_run_at: str | None = None
    last_run_at: str | None = None
    last_summary: ScrapeSummaryDto | None = None
    sources: list[str] = Field(default_factory=list)


class NotificationDto(BaseModel):
    id: str
    heading: str
    offer: JobOfferDto
    is_read: bool = False

    @classmethod
    def from_model(cls, model) -> "NotificationDto":
        return cls(
            id=model.id,
            heading=model.heading,
            offer=JobOfferDto.from_model(model.offer),
            is_read=model.is_read,
        )


class NotificationsResponseDto(BaseModel):
    notifications: list[NotificationDto] = Field(default_factory=list)


class NotificationReadResponseDto(BaseModel):
    id: str
    is_read: bool = True


class DailyDigestResultDto(BaseModel):
    pushed: bool
    message: str
    notification: NotificationDto | None = None


class DailyDigestStatusDto(BaseModel):
    scheduler_enabled: bool
    daily_enabled: bool
    interval_hours: int
    next_run_at: str | None = None
    last_run_at: str | None = None
    last_offer_id: str | None = None


class UserProfileDto(BaseModel):
    name: str
    phone: str
    email: str


class UpdateProfileRequestDto(BaseModel):
    name: str
    phone: str
    email: str


class UserSettingsDto(BaseModel):
    job_alerts_enabled: bool = True
    daily_digest_enabled: bool = True
    radius_km: float = 25.0
    currency: str = "PLN"
    city: str = "Krakow"
    latitude: float = 50.0647
    longitude: float = 19.9450
    onboarding_completed: bool = False


class UpdateUserSettingsRequestDto(BaseModel):
    job_alerts_enabled: bool | None = None
    daily_digest_enabled: bool | None = None
    radius_km: float | None = None
    currency: str | None = None
    city: str | None = None
    latitude: float | None = None
    longitude: float | None = None
    onboarding_completed: bool | None = None


class FeedbackRequestDto(BaseModel):
    category: str = "General"
    message: str
    contact_email: str | None = None


class FeedbackResponseDto(BaseModel):
    id: str
    category: str
    message: str
    status: str = "received"


class CvStructuredData(BaseModel):
    full_name: str = "John Workman"
    phone: str = "+1 123 456 789"
    email: str = "jworkman@mail.com"
    headline: str = "Quality Assurance Engineer"
    summary: str = (
        "Quality Assurance Engineer experienced in automated testing, "
        "Kubernetes cluster validation, ELK observability, and Linux CI/CD pipelines."
    )
    skills: list[str] = Field(default_factory=lambda: ["Kubernetes", "ELK", "Linux", "Python", "Pytest"])
    experience: list[str] = Field(
        default_factory=lambda: [
            "IT DEV COMPANY sp. z o.o. - QA Engineer (2022 - Present): Automated E2E microservice tests on Kubernetes.",
            "CloudTest Lab - Junior QA Automation (2020 - 2022): Built API regression suites and ELK alerting."
        ]
    )
    education: str = "B.Sc. in Computer Science"
    languages: list[str] = Field(default_factory=list)
    certifications: list[str] = Field(default_factory=list)
    extra_sections: dict[str, list[str]] = Field(default_factory=dict)


class CvDto(BaseModel):
    id: str
    file_name: str
    updated_label: str = "Updated today"
    page_count: int | None = None
    file_size_bytes: int | None = None

    @classmethod
    def from_model(
        cls,
        model,
        page_count: int | None = None,
        file_size_bytes: int | None = None,
    ) -> "CvDto":
        return cls(
            id=model.id,
            file_name=model.file_name,
            updated_label=model.updated_label,
            page_count=page_count,
            file_size_bytes=file_size_bytes,
        )


class CvsResponseDto(BaseModel):
    cvs: list[CvDto] = Field(default_factory=list)


class CreateCvRequestDto(BaseModel):
    file_name: str


class UpdateCvContentRequestDto(BaseModel):
    file_name: str | None = None
    headline: str | None = None
    summary: str | None = None
    skills: list[str] | None = None
    experience: list[str] | None = None
    education: str | None = None
    languages: list[str] | None = None
    certifications: list[str] | None = None


class DeleteCvResponseDto(BaseModel):
    deleted: bool = True
    id: str


class ApplicationsResponseDto(BaseModel):
    applications: list[JobOfferDto] = Field(default_factory=list)


class ChatMessageDto(BaseModel):
    role: str
    content: str


class AssistantChatRequestDto(BaseModel):
    message: str
    cv_id: str | None = None
    history: list[ChatMessageDto] = Field(default_factory=list)


class AssistantChatResponseDto(BaseModel):
    reply: str
    model: str | None = None
    updated_cv: CvDto | None = None
    suggested_offers: list[JobOfferDto] = Field(default_factory=list)


class AssistantHealthDto(BaseModel):
    reachable: bool
    llm_base_url: str
    chat_model: str | None = None
    embedding_model: str | None = None
    error: str | None = None
