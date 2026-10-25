"""会话存储：Redis 优先，Redis 不可用时自动降级为进程内存存储。

Key 命名规则（与 Java 侧统一）：
    {prefix}:session:index:{user_id}   -> ZSET  会话列表（member=sid, score=更新时间戳）
    {prefix}:session:data:{sid}        -> HASH  会话元数据（user_id/title/created_at）
    {prefix}:session:msgs:{sid}        -> LIST  消息列表（JSON 数组）
"""
import json
import logging
import time
import uuid
from typing import Dict, List, Optional

from ..core.config import settings

logger = logging.getLogger("ai-service.sessions")

try:
    import redis as redis_lib
except ImportError:
    redis_lib = None


class SessionStore:
    """会话存储，Redis 不可用时降级为内存字典（仅保证功能可用，重启丢失）。"""

    def __init__(self):
        """初始化存储，尝试连接 Redis，失败则降级为内存字典。"""
        self._memory: Dict[str, dict] = {}
        self._redis = None
        if redis_lib is not None:
            for pwd in (settings.redis_password, None):
                if pwd is None and settings.redis_password:
                    continue
                try:
                    client = redis_lib.Redis(
                        host=settings.redis_host,
                        port=settings.redis_port,
                        db=settings.redis_db,
                        password=pwd,
                        socket_timeout=3,
                        socket_connect_timeout=3,
                        decode_responses=True,
                    )
                    client.ping()
                    self._redis = client
                    logger.info("Redis 连接成功：%s:%s db=%s (password=%s)",
                                settings.redis_host, settings.redis_port, settings.redis_db,
                                "是" if pwd else "无")
                    break
                except Exception as e:
                    logger.warning("Redis 连接失败(password=%s)：%s", "已填" if pwd else "无", e)
        if self._redis is None:
            logger.warning("Redis 不可用，会话降级为进程内存存储（服务重启后会话丢失）")

    @property
    def redis_available(self) -> bool:
        """Redis 是否可用。"""
        return self._redis is not None

    def _k(self, *parts: str) -> str:
        """拼装带统一前缀的 Redis key。"""
        return ":".join([settings.redis_key_prefix, *parts])

    # ---------- 元数据 ----------

    def create(self, user_id: int, title: str = "新会话") -> dict:
        """创建新会话并返回会话信息。"""
        sid = uuid.uuid4().hex
        now = time.time()
        meta = {"user_id": str(user_id), "title": title, "created_at": str(now), "updated_at": str(now)}
        if self._redis is not None:
            p = self._redis.pipeline()
            p.hset(self._k("session", "data", sid), mapping=meta)
            p.expire(self._k("session", "data", sid), settings.session_ttl_seconds)
            p.zadd(self._k("session", "index", str(user_id)), {sid: now})
            p.expire(self._k("session", "index", str(user_id)), settings.session_ttl_seconds)
            p.execute()
        else:
            self._memory[sid] = {"meta": meta, "messages": []}
        return {"session_id": sid, "title": title, "created_at": now, "messages": []}

    def _load(self, sid: str) -> Optional[dict]:
        """从 Redis 或内存加载会话原始数据。"""
        if self._redis is not None:
            meta = self._redis.hgetall(self._k("session", "data", sid))
            if not meta:
                return None
            raw = self._redis.lrange(self._k("session", "msgs", sid), 0, -1)
            msgs = [json.loads(x) for x in raw if x]
            return {"meta": meta, "messages": msgs}
        return self._memory.get(sid)

    def get(self, sid: str, user_id: int) -> Optional[dict]:
        """按会话 ID 与用户 ID 查询会话（校验归属）。"""
        data = self._load(sid)
        if not data or str(data["meta"].get("user_id")) != str(user_id):
            return None
        meta = data["meta"]
        return {
            "session_id": sid,
            "title": meta.get("title", "新会话"),
            "created_at": float(meta.get("created_at", 0)),
            "messages": data["messages"],
        }

    def list(self, user_id: int, limit: int = 50) -> List[dict]:
        """查询某用户的会话列表（带预览）。"""
        items: List[dict] = []
        if self._redis is not None:
            sids = self._redis.zrevrange(self._k("session", "index", str(user_id)), 0, limit - 1)
            for sid in sids:
                meta = self._redis.hgetall(self._k("session", "data", sid))
                if not meta:
                    continue
                first = self._redis.lindex(self._k("session", "msgs", sid), 0)
                preview = ""
                if first:
                    m = json.loads(first)
                    preview = (m.get("content") or "")[:40]
                items.append({
                    "session_id": sid,
                    "title": meta.get("title", "新会话"),
                    "preview": preview,
                    "created_at": float(meta.get("created_at", 0)),
                    "updated_at": float(meta.get("updated_at", 0)),
                })
        else:
            rows = [(sid, d) for sid, d in self._memory.items()
                    if str(d["meta"].get("user_id")) == str(user_id)]
            rows.sort(key=lambda r: float(r[1]["meta"].get("updated_at", 0)), reverse=True)
            for sid, d in rows[:limit]:
                preview = (d["messages"][0].get("content") or "")[:40] if d["messages"] else ""
                items.append({
                    "session_id": sid,
                    "title": d["meta"].get("title", "新会话"),
                    "preview": preview,
                    "created_at": float(d["meta"].get("created_at", 0)),
                    "updated_at": float(d["meta"].get("updated_at", 0)),
                })
        return items

    def delete(self, sid: str, user_id: int) -> bool:
        """删除会话（校验归属）。"""
        data = self._load(sid)
        if not data or str(data["meta"].get("user_id")) != str(user_id):
            return False
        if self._redis is not None:
            p = self._redis.pipeline()
            p.delete(self._k("session", "data", sid), self._k("session", "msgs", sid))
            p.zrem(self._k("session", "index", str(user_id)), sid)
            p.execute()
        else:
            self._memory.pop(sid, None)
        return True

    # ---------- 消息 ----------

    def append_message(self, sid: str, user_id: int, role: str, content: str) -> bool:
        """向会话追加一条消息（截断保留最近 N 条）。"""
        data = self._load(sid)
        if not data or str(data["meta"].get("user_id")) != str(user_id):
            return False
        msg = {"role": role, "content": content, "ts": time.time()}
        now = time.time()
        if self._redis is not None:
            key = self._k("session", "msgs", sid)
            p = self._redis.pipeline()
            p.rpush(key, json.dumps(msg, ensure_ascii=False))
            p.ltrim(key, -settings.session_max_messages, -1)
            p.expire(key, settings.session_ttl_seconds)
            p.hset(self._k("session", "data", sid), "updated_at", str(now))
            p.expire(self._k("session", "data", sid), settings.session_ttl_seconds)
            p.zadd(self._k("session", "index", str(user_id)), {sid: now})
            p.execute()
        else:
            data["messages"].append(msg)
            data["messages"] = data["messages"][-settings.session_max_messages:]
            data["meta"]["updated_at"] = str(now)
            if data["meta"].get("title") == "新会话" and role == "user":
                data["meta"]["title"] = content[:20]
        return True

    def rename_if_first(self, sid: str, user_id: int) -> None:
        """首条用户消息后，用问题前缀做会话标题（Redis 场景）。"""
        if self._redis is None:
            return  # 内存模式已在 append 时处理
        data = self._load(sid)
        if not data:
            return
        if data["meta"].get("title") == "新会话" and data["messages"]:
            first_user = next((m for m in data["messages"] if m["role"] == "user"), None)
            if first_user:
                self._redis.hset(self._k("session", "data", sid),
                                 "title", (first_user["content"] or "新会话")[:20])


store = SessionStore()
