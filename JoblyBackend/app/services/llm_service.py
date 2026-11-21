import json
from pathlib import Path
import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.config import Settings
from app.models import ApplicationModel, CvModel, JobOfferModel, UserModel, utc_now
from app.schemas import (
    AssistantChatResponseDto,
    ChatMessageDto,
    CvDto,
    CvStructuredData,
    JobOfferDto,
)
from app.services import pdf_service, recommendation_service

_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "update_cv",
            "description": (
                "Edit the user's CV structured content and regenerate the real downloadable PDF file on disk. "
                "Call this immediately whenever the user asks to edit, update, tailor, add skills, change wording, or improve their CV."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "full_name": {"type": "string", "description": "Updated candidate full name"},
                    "phone": {"type": "string", "description": "Updated phone number"},
                    "email": {"type": "string", "description": "Updated email address"},
                    "headline": {"type": "string", "description": "Updated professional headline / job title"},
                    "summary": {"type": "string", "description": "Updated CV summary paragraph"},
                    "skills": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Full replacement list of technical skills (use add_skills/remove_skills for incremental changes)",
                    },
                    "add_skills": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Skills to append to the existing CV without removing current skills",
                    },
                    "remove_skills": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Skills to remove from the existing CV",
                    },
                    "experience_bullets": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Full replacement list of experience bullet points",
                    },
                    "add_experience_bullet": {
                        "type": "string",
                        "description": "Single new experience entry or achievement bullet to append",
                    },
                    "education": {"type": "string", "description": "Updated education section text"},
                    "languages": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Updated list of spoken languages and proficiency",
                    },
                    "certifications": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Updated list of certifications",
                    },
                    "file_name": {
                        "type": "string",
                        "description": "Optional new PDF filename, e.g. My-tailored-cv.pdf",
                    },
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_job_offers",
            "description": "Search and rank available job offers matching the user's query and CV.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Optional keyword, role, or skill filter (empty string for top matches)",
                    }
                },
            },
        },
    },
]


class OpenAiCompatibleClient:
    def __init__(self, settings: Settings, http_client: httpx.AsyncClient | None = None) -> None:
        self.settings = settings
        self.base_url = settings.llm_base_url.rstrip("/")
        self._external_client = http_client

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self.settings.llm_api_key}",
            "Content-Type": "application/json",
        }

    async def detect_models(self) -> tuple[str | None, str | None]:
        client = self._external_client or httpx.AsyncClient(timeout=10.0)
        owns = self._external_client is None
        try:
            resp = await client.get(f"{self.base_url}/models", headers=self._headers())
            resp.raise_for_status()
            items = resp.json().get("data", [])
            ids = [str(item.get("id")) for item in items if item.get("id")]
            chat_model = next((m for m in ids if "embed" not in m.lower()), ids[0] if ids else None)
            embed_model = next((m for m in ids if "embed" in m.lower()), None)
            return chat_model, embed_model
        finally:
            if owns:
                await client.aclose()

    async def embed_text(self, text: str) -> list[float] | None:
        try:
            _, embed_model = await self.detect_models()
            if not embed_model:
                return None
            client = self._external_client or httpx.AsyncClient(timeout=20.0)
            owns = self._external_client is None
            try:
                resp = await client.post(
                    f"{self.base_url}/embeddings",
                    headers=self._headers(),
                    json={"model": embed_model, "input": text},
                )
                resp.raise_for_status()
                data = resp.json().get("data", [])
                if data and isinstance(data[0].get("embedding"), list):
                    return [float(x) for x in data[0]["embedding"]]
            finally:
                if owns:
                    await client.aclose()
        except Exception:
            return None
        return None

    async def chat_with_tools(
        self,
        session: AsyncSession,
        user: UserModel,
        message: str,
        cv_id: str | None = None,
        history: list[ChatMessageDto] | None = None,
    ) -> AssistantChatResponseDto:
        cv = await self._resolve_cv(session, user.id, cv_id)
        chat_model, _ = await self.detect_models()
        if not chat_model:
            raise RuntimeError("No chat model loaded at LLM server")

        system_prompt = self._build_system_prompt(user, cv)
        api_messages: list[dict] = [{"role": "system", "content": system_prompt}]
        for item in history or []:
            api_messages.append({"role": item.role, "content": item.content})
        api_messages.append({"role": "user", "content": message})

        updated_cv_dto: CvDto | None = None
        suggested_offers: list[JobOfferDto] = []

        client = self._external_client or httpx.AsyncClient(timeout=self.settings.llm_timeout_seconds)
        owns = self._external_client is None
        try:
            # Allow up to 3 tool-call rounds before returning final reply
            for _ in range(3):
                resp = await client.post(
                    f"{self.base_url}/chat/completions",
                    headers=self._headers(),
                    json={
                        "model": chat_model,
                        "messages": api_messages,
                        "tools": _TOOLS,
                        "temperature": 0.6,
                        "stream": False,
                    },
                )
                resp.raise_for_status()
                payload = resp.json()
                choice = (payload.get("choices") or [{}])[0]
                msg_obj = choice.get("message") or {}
                tool_calls = msg_obj.get("tool_calls") or []

                if not tool_calls:
                    reply_text = (msg_obj.get("content") or "").strip()
                    if not reply_text:
                        reply_text = "Done."
                    return AssistantChatResponseDto(
                        reply=reply_text,
                        model=chat_model,
                        updated_cv=updated_cv_dto,
                        suggested_offers=suggested_offers,
                    )

                api_messages.append(msg_obj)
                for call in tool_calls:
                    fn = call.get("function") or {}
                    name = fn.get("name", "")
                    raw_args = fn.get("arguments") or "{}"
                    try:
                        args = json.loads(raw_args) if isinstance(raw_args, str) else dict(raw_args)
                    except json.JSONDecodeError:
                        args = {}

                    if name == "update_cv":
                        tool_result, updated_cv_dto, cv = await self._execute_update_cv(
                            session=session,
                            user=user,
                            existing_cv=cv,
                            args=args,
                        )
                    elif name == "search_job_offers":
                        tool_result, suggested_offers = await self._execute_search_offers(
                            session=session,
                            user=user,
                            cv=cv,
                            query=str(args.get("query") or ""),
                        )
                    else:
                        tool_result = json.dumps({"error": f"Unknown tool: {name}"})

                    api_messages.append(
                        {
                            "role": "tool",
                            "tool_call_id": call.get("id", "call_1"),
                            "content": tool_result,
                        }
                    )

            return AssistantChatResponseDto(
                reply="Completed requested actions.",
                model=chat_model,
                updated_cv=updated_cv_dto,
                suggested_offers=suggested_offers,
            )
        finally:
            if owns:
                await client.aclose()

    async def _resolve_cv(
        self, session: AsyncSession, user_id: str, cv_id: str | None
    ) -> CvModel | None:
        if cv_id:
            row = (
                await session.execute(
                    select(CvModel).where(CvModel.id == cv_id, CvModel.user_id == user_id)
                )
            ).scalar_one_or_none()
            if row:
                return row
        return (
            await session.execute(
                select(CvModel).where(CvModel.user_id == user_id).order_by(CvModel.updated_at.desc())
            )
        ).scalars().first()

    def _build_system_prompt(self, user: UserModel, cv: CvModel | None) -> str:
        if cv:
            cv_name = cv.file_name
            structured_block = (cv.structured_json or "{}")[:2500]
            raw_block = (cv.raw_text or "")[:4000]
            cv_context = (
                f"- Active CV ({cv_name}):\n"
                f"  Structured fields: {structured_block}\n"
                f"  Extracted PDF text:\n{raw_block}"
            )
        else:
            cv_context = "- Active CV: None uploaded yet."

        return (
            "You are Jobly Assistant, an AI career coach and CV editor built into the Jobly mobile job-search app.\n\n"
            "## User Context\n"
            f"- Name: {user.name}\n"
            f"- Email: {user.email}\n"
            f"{cv_context}\n\n"
            "## Your Capabilities & Tools\n"
            "You have access to two tools:\n"
            "1. `update_cv(full_name, phone, email, headline, summary, skills, add_skills, remove_skills, experience_bullets, add_experience_bullet, education, languages, certifications, file_name)` - Directly edits the user's active CV data and regenerates their real downloadable PDF file on disk.\n"
            "2. `search_job_offers(query)` - Searches and ranks real job offers from Jobly's database based on the query, skills, geo proximity, and the user's CV.\n\n"
            "## Tool Usage Rules\n"
            "- Job search: Whenever the user asks to find jobs, see matching roles, check salaries, or explore openings (including 'Find me a job'), ALWAYS call `search_job_offers`. Never invent or guess job listings. Present the top 2-4 matches concisely with title, company, salary, match score/reasons, key skills, and why each fits their CV.\n"
            "- CV editing:\n"
            "  - Whenever the user gives any concrete CV edit request (e.g., 'add Docker to my skills', 'remove Excel', 'rewrite my summary', 'add an experience bullet', 'tailor my CV for Backend Engineer', 'translate my summary to English/Polish'), ALWAYS call `update_cv` immediately so the PDF on disk is really updated.\n"
            "  - Prefer incremental fields (`add_skills`, `remove_skills`, `add_experience_bullet`) when only adding/removing items so existing CV sections remain intact.\n"
            "  - Only ask clarifying questions without calling `update_cv` if the user wrote an ambiguous broad prompt like 'Edit my CV' with zero direction.\n"
            "  - After `update_cv` succeeds, clearly summarize the changed fields and confirm their PDF has been regenerated and is ready to preview/download.\n\n"
            "## Truthfulness & Safety\n"
            "- Never fabricate employers, job titles, dates, degrees, certifications, or metrics the user hasn't mentioned. If quantifying impact would help, suggest a placeholder or ask the user for the real number.\n"
            "- Stay focused on career topics: job search, CV optimization, cover letters, salary expectations, and interview preparation. Politely redirect unrelated requests.\n\n"
            "## Response Style (Mobile Chat)\n"
            "- Keep answers concise, practical, and easy to scan on a phone screen.\n"
            "- Use short paragraphs and compact bullet points (avoid long essays or unnecessary filler).\n"
            "- Match the user's language (reply in Polish if they write in Polish, English if they write in English)."
        )

    def _load_structured_cv(
        self, user: UserModel, existing_cv: CvModel | None
    ) -> CvStructuredData:
        if existing_cv and existing_cv.structured_json and existing_cv.structured_json.strip() not in {"", "{}"}:
            try:
                return CvStructuredData.model_validate_json(existing_cv.structured_json)
            except Exception:
                pass
        if existing_cv and (existing_cv.raw_text or "").strip():
            return pdf_service.parse_cv_text_to_structured(
                raw_text=existing_cv.raw_text,
                fallback_name=user.name,
                fallback_phone=user.phone,
                fallback_email=user.email,
            )
        return CvStructuredData(
            full_name=user.name,
            phone=user.phone,
            email=user.email,
            headline="",
            summary="",
            skills=[],
            experience=[],
            education="",
        )

    async def _execute_update_cv(
        self,
        session: AsyncSession,
        user: UserModel,
        existing_cv: CvModel | None,
        args: dict,
    ) -> tuple[str, CvDto, CvModel]:
        structured = self._load_structured_cv(user, existing_cv)
        changed_fields: list[str] = []

        for field_name in ("full_name", "phone", "email", "headline", "summary", "education"):
            raw_val = args.get(field_name)
            if isinstance(raw_val, str) and raw_val.strip():
                setattr(structured, field_name, raw_val.strip())
                changed_fields.append(field_name)

        if isinstance(args.get("skills"), list):
            structured.skills = [str(s).strip() for s in args["skills"] if str(s).strip()]
            changed_fields.append("skills")

        if isinstance(args.get("add_skills"), list):
            existing_lower = {s.lower() for s in structured.skills}
            added_any = False
            for raw_skill in args["add_skills"]:
                cleaned = str(raw_skill).strip()
                if cleaned and cleaned.lower() not in existing_lower:
                    structured.skills.append(cleaned)
                    existing_lower.add(cleaned.lower())
                    added_any = True
            if added_any and "skills" not in changed_fields:
                changed_fields.append("skills")

        if isinstance(args.get("remove_skills"), list):
            to_remove = {str(s).strip().lower() for s in args["remove_skills"] if str(s).strip()}
            if to_remove:
                before_len = len(structured.skills)
                structured.skills = [s for s in structured.skills if s.lower() not in to_remove]
                if len(structured.skills) != before_len and "skills" not in changed_fields:
                    changed_fields.append("skills")

        if isinstance(args.get("experience_bullets"), list):
            structured.experience = [str(e).strip() for e in args["experience_bullets"] if str(e).strip()]
            changed_fields.append("experience")

        extra_bullet = args.get("add_experience_bullet")
        if isinstance(extra_bullet, str) and extra_bullet.strip():
            structured.experience.append(extra_bullet.strip())
            if "experience" not in changed_fields:
                changed_fields.append("experience")

        if isinstance(args.get("languages"), list):
            structured.languages = [str(x).strip() for x in args["languages"] if str(x).strip()]
            changed_fields.append("languages")

        if isinstance(args.get("certifications"), list):
            structured.certifications = [str(x).strip() for x in args["certifications"] if str(x).strip()]
            changed_fields.append("certifications")

        requested_name = args.get("file_name") or (existing_cv.file_name if existing_cv else "My-new-cv.pdf")
        safe_name = pdf_service.sanitize_filename(str(requested_name))
        plain_text = pdf_service.build_plain_text(structured)
        embedding = await self.embed_text(plain_text)

        if existing_cv:
            old_path = Path(existing_cv.file_path)
            renamed = bool(args.get("file_name")) and safe_name != existing_cv.file_name
            target_path = (
                self.settings.cvs_dir / f"{existing_cv.id}_{safe_name}"
                if renamed
                else old_path
            )
            pdf_service.render_cv_pdf(structured, target_path)
            if renamed and old_path != target_path:
                old_path.unlink(missing_ok=True)
                changed_fields.append("file_name")
            existing_cv.file_name = safe_name
            existing_cv.file_path = str(target_path)
            existing_cv.updated_label = "Edited by AI"
            existing_cv.raw_text = plain_text
            existing_cv.structured_json = structured.model_dump_json()
            if embedding is not None:
                existing_cv.embedding_json = json.dumps(embedding)
            existing_cv.updated_at = utc_now()
            saved = existing_cv
        else:
            cv_id = f"cv_{int(utc_now().timestamp() * 1000)}"
            target_path = self.settings.cvs_dir / f"{cv_id}_{safe_name}"
            pdf_service.render_cv_pdf(structured, target_path)
            saved = CvModel(
                id=cv_id,
                user_id=user.id,
                file_name=safe_name,
                updated_label="Edited by AI",
                file_path=str(target_path),
                raw_text=plain_text,
                structured_json=structured.model_dump_json(),
                embedding_json=json.dumps(embedding) if embedding is not None else None,
            )
            session.add(saved)

        edited_chunks = "\n".join(
            str(args.get(k))
            for k in ("headline", "summary", "education", "add_experience_bullet")
            if isinstance(args.get(k), str) and str(args.get(k)).strip()
        )
        detected_loc = await pdf_service.sync_user_location_from_cv_text(
            session, user, plain_text, preferred_text=edited_chunks or None
        )
        await session.commit()
        await session.refresh(saved)
        pages, size_bytes = pdf_service.inspect_pdf_file(Path(saved.file_path))
        dto = CvDto.from_model(saved, page_count=pages, file_size_bytes=size_bytes)
        return (
            json.dumps(
                {
                    "status": "updated",
                    "changed_fields": changed_fields,
                    "pdf_verified_pages": pages,
                    "file_size_bytes": size_bytes,
                    "detected_location": detected_loc[0] if detected_loc else None,
                    "cv": dto.model_dump(),
                }
            ),
            dto,
            saved,
        )

    async def _execute_search_offers(
        self,
        session: AsyncSession,
        user: UserModel,
        cv: CvModel | None,
        query: str,
    ) -> tuple[str, list[JobOfferDto]]:
        all_offers = list((await session.execute(select(JobOfferModel))).scalars().all())
        app_rows = (
            await session.execute(
                select(ApplicationModel).where(ApplicationModel.user_id == user.id)
            )
        ).scalars().all()
        applied_offers = [row.offer for row in app_rows if row.offer is not None]
        scored = recommendation_service.score_best_for_you(
            offers=all_offers,
            cv=cv,
            query=query,
            user_lat=user.latitude,
            user_lon=user.longitude,
            applied_offers=applied_offers,
        )[:5]
        dtos = [
            JobOfferDto.from_model(
                s.offer,
                match_score=s.match_score,
                match_reasons=s.match_reasons,
            )
            for s in scored
        ]
        return json.dumps({"offers": [d.model_dump() for d in dtos]}), dtos
