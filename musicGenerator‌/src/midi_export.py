"""简谱到 MIDI 的转换模块。

将简谱文本（1-7, -1~-7, +1~+7, 0, 延长标记）转换为标准 MIDI 文件。
使用 G 调音阶，支持多种 GM 乐器音色。
"""

import re
from mido import MidiFile, MidiTrack, MetaMessage, Message


# G 调音阶 MIDI 音符映射（中音）
# G调: 1=G(55), 2=A(57), 3=B(59), 4=C(60), 5=D(62), 6=E(64), 7=F#(66)
KEY_G_MIDDLE = {
    '1': 55, '2': 57, '3': 59, '4': 60,
    '5': 62, '6': 64, '7': 66,
}

# MIDI 合法范围
MIDI_MIN = 21
MIDI_MAX = 108

# GM 乐器音色表 (General MIDI)
INSTRUMENTS = {
    0: 'Acoustic Grand Piano',
    1: 'Bright Acoustic Piano',
    2: 'Electric Grand Piano',
    4: 'Acoustic Guitar (Nylon)',
    5: 'Acoustic Guitar (Steel)',
    8: 'Banjo',
    11: 'Electric Guitar (Clean)',
    19: 'Tuba',
    22: 'Violin',
    23: 'Viola',
    24: 'Cello',
    25: 'Contrabass',
    28: 'Trumpet',
    32: 'Accordion',
    40: 'Recorder',
    56: 'Harpsichord',
    58: 'Vibraphone',
    59: 'Marimba',
    72: 'Shakuhachi',
    73: 'Whistle',
    76: 'Ocarina',
}

# 默认参数
DEFAULT_BPM = 120
DEFAULT_BEATS_PER_BAR = 4
DEFAULT_PROGRAM = 0  # 钢琴
BEAT_DURATION_MS = lambda bpm: int(60000 / bpm)  # 一拍毫秒数


def _parse_token(token: str) -> tuple[str, int, int] | None:
    """解析简谱 token，返回 (类型, 音高, 拍数) 或 None。

    类型: 'note' | 'rest'
    音高: MIDI 音符编号（未偏移前）
    拍数: 音符持续时间（拍，整数）
    """
    if token in ('|', ''):
        return None
    if token == '0':
        return ('rest', 0, 1)  # 休止符默认1拍

    # 先分离节奏/延长标记：_（延长）和 .（附点）
    base_token = token

    # 处理末尾的节奏标记
    if token.endswith('_'):
        # 下划线表示延长音符
        base_token = token[:-1]
    elif token.endswith('.'):
        # 附点音符，时值增加50%
        base_token = token[:-1]
        # 附点用后续处理，拍数会是整数，通过组合解决

    # 解析延长标记（末尾连续的 -）
    extend_count = 0
    s = base_token
    while s.endswith('-') and len(s) > 1:
        extend_count += 1
        s = s[:-1]

    if not s:
        return None

    if s.startswith('+'):
        try:
            num = int(s[1:])
        except ValueError:
            return None
        midi_note = KEY_G_MIDDLE[str(num)] + 12
        # 基础拍数1 + 延长拍数，附点会导致半拍，需整体调整为整数拍
        beats = 1 + extend_count
        if token.endswith('.'):
            beats = beats * 2 + 1  # 附点变成3/2拍，乘以2得3拍
        else:
            beats = beats + extend_count  # 延长每个加1拍
        return ('note', midi_note, beats)
    elif s.startswith('-'):
        try:
            num = int(s[1:])
        except ValueError:
            return None
        midi_note = KEY_G_MIDDLE[str(num)] - 12
        beats = 1 + extend_count
        if token.endswith('.'):
            beats = beats * 2 + 1
        else:
            beats = beats + extend_count
        return ('note', midi_note, beats)
    elif s.isdigit() and 1 <= int(s) <= 7:
        midi_note = KEY_G_MIDDLE[s]
        beats = 1 + extend_count
        if token.endswith('.'):
            beats = beats * 2 + 1
        else:
            beats = beats + extend_count
        return ('note', midi_note, beats)
    elif s == '0':
        return ('rest', 0, 1)

    return None


def _clamp_note(note: int) -> int:
    """裁剪到 MIDI 合法范围。"""
    return max(MIDI_MIN, min(MIDI_MAX, note))


def text_to_midi_events(
    text: str,
    bpm: int = DEFAULT_BPM,
) -> list[tuple[int, Message]]:
    """将简谱文本转换为 MIDI 事件列表。

    Args:
        text: 简谱文本
        bpm: BPM（每分钟拍数）

    Returns:
        事件列表: [(tick, message), ...]
    """
    events = []
    tick = 0
    beat_ms = BEAT_DURATION_MS(bpm)
    NOTE_GAP = int(beat_ms * 0.05)  # 音符间隔: 50ms (约一拍的一半)

    lines = text.split('\n')
    for line in lines:
        parts = line.split()
        for part in parts:
            parsed = _parse_token(part)
            if parsed is None:
                continue

            note_type, midi_note, duration = parsed
            midi_note = _clamp_note(midi_note)

            if note_type == 'note':
                # note_on 放在当前 tick，note_off 提前一点离开（留出音符间隔）
                note_off_tick = tick + duration * beat_ms - NOTE_GAP
                events.append((tick, Message('note_on', note=midi_note, velocity=64)))
                events.append((note_off_tick, Message('note_off', note=midi_note, velocity=0)))
                tick = note_off_tick + NOTE_GAP  # 下一个音符从间隔之后开始
            elif note_type == 'rest':
                tick += duration * beat_ms

    return events


def text_to_midi(
    text: str,
    bpm: int = DEFAULT_BPM,
    program: int = 0,
) -> MidiFile:
    """将简谱文本转换为 MidiFile 对象。

    Args:
        text: 简谱文本
        bpm: BPM
        program: MIDI 程序号（0=钢琴）

    Returns:
        MidiFile 对象
    """
    events = text_to_midi_events(text, bpm)

    mid = MidiFile(type=1, ticks_per_beat=480)
    track = MidiTrack()
    mid.tracks.append(track)

    # Tempo
    usec_per_beat = int(60000000 / bpm)
    track.append(MetaMessage('set_tempo', tempo=usec_per_beat))

    # Time Signature
    track.append(MetaMessage('time_signature', numerator=4, denominator=4))

    # Program Change (钢琴)
    track.append(Message('program_change', program=program, channel=0))

    # 音符事件
    tick = 0
    for evt_tick, msg in events:
        if msg.type in ('note_on', 'note_off'):
            track.append(msg.copy(time=evt_tick - tick))
            tick = evt_tick
        else:
            track.append(msg)

    return mid


def export_midi(
    text: str,
    filepath: str,
    bpm: int = DEFAULT_BPM,
    program: int = DEFAULT_PROGRAM,
) -> str:
    """将简谱文本导出为 MIDI 文件。

    Args:
        text: 简谱文本
        filepath: 输出文件路径
        bpm: BPM
        program: MIDI 程序号（乐器音色）

    Returns:
        文件路径
    """
    mid = text_to_midi(text, bpm, program=program)
    mid.save(filepath)
    return filepath
