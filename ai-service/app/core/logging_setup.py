"""日志初始化：统一格式与级别。"""
import logging


def setup_logging(level: int = logging.INFO) -> None:
    """配置根日志格式，供各模块直接使用 logging.getLogger 输出。"""
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
