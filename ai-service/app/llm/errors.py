"""模型调用错误定义。"""


class LLMError(Exception):
    """模型调用错误，携带可返回给调用方的错误码与提示。"""

    def __init__(self, code: str, message: str):
        self.code = code
        self.message = message
        super().__init__(message)
