"""简谱符号编码器：将高低音/中音/休止符映射为内部ASCII表示。

符号体系：
  低音: -1 ~ -7
  中音: 1 ~ 7
  高音: +1 ~ +7
  休止: 0
  延长: n- (n为1-7)
  分隔: | (小节线)
"""

import re


# 合法简谱符号的正则
_NOTE_RE = re.compile(r'^[+-]?\d+$')
_EXTEND_RE = re.compile(r'^[+-]?\d+-$')
_VALID_CHARS = set('01234567+-| ')


def is_valid_char(ch: str) -> bool:
    """检查单个字符是否为合法简谱符号。"""
    return ch in _VALID_CHARS


def is_valid_line(line: str) -> bool:
    """检查一行简谱文本是否全部由合法符号组成。"""
    return all(is_valid_char(ch) for ch in line)


def encode_symbol(sym: str) -> str:
    """将单个符号编码为内部表示。

    输入和输出一致（内部已用ASCII），但提供统一入口以便扩展。
    例如: '-5' -> '-5', '+3' -> '+3', '0' -> '0'
    """
    sym = sym.strip()
    if not sym or sym == ' ':
        return ''
    return sym


def encode_line(line: str) -> str:
    """编码一行简谱文本，移除多余空格并保持符号不变。"""
    parts = line.split()
    return ' '.join(p for p in parts if p)


def encode_text(text: str) -> str:
    """编码整个简谱文本，返回标准化的内部表示。"""
    lines = text.strip().split('\n')
    encoded = []
    for line in lines:
        cleaned = encode_line(line)
        if cleaned:
            encoded.append(cleaned)
    return '\n'.join(encoded)


def decode_symbol(sym: str) -> str:
    """解码内部表示为可读简谱（与encode互为逆操作）。"""
    return encode_symbol(sym)


def decode_text(text: str) -> str:
    """解码整个简谱文本。"""
    return encode_text(text)
