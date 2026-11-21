from datetime import datetime, timezone
from sqlalchemy import Boolean, DateTime, Float, ForeignKey, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database import Base


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class UserModel(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    phone: Mapped[str] = mapped_column(String(64))
    email: Mapped[str] = mapped_column(String(160))
    latitude: Mapped[float] = mapped_column(Float, default=50.0647)
    longitude: Mapped[float] = mapped_column(Float, default=19.9450)


class JobOfferModel(Base):
    __tablename__ = "job_offers"

    id: Mapped[str] = mapped_column(String(128), primary_key=True)
    initial: Mapped[str] = mapped_column(String(4), default="A")
    title: Mapped[str] = mapped_column(String(200))
    salary_range: Mapped[str] = mapped_column(String(80))
    salary_unit: Mapped[str] = mapped_column(String(40), default="PLN/month")
    skills_json: Mapped[str] = mapped_column(Text, default="[]")
    company_name: Mapped[str] = mapped_column(String(200))
    level: Mapped[str] = mapped_column(String(40), default="MID")
    contract_type: Mapped[str] = mapped_column(String(40), default="B2B")
    work_schedule: Mapped[str] = mapped_column(String(40), default="Full-Time")
    accent: Mapped[str] = mapped_column(String(32), default="YELLOW")
    description: Mapped[str] = mapped_column(Text, default="")
    source: Mapped[str] = mapped_column(String(64), default="seed")
    external_url: Mapped[str | None] = mapped_column(String(500), nullable=True, unique=True)
    latitude: Mapped[float] = mapped_column(Float, default=50.0647)
    longitude: Mapped[float] = mapped_column(Float, default=19.9450)
    embedding_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class CvModel(Base):
    __tablename__ = "cvs"

    id: Mapped[str] = mapped_column(String(128), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    file_name: Mapped[str] = mapped_column(String(200))
    updated_label: Mapped[str] = mapped_column(String(80), default="Updated today")
    file_path: Mapped[str] = mapped_column(String(500))
    raw_text: Mapped[str] = mapped_column(Text, default="")
    structured_json: Mapped[str] = mapped_column(Text, default="{}")
    embedding_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class ApplicationModel(Base):
    __tablename__ = "applications"
    __table_args__ = (UniqueConstraint("user_id", "offer_id", name="uq_user_offer_application"),)

    id: Mapped[str] = mapped_column(String(128), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    offer_id: Mapped[str] = mapped_column(ForeignKey("job_offers.id"), index=True)
    cv_id: Mapped[str | None] = mapped_column(ForeignKey("cvs.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    offer: Mapped[JobOfferModel] = relationship(lazy="selectin")


class NotificationModel(Base):
    __tablename__ = "notifications"

    id: Mapped[str] = mapped_column(String(128), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    heading: Mapped[str] = mapped_column(String(200), default="New job offer for you")
    offer_id: Mapped[str] = mapped_column(ForeignKey("job_offers.id"), index=True)
    is_read: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    offer: Mapped[JobOfferModel] = relationship(lazy="selectin")


class UserSettingsModel(Base):
    __tablename__ = "user_settings"

    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), primary_key=True)
    job_alerts_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    daily_digest_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    radius_km: Mapped[float] = mapped_column(Float, default=25.0)
    currency: Mapped[str] = mapped_column(String(16), default="PLN")
    city: Mapped[str] = mapped_column(String(80), default="Krakow")
    onboarding_completed: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class FeedbackModel(Base):
    __tablename__ = "feedback_messages"

    id: Mapped[str] = mapped_column(String(128), primary_key=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    category: Mapped[str] = mapped_column(String(40), default="General")
    message: Mapped[str] = mapped_column(Text)
    contact_email: Mapped[str | None] = mapped_column(String(160), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
