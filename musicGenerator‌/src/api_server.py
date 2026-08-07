"""简谱旋律生成 API 服务。

基于 FastAPI，预加载 Hot/Sad/Fairy 三种风格的 RNN 模型，
接收简谱输入，返回生成的简谱文本和 MIDI 文件。

启动: uvicorn src.api_server:app --host 0.0.0.0 --port 8000 --workers 2
"""

import os
import sys
import io
import base64

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import Optional

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from src.model_loader import ModelLoader, STYLES
from src.encoder import encode_text
from src.decoder import decode_to_standard
from src.midi_export import export_midi, INSTRUMENTS
from src import midi_tokenizer


# ------------------------------------------------------------------ #
# Pydantic models for request/response
# ------------------------------------------------------------------ #

class GenerateRequest(BaseModel):
    style: str = Field(..., description='风格: hot / sad / fairy / midi', pattern='^(hot|sad|fairy|midi)$')
    input_text: str = Field(default='', description='起始简谱文本（可为空，从 BOS 开始）')
    output_length: int = Field(default=128, ge=16, le=512, description='输出长度（字符数）')
    temperature: float = Field(default=0.5, ge=0.1, le=2.0, description='采样温度')
    model_type: str = Field(default='rnn', description='模型类型: rnn / lstm / gru')
    instrument: str = Field(default='piano', description='乐器名称或编号')
    key: str = Field(default='G', description='输出调性: G / C')
    bpm: int = Field(default=120, ge=40, le=300, description='MIDI BPM')
    return_midi: bool = Field(default=True, description='是否返回 Base64 编码的 MIDI')


class GenerateResponse(BaseModel):
    status: str
    generated_text: str
    midi_base64: Optional[str] = None
    model_type: str
    style: str


class HealthResponse(BaseModel):
    status: str
    models: dict


# ------------------------------------------------------------------ #
# App setup
# ------------------------------------------------------------------ #

app = FastAPI(
    title='Music Generator API',
    description='简谱旋律生成服务 - 支持 Hot/Sad/Fairy 三种风格',
    version='1.0.0',
)

# 全局模型加载器
loader = ModelLoader()


def _resolve_instrument(instrument_name: str) -> int:
    """解析乐器名称为 MIDI program number。"""
    name = instrument_name.lower().strip()
    if name.isdigit():
        prog = int(name)
        if prog in INSTRUMENTS:
            return prog
        return 0  # fallback to piano
    for num, nname in INSTRUMENTS.items():
        if nname.lower().startswith(name) or name in nname.lower():
            return num
    return 0  # fallback to piano


@app.post('/api/generate', response_model=GenerateResponse)
def generate(req: GenerateRequest):
    """生成简谱旋律。

    Args:
        style: 风格 (hot/sad/fairy)
        input_text: 起始简谱文本
        output_length: 生成字符数
        temperature: 采样温度
        model_type: 模型类型 (rnn/lstm/gru)
        instrument: 乐器名称或编号
        key: 调性 (G/C)
        bpm: MIDI BPM
        return_midi: 是否返回 MIDI
    """
    style = req.style
    model_type = req.model_type

    # 检查模型是否加载
    if not loader.load_status.get(style, {}).get('loaded'):
        raise HTTPException(
            status_code=503,
            detail=f"Style '{style}' model is not loaded. Check /health for available models.",
        )

    model = loader.get_model(style, model_type)
    vocab = loader.get_vocab(style, model_type)

    if model is None or vocab is None:
        raise HTTPException(status_code=503, detail=f"Model or vocabulary not available for {style}/{model_type}")

    # 读取 output_mode
    output_mode = loader.load_status.get(style, {}).get('output_mode', 'text')

    # 准备种子
    bos_idx = vocab.token2idx.get('<BOS>', 1)
    if req.input_text.strip():
        if output_mode == 'midi':
            # MIDI 模型不支持简谱输入，忽略 input_text
            seed_indices = [bos_idx]
        else:
            seed_text = encode_text(req.input_text)
            seed_indices = vocab.encode(seed_text)
    else:
        seed_indices = [bos_idx]

    # 生成
    generated_indices = model.generate(
        vocab, indices=seed_indices,
        max_length=req.output_length,
        temperature=req.temperature,
    )
    print(f"  Generated {len(generated_indices)} tokens (mode={output_mode})")

    # 解码
    midi_b64 = None
    if output_mode == 'midi':
        # MIDI 原生路径：token → MIDI
        gen_tokens = [vocab.idx2token[i] if 0 <= i < len(vocab.idx2token) else '<UNK>' for i in generated_indices]
        for tok in ['<BOS>', '<EOS>', '<PAD>', '<UNK>']:
            gen_tokens = [t for t in gen_tokens if t != tok]
        try:
            midi = midi_tokenizer.tokens_to_midi(gen_tokens, bpm=req.bpm, program=_resolve_instrument(req.instrument))
            tmp_path = os.path.join(os.path.dirname(__file__), '..', 'output', 'api_temp_midi.mid')
            os.makedirs(os.path.dirname(tmp_path), exist_ok=True)
            midi.save(tmp_path)
            with open(tmp_path, 'rb') as f:
                midi_b64 = base64.b64encode(f.read()).decode('utf-8')
            os.remove(tmp_path)
        except Exception as e:
            print(f"  [WARN] MIDI decode failed: {e}")
        gen_text = ''
    else:
        # 简谱路径（原有逻辑）
        gen_text = vocab.decode(generated_indices)
        for tok in ['<BOS>', '<EOS>', '<PAD>', '<UNK>']:
            gen_text = gen_text.replace(tok, '')
        gen_text = ' '.join(gen_text.split())
        gen_text = decode_to_standard(gen_text)

        # 调性处理
        output_key = req.key.upper()
        if output_key == 'C':
            from src.dataset import transpose_to_key
            gen_text = transpose_to_key(gen_text, 'G', 'C')

        # MIDI 生成
        if req.return_midi:
            midi_path = os.path.join(os.path.dirname(__file__), '..', 'output', 'api_temp.mid')
            os.makedirs(os.path.dirname(midi_path), exist_ok=True)
            try:
                export_midi(gen_text, midi_path, bpm=req.bpm, program=_resolve_instrument(req.instrument))
                with open(midi_path, 'rb') as f:
                    midi_b64 = base64.b64encode(f.read()).decode('utf-8')
                os.remove(midi_path)
            except Exception as e:
                print(f"  [WARN] MIDI export failed: {e}")

    return GenerateResponse(
        status='success',
        generated_text=gen_text,
        midi_base64=midi_b64,
        model_type=model_type,
        style=style,
    )


@app.get('/health', response_model=HealthResponse)
def health():
    """健康检查。"""
    return HealthResponse(
        status='ok',
        models=loader.get_health(),
    )


if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='0.0.0.0', port=8000, workers=2)
