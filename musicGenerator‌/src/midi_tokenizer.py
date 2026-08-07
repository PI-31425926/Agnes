"""MIDI Tokenizer: Parse MIDI files into self-contained note tokens.

Token format: n_<pitch>_<dt>
  pitch: MIDI note number (0-127)
  dt: duration bucket (dt_0, dt_1/4, dt_1/2, dt_3/4, dt_1, dt_3/2, dt_2, dt_4, dt_long)

Special tokens: <BOS> <EOS> <PAD> <UNK> <REST>
BPM token: bpm_<N>

Usage:
  tokens = midi_to_tokens('path/to/file.mid')
  vocab = build_vocab(token_lists)
  midi = tokens_to_midi(tokens, bpm=120, program=0)
"""

import os
import re
import mido
from pathlib import Path
from collections import Counter


# Duration quantization buckets (in beats, assuming ticks_per_beat=480)
DURATION_BUCKETS = [
    (0.0, 0.2, 'dt_0'),
    (0.2, 0.4, 'dt_1/4'),
    (0.4, 0.6, 'dt_1/2'),
    (0.6, 0.9, 'dt_3/4'),
    (0.9, 1.4, 'dt_1'),
    (1.4, 1.9, 'dt_3/2'),
    (1.9, 2.9, 'dt_2'),
    (2.9, 4.9, 'dt_4'),
    (4.9, float('inf'), 'dt_long'),
]

# Pitch filtering range
MIN_PITCH = 48   # G3
MAX_PITCH = 84   # G5

# Special tokens
SPECIAL_TOKENS = ['<PAD>', '<UNK>', '<BOS>', '<EOS>', '<REST>']


def quantize_duration(ticks: int, ticks_per_beat: int = 480) -> str:
    """Quantize tick duration to the nearest bucket string."""
    beats = ticks / ticks_per_beat
    for lo, hi, name in DURATION_BUCKETS:
        if lo <= beats < hi:
            return name
    return 'dt_long'


def parse_midi(midi_path: str) -> tuple[list[tuple[int, int, int]], int]:
    """Parse a MIDI file and extract monophonic melody events.

    Returns:
        events: list of (abs_tick, pitch, duration_ticks) sorted by abs_tick
        ticks_per_beat: the MIDI file's ticks per beat
    """
    mid = mido.MidiFile(midi_path)
    tpb = mid.ticks_per_beat

    # Merge all tracks, sort by absolute tick
    all_msgs = []
    for track in mid.tracks:
        abs_tick = 0
        for msg in track:
            abs_tick += msg.time
            all_msgs.append((abs_tick, msg))
    all_msgs.sort(key=lambda x: x[0])

    # Group note_ons by onset tick (round to 10 ticks for grouping)
    onset_groups: dict[int, list[int]] = {}
    for abs_tick, msg in all_msgs:
        if msg.type == 'note_on' and msg.velocity > 0:
            group_tick = (abs_tick // 10) * 10
            if group_tick not in onset_groups:
                onset_groups[group_tick] = []
            onset_groups[group_tick].append(msg.note)

    # Build full events with durations from note_off
    events = []
    active: dict[int, int] = {}  # note -> onset_abs_tick
    for abs_tick, msg in all_msgs:
        if msg.type == 'note_on' and msg.velocity > 0:
            active[msg.note] = abs_tick
        elif msg.type == 'note_off':
            if msg.note in active:
                dur = abs_tick - active[msg.note]
                events.append((active[msg.note], msg.note, dur))
                del active[msg.note]

    # Extract melody: for each onset group, keep the highest pitch
    melody_ticks = []
    for tick, notes in onset_groups.items():
        best = max(notes)
        melody_ticks.append(tick)

    # Map back to events
    event_map = {t: (p, d) for t, p, d in events}
    filtered = [(t, event_map[t][0], event_map[t][1]) for t in melody_ticks if t in event_map]
    filtered.sort(key=lambda x: x[0])

    return filtered, tpb


def midi_to_tokens(midi_path: str) -> tuple[list[str], list[str]]:
    """Convert a MIDI file to a list of tokens.

    Returns:
        (tokens, warnings)
    """
    warnings = []
    try:
        events, tpb = parse_midi(midi_path)
    except Exception as e:
        return [], [f"Failed to parse MIDI: {e}"]

    if not events:
        return [], ["No note events found in MIDI file"]

    # Filter by pitch range
    events = [(t, p, d) for t, p, d in events if MIN_PITCH <= p <= MAX_PITCH]
    if not events:
        return [], ["No notes in valid pitch range"]

    # Get BPM from first set_tempo or default
    bpm = 120
    try:
        mid = mido.MidiFile(midi_path)
        for track in mid.tracks:
            for msg in track:
                if msg.type == 'set_tempo':
                    bpm = int(mido.tempo2bpm(msg.tempo))
                    break
            else:
                continue
            break
    except:
        pass

    # Tokenize
    tokens = [f'bpm_{bpm}']
    skipped = 0
    for _, pitch, dur in events:
        dt = quantize_duration(dur, tpb)
        if dt == 'dt_0':
            skipped += 1
            continue
        tokens.append(f'n_{pitch}_{dt}')

    if skipped > 0:
        warnings.append(f"Skipped {skipped} very short notes (dt_0)")

    return tokens, warnings


def build_vocab(token_lists: list[list[str]], min_freq: int = 10) -> 'Vocabulary':
    """Build a Vocabulary from a list of token sequences.

    Args:
        token_lists: list of token lists
        min_freq: minimum frequency to keep a token (else <UNK>)

    Returns:
        Vocabulary with MIDI token support
    """
    from src.dataset import Vocabulary

    vocab = Vocabulary()
    # Add special tokens
    for tok in SPECIAL_TOKENS:
        if tok not in vocab.token2idx:
            vocab.idx2token.append(tok)
            vocab.token2idx[tok] = len(vocab.idx2token) - 1

    # Count all tokens
    counter = Counter()
    for tokens in token_lists:
        counter.update(tokens)

    # Add tokens above min_freq threshold
    for tok, cnt in counter.items():
        if cnt >= min_freq and tok not in vocab.token2idx:
            vocab.idx2token.append(tok)
            vocab.token2idx[tok] = len(vocab.idx2token) - 1

    return vocab


def tokens_to_midi(
    tokens: list[str],
    bpm: int = 120,
    program: int = 0,
    ticks_per_beat: int = 480,
) -> mido.MidiFile:
    """Decode a token sequence back to a MIDI file.

    Args:
        tokens: list of token strings (n_pitch_dt, bpm_N, <REST>, etc.)
        bpm: default BPM if no bpm_N token found
        program: MIDI program number (0=piano)
        ticks_per_beat: MIDI ticks per beat

    Returns:
        MidiFile object
    """
    # Parse BPM from tokens
    current_bpm = bpm
    for tok in tokens:
        if tok.startswith('bpm_'):
            try:
                current_bpm = int(tok.split('_')[1])
                break
            except:
                pass

    # Map duration strings back to ticks
    dur_map = {
        'dt_0': int(ticks_per_beat * 0.1),
        'dt_1/4': int(ticks_per_beat * 0.25),
        'dt_1/2': int(ticks_per_beat * 0.5),
        'dt_3/4': int(ticks_per_beat * 0.75),
        'dt_1': ticks_per_beat,
        'dt_3/2': int(ticks_per_beat * 1.5),
        'dt_2': int(ticks_per_beat * 2),
        'dt_4': int(ticks_per_beat * 4),
        'dt_long': int(ticks_per_beat * 6),
    }

    # Build MIDI file
    mid = mido.MidiFile(type=1, ticks_per_beat=ticks_per_beat)
    track = mido.MidiTrack()
    mid.tracks.append(track)

    # Tempo
    usec_per_beat = int(1000000 * 60 / current_bpm)
    track.append(mido.MetaMessage('set_tempo', tempo=usec_per_beat))
    track.append(mido.MetaMessage('time_signature', numerator=4, denominator=4))
    track.append(mido.Message('program_change', program=program, channel=0))

    # Parse tokens and generate events
    current_tick = 0
    last_tick = 0
    # Track active notes: pitch -> (onset_tick, duration_ticks)
    active: dict[int, tuple[int, int]] = {}

    for tok in tokens:
        if tok.startswith('<'):
            if tok == '<REST>':
                current_tick += ticks_per_beat
            continue
        if tok.startswith('bpm_'):
            continue

        match = re.match(r'^n_(\d+)_(.+)$', tok)
        if match:
            pitch = int(match.group(1))
            dt_str = match.group(2)
            dur_ticks = dur_map.get(dt_str, ticks_per_beat)

            # Close any previously active notes for this pitch
            if pitch in active:
                old_onset, old_dur = active[pitch]
                end_tick = max(current_tick, old_onset + old_dur)
                if end_tick > last_tick:
                    track.append(mido.Message('note_off', note=pitch, velocity=0,
                                              time=end_tick - last_tick))
                    last_tick = end_tick

            # Start new note with appropriate time gap
            if current_tick > last_tick:
                track.append(mido.Message('note_on', note=pitch, velocity=64,
                                          time=current_tick - last_tick))
            else:
                track.append(mido.Message('note_on', note=pitch, velocity=64, time=0))
            last_tick = current_tick
            active[pitch] = (current_tick, dur_ticks)
            current_tick += dur_ticks

    # Close all remaining active notes
    for pitch, (onset, dur) in active.items():
        end_tick = onset + dur
        if end_tick > last_tick:
            track.append(mido.Message('note_off', note=pitch, velocity=0,
                                      time=end_tick - last_tick))
            last_tick = end_tick
        else:
            track.append(mido.Message('note_off', note=pitch, velocity=0, time=ticks_per_beat))
            last_tick += ticks_per_beat

    return mid


def sanitize_filename(name: str) -> str:
    """Convert a filename to ASCII-safe format."""
    # Replace non-ASCII chars with underscore
    safe = re.sub(r'[^\x00-\x7F]', '_', name)
    # Replace special chars
    safe = re.sub(r'[^a-zA-Z0-9_.\-]', '_', safe)
    # Collapse multiple underscores
    safe = re.sub(r'_+', '_', safe)
    return safe.strip('_')
