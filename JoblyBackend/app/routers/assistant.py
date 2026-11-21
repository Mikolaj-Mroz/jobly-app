import httpx
from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.models import UserModel
from app.schemas import AssistantChatRequestDto, AssistantChatResponseDto, AssistantHealthDto

router = APIRouter(prefix="/assistant", tags=["assistant"])


@router.get("/health", response_model=AssistantHealthDto)
async def assistant_health(request: Request) -> AssistantHealthDto:
    settings = request.app.state.settings
    llm_client = request.app.state.llm_client
    try:
        chat_model, embed_model = await llm_client.detect_models()
        return AssistantHealthDto(
            reachable=True,
            llm_base_url=settings.llm_base_url,
            chat_model=chat_model,
            embedding_model=embed_model,
        )
    except Exception as exc:
        return AssistantHealthDto(
            reachable=False,
            llm_base_url=settings.llm_base_url,
            error=str(exc),
        )


@router.post("/chat", response_model=AssistantChatResponseDto)
async def assistant_chat(
    body: AssistantChatRequestDto,
    request: Request,
    db: AsyncSession = Depends(get_db),
) -> AssistantChatResponseDto:
    settings = request.app.state.settings
    llm_client = request.app.state.llm_client
    user = (
        await db.execute(select(UserModel).where(UserModel.id == settings.default_user_id))
    ).scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")

    try:
        return await llm_client.chat_with_tools(
            session=db,
            user=user,
            message=body.message,
            cv_id=body.cv_id,
            history=body.history,
        )
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=503,
            detail=f"Could not reach LLM server at {settings.llm_base_url}: {exc}",
        ) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
