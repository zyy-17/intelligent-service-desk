"""工单辅助路由：摘要、分类推荐、回复草稿、处置建议、知识检索。"""
from fastapi import APIRouter

from ..schemas import (
    ClassifyRequest,
    ClassifyResponse,
    DraftRequest,
    DraftResponse,
    SearchRequest,
    SearchResponse,
    SuggestStepsRequest,
    SuggestStepsResponse,
    SummaryRequest,
    SummaryResponse,
)
from ..services import ticket_service

router = APIRouter(prefix="/api/ai", tags=["工单辅助"])


@router.post("/summarize", response_model=SummaryResponse)
def summarize(req: SummaryRequest):
    """工单摘要：用一句话概括核心问题。"""
    return ticket_service.summarize(req)


@router.post("/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest):
    """分类推荐：从候选分类中选出最匹配的一个。"""
    return ticket_service.classify(req)


@router.post("/draft", response_model=DraftResponse)
def draft(req: DraftRequest):
    """回复草稿：根据工单与知识库起草客服回复。"""
    return ticket_service.draft(req)


@router.post("/suggest-steps", response_model=SuggestStepsResponse)
def suggest_steps(req: SuggestStepsRequest):
    """处置建议：根据工单内容与知识库给出 2-3 步可执行建议。"""
    return ticket_service.suggest_steps(req)


@router.post("/search", response_model=SearchResponse)
def search(req: SearchRequest):
    """知识检索（纯本地，不依赖模型）。"""
    return ticket_service.search(req)
