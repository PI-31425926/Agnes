# 音乐生成 API 服务

基于 RNN 的旋律生成服务，支持 **简谱模式**（hot/sad/fairy 三种风格）和 **MIDI 原生模式**。

## 快速开始

```bash
# 安装依赖
pip install -r requirements.txt

# 启动服务
python -m uvicorn src.api_server:app --host 0.0.0.0 --port 8000 --workers 2
```

启动后访问 `http://localhost:8000/docs` 查看交互式文档。

## API 端点

### POST /api/generate — 生成旋律

```bash
curl -X POST http://localhost:8000/api/generate \
  -H "Content-Type: application/json" \
  -d '{"style":"hot","output_length":64,"temperature":0.5}'
```

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| style | string | - | hot / sad / fairy / midi |
| input_text | string | "" | 起始简谱文本（midi 模式忽略） |
| output_length | int | 128 | 输出 token 数 (16-512) |
| temperature | float | 0.5 | 采样温度 (0.1-2.0) |
| model_type | string | rnn | 模型类型（当前仅支持 rnn） |
| instrument | string | piano | 乐器名称或编号 |
| key | string | G | 输出调性：G / C |
| bpm | int | 120 | MIDI BPM (40-300) |
| return_midi | bool | true | 是否返回 Base64 MIDI |

**响应示例（简谱模式）：**

```json
{
  "status": "success",
  "generated_text": "6 +1 | +2 +1 7 0 | +1 +2 +3 +5 | ...",
  "midi_base64": "TVRoZAAAAAYAAQACAHBNRVRNQSECAAAADQ==...",
  "model_type": "rnn",
  "style": "hot"
}
```

**响应示例（MIDI 模式）：**

```json
{
  "status": "success",
  "generated_text": "",
  "midi_base64": "TVRoZAAAAAYAAQACAHBNRVRNQSECAAAADQ==...",
  "model_type": "rnn",
  "style": "midi"
}
```

### GET /health — 健康检查

```bash
curl http://localhost:8000/health
```

**响应：**

```json
{
  "status": "ok",
  "models": {
    "hot":    {"loaded": true, "type": "rnn",  "output_mode": "text"},
    "sad":    {"loaded": true, "type": "rnn",  "output_mode": "text"},
    "fairy":  {"loaded": true, "type": "rnn",  "output_mode": "text"},
    "midi":   {"loaded": true, "type": "rnn",  "output_mode": "midi"}
  }
}
```

## 模型

预加载 4 个 RNN 模型：

| 风格 | output_mode | 词表大小 | 参数量 | 说明 |
|------|-------------|---------|--------|------|
| hot（燃歌） | text | 55 | 889K | 简谱输出 |
| sad（伤感） | text | 53 | 888K | 简谱输出 |
| fairy（仙歌） | text | 61 | 893K | 简谱输出 |
| midi（MIDI 原生） | midi | 299 | 346K | 直接生成 n_pitch_dt 序列 |

MIDI 原生模式使用 `n_<pitch>_<dt>` token 格式，音高范围 G3~G5（37 个音），8 档时长量化（dt_1/4 ~ dt_long），无需经过简谱中转。

## 资源需求

- **磁盘**: ~15MB（模型文件）
- **内存**: ~50MB（4 个模型加载到内存）
- **CPU**: 推理在 CPU 上运行
- **端口**: 8000
