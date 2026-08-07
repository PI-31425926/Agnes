"""简谱数据集模块：加载、清洗、token化、构建训练集。

涵盖功能：
- 扫描目录加载简谱文件
- 文件格式校验
- 字符级 token 化和词表构建
- 训练集/验证集划分
- 固定长度序列切片（滑动窗口）
- 数据增强（移调 + 节奏扰动）
- 调性统一（训练时移调到 G 调）
"""

import os
import re
import random
from pathlib import Path
from collections import Counter

import numpy as np

from src.encoder import is_valid_line, encode_text
from src.decoder import clean_input, validate_and_clean
from src import midi_tokenizer

# 默认滑动窗口参数
DEFAULT_WINDOW_SIZE = 256
DEFAULT_OVERLAP_RATIO = 0.5

# 调性映射：半音偏移量（相对于 C 调）
KEY_SEMITONE_MAP = {
    'C': 0,
    'G': 7,   # G 调 = C 调上移 7 个半音
}

# 音阶范围（C 调基准）
MIN_NOTE = 1   # 低音 1
MAX_NOTE = 7   # 高音 7


# ------------------------------------------------------------------ #
#                         文件加载                                     #
# ------------------------------------------------------------------ #

def scan_score_files(directory: str) -> list[str]:
    """扫描目录中所有简谱文件（.txt 和 .jpx）。"""
    dirpath = Path(directory)
    files = []
    for ext in ('*.txt', '*.jpx'):
        files.extend(str(p) for p in dirpath.glob(ext))
    return sorted(files)


def scan_midi_files(directory: str) -> list[str]:
    """扫描目录中所有 MIDI 文件。"""
    dirpath = Path(directory)
    return sorted(str(p) for p in dirpath.glob('*.mid'))


def load_score_file(filepath: str) -> tuple[str, list[str]]:
    """加载单个简谱文件，返回 (清洗后文本, 警告列表)。"""
    with open(filepath, 'r', encoding='utf-8') as f:
        raw = f.read()

    cleaned, warnings = validate_and_clean(raw)
    return cleaned, warnings


def load_midi_file(filepath: str) -> tuple[list[str], list[str]]:
    """加载单个 MIDI 文件，返回 (token 列表, 警告列表)。"""
    return midi_tokenizer.midi_to_tokens(filepath)


def load_directory(directory: str) -> tuple[list[tuple[str, str]], list[str]]:
    """批量加载目录中的所有简谱文件。

    Returns:
        songs: 列表 of (filename, cleaned_text)
        all_warnings: 所有文件的警告汇总
    """
    files = scan_score_files(directory)
    songs = []
    all_warnings = []

    for fp in files:
        fname = os.path.basename(fp)
        text, warnings = load_score_file(fp)
        if not text.strip():
            all_warnings.append(f"[SKIP] {fname}: 文件为空")
            continue
        songs.append((fname, text))
        all_warnings.extend(f"[{fname}] {w}" for w in warnings)

    return songs, all_warnings


def load_midi_directory(directory: str) -> tuple[list[tuple[str, list[str]]], list[str]]:
    """批量加载目录中的所有 MIDI 文件。

    Returns:
        songs: 列表 of (filename, token_list)
        all_warnings: 所有文件的警告汇总
    """
    files = scan_midi_files(directory)
    songs = []
    all_warnings = []

    for fp in files:
        fname = os.path.basename(fp)
        tokens, warnings = load_midi_file(fp)
        if not tokens:
            all_warnings.append(f"[SKIP] {fname}: 无有效音符")
            continue
        songs.append((fname, tokens))
        all_warnings.extend(f"[{fname}] {w}" for w in warnings)

    return songs, all_warnings


# ------------------------------------------------------------------ #
#                        文件格式校验                                   #
# ------------------------------------------------------------------ #

def validate_content(text: str) -> list[str]:
    """校验简谱内容，返回非法行的警告列表。"""
    warnings = []
    for i, line in enumerate(text.split('\n'), 1):
        if line.strip() and not is_valid_line(line):
            warnings.append(f"第 {i} 行包含非法字符: {line[:60]}")
    return warnings


# ------------------------------------------------------------------ #
#                        Token 化和词表                                #
# ------------------------------------------------------------------ #

class Vocabulary:
    """简谱 token 词表：每个音符/符号作为一个 token。

    Token 列表：
      特殊: <PAD> <UNK> <BOS> <EOS>
      节奏标记: _ __ . - (固定保留)
      音符: 1 2 3 4 5 6 7 0
      低音: -1 -2 -3 -4 -5 -6 -7
      高音: +1 +2 +3 +4 +5 +6 +7
      其他: | (小节线)
    """

    SPECIAL_TOKENS = ['<PAD>', '<UNK>', '<BOS>', '<EOS>']
    RHYTHM_TOKENS = ['_', '__', '.', '-']  # 固定保留的节奏标记

    def __init__(self):
        self.idx2token: list[str] = list(self.SPECIAL_TOKENS)
        self.token2idx: dict[str, int] = {t: i for i, t in enumerate(self.idx2token)}

    def build_from_text(self, text: str):
        """从文本中提取所有 token 并构建词表。"""
        tokens = _extract_tokens(text)
        for tok in tokens:
            if tok not in self.token2idx:
                self.idx2token.append(tok)
                self.token2idx[tok] = len(self.idx2token) - 1

    def ensure_rhythm_tokens(self):
        """确保节奏标记 token 始终在词表中。"""
        for tok in self.RHYTHM_TOKENS:
            if tok not in self.token2idx:
                self.idx2token.append(tok)
                self.token2idx[tok] = len(self.idx2token) - 1

    def encode(self, text: str) -> list[int]:
        """将文本编码为 token 索引列表。"""
        tokens = _extract_tokens(text)
        return [self.token2idx.get(t, self.token2idx['<UNK>']) for t in tokens]

    def decode(self, indices: list[int]) -> str:
        """将 token 索引列表解码为文本。"""
        result = []
        for i in indices:
            if 0 <= i < len(self.idx2token):
                result.append(self.idx2token[i])
            else:
                result.append('<UNK>')
        return ' '.join(result)

    @property
    def size(self) -> int:
        return len(self.idx2token)


def _extract_tokens(text: str) -> list[str]:
    """从简谱文本中提取 token 列表。

    规则：
    - 音高 + 修饰符作为一个完整 token（如 5_、5-、5.）
    - 独立 token: <PAD>, <UNK>, <BOS>, <EOS>, |, 0
    - 音符 token: [+-]?[1-7][_-.]+ 或纯数字（无修饰符）
    """
    tokens = []
    parts = text.split()
    for part in parts:
        if not part:
            continue
        # 小节线
        if part == '|':
            tokens.append('|')
            continue
        # 休止符
        if part == '0':
            tokens.append('0')
            continue

        # 解析音高 + 修饰符组合
        # 模式: [+-]?数字 + (后缀 _ 或 . 或 -)
        # 例如: 5_, 5-, 5., +5__, -5_, 5__, 5.5 (错误)

        # 先检查是否有后缀 _ . -
        suffix = ''
        base = part

        # 从右往左提取后缀（最多2个字符）
        if len(part) >= 2 and part[-2:] in ('__', '---', '..'):
            suffix = part[-2:]
            base = part[:-2]
        elif len(part) >= 1 and part[-1] in ('_', '.', '-'):
            suffix = part[-1]
            base = part[:-1]

        # 检查 base 是否是合法音高（数字，或 -数字，或 +数字）
        if base:
            if re.match(r'^[+-]?\d+$', base):
                tokens.append(base + suffix)
                continue

        # 无法识别的 token，跳过或当作普通 token
        # 检查是否是纯音高（无后缀）
        if re.match(r'^[+-]?\d+$', part):
            tokens.append(part)
        else:
            # 尝试特殊模式：如单独的数字后跟修饰符
            # 例如 "5-" 中的 "-" 如果前面是数字
            pass

    return tokens


def _parse_note_token_str(s: str) -> tuple[int, str] | None:
    """解析音符 token 字符串，返回 (数字, 符号) 或 None。"""
    if s in ('0', '|', '-', ''):
        return None
    if s.startswith('+') and s[1:].isdigit() and 1 <= int(s[1:]) <= 7:
        return (int(s[1:]), '+')
    if s.startswith('-') and s[1:].isdigit() and 1 <= int(s[1:]) <= 7:
        return (int(s[1:]), '-')
    if s.isdigit() and 1 <= int(s) <= 7:
        return (int(s), '')
    return None


def _is_note(s: str) -> bool:
    """检查字符串是否为合法音符 token（如 1, -5, +3）。"""
    if not s:
        return False
    if s.startswith('+') and s[1:].isdigit() and 1 <= int(s[1:]) <= 7:
        return True
    if s.startswith('-') and s[1:].isdigit() and 1 <= int(s[1:]) <= 7:
        return True
    if s.isdigit() and 1 <= int(s) <= 7:
        return True
    return False


def text_to_tokens(text: str) -> list[int]:
    """将简谱文本转为 token 索引列表（词表在 build_dataset 中构建）。"""
    # 占位：实际使用时先构建 Vocabulary
    raise NotImplementedError("Use build_dataset to get vocab and tokens together.")


# ------------------------------------------------------------------ #
#                    训练集/验证集划分                                   #
# ------------------------------------------------------------------ #

def split_datasets(
    songs: list[tuple[str, str]],
    val_ratio: float = 0.1,
    seed: int = 42,
) -> tuple[list[str], list[str]]:
    """将歌曲列表划分为训练集和验证集（歌曲级别，90/10）。"""
    shuffled = list(songs)
    random.seed(seed)
    random.shuffle(shuffled)

    split_idx = int(len(shuffled) * (1 - val_ratio))
    train_texts = [t for _, t in shuffled[:split_idx]]
    val_texts = [t for _, t in shuffled[split_idx:]]

    return train_texts, val_texts


# ------------------------------------------------------------------ #
#                    滑动窗口序列切片                                    #
# ------------------------------------------------------------------ #

def sliding_window_slices(
    texts: list[str],
    vocab: Vocabulary,
    window_size: int = DEFAULT_WINDOW_SIZE,
    overlap_ratio: float = DEFAULT_OVERLAP_RATIO,
) -> list[list[int]]:
    """从文本列表中抽取固定长度的 token 切片。

    Args:
        texts: 简谱文本列表
        vocab: 词表对象
        window_size: 每个切片的字符数
        overlap_ratio: 相邻切片的重叠比例（0.5 = 50%）

    Returns:
        token 切片列表，每个元素是长度为 window_size 的 int 列表
    """
    stride = int(window_size * (1 - overlap_ratio))
    if stride < 1:
        stride = 1

    all_slices = []
    for text in texts:
        tokens = vocab.encode(text)
        for i in range(0, max(1, len(tokens) - window_size + 1), stride):
            slice_tokens = tokens[i:i + window_size]
            if len(slice_tokens) == window_size:
                all_slices.append(slice_tokens)

    return all_slices


# ------------------------------------------------------------------ #
#                        数据增强                                       #
# ------------------------------------------------------------------ #

def transpose_sequence(
    tokens: list[int],
    vocab: Vocabulary,
    semitone_shift: int,
) -> list[int]:
    """对 token 序列进行移调（半音平移）。

    仅平移音符 token，其他 token 保持不变。
    """
    result = []
    for idx in tokens:
        token = vocab.idx2token[idx]
        if idx == 0:  # <PAD>
            result.append(idx)
            continue

        parsed = _parse_note_token_str(token)
        if parsed is not None:
            num, sign = parsed
            new_num = num + semitone_shift
            # 超出音域则截断
            new_num = max(MIN_NOTE, min(MAX_NOTE, new_num))
            new_tok = f"{sign}{new_num}" if sign else str(new_num)
            new_idx = vocab.token2idx.get(new_tok, vocab.token2idx['<UNK>'])
            result.append(new_idx)
        else:
            result.append(idx)

    return result


def augment_rhythm(tokens: list[int], vocab: Vocabulary, prob: float = 0.1) -> list[int]:
    """节奏扰动增强：随机移除或复制延长标记 '-'。"""
    result = []
    i = 0
    while i < len(tokens):
        token = vocab.idx2token[tokens[i]]
        if token == '-' and random.random() < prob:
            if random.random() < 0.5:
                # 移除延长标记，也移除前面的音符（只保留数字部分）
                # 实际上延长标记紧跟音符，这里简单跳过
                pass
            else:
                # 复制延长标记
                result.append(tokens[i])
                result.append(tokens[i])
                i += 1
                continue
        result.append(tokens[i])
        i += 1
    return result


# ------------------------------------------------------------------ #
#                        调性处理                                       #
# ------------------------------------------------------------------ #

def transpose_to_key(
    text: str,
    from_key: str,
    to_key: str,
) -> str:
    """将简谱文本从一个调性移调到另一个调性。

    Args:
        text: 简谱文本
        from_key: 源调性（如 'G'）
        to_key: 目标调性（如 'C'）

    Returns:
        移调后的简谱文本
    """
    shift = KEY_SEMITONE_MAP.get(to_key, 0) - KEY_SEMITONE_MAP.get(from_key, 0)
    if shift == 0:
        return text

    tokens = _extract_tokens(text)
    result_tokens = []
    for tok in tokens:
        parsed = _parse_note_token_str(tok)
        if parsed is not None:
            num, sign = parsed
            new_num = num + shift
            new_num = max(MIN_NOTE, min(MAX_NOTE, new_num))
            new_tok = f"{sign}{new_num}" if sign else str(new_num)
            result_tokens.append(new_tok)
        else:
            result_tokens.append(tok)

    return ' '.join(result_tokens)


# ------------------------------------------------------------------ #
#                        主入口                                        #
# ------------------------------------------------------------------ #

def build_dataset(
    data_dir: str,
    window_size: int = DEFAULT_WINDOW_SIZE,
    overlap_ratio: float = DEFAULT_OVERLAP_RATIO,
    val_ratio: float = 0.1,
    mid_dir: str | None = None,
) -> tuple[Vocabulary, list[list[int]], list[list[int]], list[str]]:
    """构建完整的训练数据集。

    Args:
        data_dir: 训练数据目录（.txt/.jpx 文件）
        window_size: 滑动窗口大小
        overlap_ratio: 窗口重叠比例
        val_ratio: 验证集比例
        mid_dir: MIDI 数据目录（可选，与 data_dir 合并）

    Returns:
        (vocab, train_slices, val_slices, warnings)
    """
    all_warnings = []

    # 1. 加载简谱文件
    songs, load_warnings = load_directory(data_dir)
    all_warnings.extend(load_warnings)

    # 2. 加载 MIDI 文件（如果提供）
    midi_songs = []
    if mid_dir and os.path.isdir(mid_dir):
        midi_songs, midi_warnings = load_midi_directory(mid_dir)
        all_warnings.extend(midi_warnings)
        print(f"  Loaded {len(midi_songs)} MIDI files from {mid_dir}")

    if not songs and not midi_songs:
        raise ValueError(f"在目录 {data_dir} 和 {mid_dir or 'none'} 中未找到任何数据文件")

    # 3. 构建统一词表
    vocab = Vocabulary()
    vocab.SPECIAL_TOKENS = list(midi_tokenizer.SPECIAL_TOKENS)
    vocab.idx2token = list(midi_tokenizer.SPECIAL_TOKENS)
    vocab.token2idx = {t: i for i, t in enumerate(midi_tokenizer.SPECIAL_TOKENS)}

    # 3a. 从简谱文本构建词表
    if songs:
        all_text = '\n'.join(text for _, text in songs)
        vocab.build_from_text(all_text)
        vocab.ensure_rhythm_tokens()

    # 3b. 从 MIDI tokens 构建词表
    if midi_songs:
        all_midi_tokens = []
        for _, tokens in midi_songs:
            all_midi_tokens.extend(tokens)
        for tok in all_midi_tokens:
            if tok not in vocab.token2idx:
                vocab.idx2token.append(tok)
                vocab.token2idx[tok] = len(vocab.idx2token) - 1

    # 4. 训练/验证划分
    # 简谱歌曲
    if songs:
        train_texts, val_texts = split_datasets(songs, val_ratio)
    else:
        train_texts, val_texts = [], []

    # MIDI 歌曲（不按风格划分，直接随机分割）
    if midi_songs:
        random.seed(42)
        shuffled = list(midi_songs)
        random.shuffle(shuffled)
        split_idx = int(len(shuffled) * (1 - val_ratio))
        train_midi = shuffled[:split_idx]
        val_midi = shuffled[split_idx:]
    else:
        train_midi, val_midi = [], []

    # 5. 滑动窗口切片
    # 简谱数据
    train_slices = []
    val_slices = []
    if train_texts:
        train_slices = sliding_window_slices(train_texts, vocab, window_size, overlap_ratio)
    if val_texts:
        val_slices = sliding_window_slices(val_texts, vocab, window_size, overlap_ratio)

    # MIDI 数据
    if train_midi:
        for _, tokens in train_midi:
            idxs = [vocab.token2idx.get(t, vocab.token2idx['<UNK>']) for t in tokens]
            stride = int(window_size * (1 - overlap_ratio))
            if stride < 1:
                stride = 1
            for i in range(0, max(1, len(idxs) - window_size + 1), stride):
                slice_tokens = idxs[i:i + window_size]
                if len(slice_tokens) == window_size:
                    train_slices.append(slice_tokens)
    if val_midi:
        for _, tokens in val_midi:
            idxs = [vocab.token2idx.get(t, vocab.token2idx['<UNK>']) for t in tokens]
            stride = int(window_size * (1 - overlap_ratio))
            if stride < 1:
                stride = 1
            for i in range(0, max(1, len(idxs) - window_size + 1), stride):
                slice_tokens = idxs[i:i + window_size]
                if len(slice_tokens) == window_size:
                    val_slices.append(slice_tokens)

    all_warnings.append(
        f"数据集构建完成: {len(train_slices)} 训练切片, {len(val_slices)} 验证切片, 词表大小 {vocab.size}"
    )

    return vocab, train_slices, val_slices, all_warnings
