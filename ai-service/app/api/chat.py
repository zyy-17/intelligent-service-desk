"""智能问答路由：一次性回答与 SSE 流式回答。"""
from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from ..schemas import ChatRequest, ChatResponse
from ..services import chat_service

router = APIRouter(prefix="/api/ai", tags=["智能问答"])


@router.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    """智能问答：优先云端模型（RAG），失败降级为知识检索，支持会话记录。"""
    return chat_service.answer(req)


@router.post("/chat/stream")
def chat_stream(req: ChatRequest):
    """流式智能问答：以 SSE 逐段返回模型回答，支持会话记录。"""
    return StreamingResponse(chat_service.answer_stream(req), media_type="text/event-stream")
