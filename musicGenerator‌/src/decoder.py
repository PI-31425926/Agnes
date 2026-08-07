"""简谱符号解码器：将内部ASCII表示还原为可读简谱文本。

同时提供输入清洗功能，将非标准写法（Unicode下标/上标等）
转换为统一的ASCII符号体系（-1低音, +1高音, 1中音）。
"""

import re


# Unicode 下标数字映射 (₁ ₂ ₃ ₄ ₅ ₆ ₇ ₀)
_SUBSCRIPT_MAP = {
    '₀': '0', '₁': '1', '₂': '2', '₃': '3',
    '₄': '4', '₅': '5', '₆': '6', '₇': '7',
}

# Unicode 上标数字映射 (¹ ² ³)
_SUPERSCRIPT_MAP = {
    '¹': '1', '²': '2', '³': '3',
}


def _clean_subscripts(text: str) -> str:
    """将 Unicode 下标数字转为普通数字。"""
    for sub, norm in _SUBSCRIPT_MAP.items():
        text = text.replace(sub, norm)
    return text


def _clean_superscripts(text: str) -> str:
    """将 Unicode 上标数字转为普通数字。"""
    for sup, norm in _SUPERSCRIPT_MAP.items():
        text = text.replace(sup, norm)
    return text


def clean_input(text: str) -> str:
    """清洗简谱输入，将各种写法统一为 ASCII 符号体系。

    处理规则：
    1. Unicode 下标数字 → 普通数字（低音需结合上下文推断为前缀 -）
    2. Unicode 上标数字 → 普通数字（高音需结合上下文推断为前缀 +）
    3. 移除无关空白和不可见字符
    4. 保留合法符号: 0-7, +/-, |, 空格, 换行
    """
    # 步骤1: 清理 Unicode 特殊数字
    text = _clean_subscripts(text)
    text = _clean_superscripts(text)

    # 步骤2: 移除不可见字符（保留空格、换行、TAB）
    text = re.sub(r'[^\x20\x0A\x0D\x09\-+0-7|]', '', text)

    # 步骤3: 规范化空格
    lines = text.split('\n')
    cleaned_lines = []
    for line in lines:
        parts = line.split()
        if parts:
            cleaned_lines.append(' '.join(parts))

    return '\n'.join(cleaned_lines)


def decode_to_standard(text: str) -> str:
    """将内部 ASCII 表示解码为标准简谱文本格式。

    当前内部表示与标准输出一致，此函数预留扩展空间
    （如将来支持 Unicode 渲染输出）。
    """
    return text


def validate_and_clean(text: str) -> tuple[str, list[str]]:
    """验证并清洗简谱文本，返回 (清洗后文本, 警告列表)。

    Returns:
        (cleaned_text, warnings): 清洗后的文本和发现的警告信息
    """
    warnings = []
    original_lines = text.split('\n')

    cleaned = clean_input(text)
    cleaned_lines = cleaned.split('\n')

    # 检查空行
    for i, line in enumerate(original_lines):
        if not line.strip():
            warnings.append(f"第 {i+1} 行为空，已跳过")

    return cleaned, warnings
