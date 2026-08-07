"""LSTM 字符级语言模型。

架构：
  Embedding -> LSTM(x2) -> Dropout(0.3) -> Linear -> Softmax

用于学习简谱序列的概率分布，每个字符作为一个 token。
"""

import torch
import torch.nn as nn


class MusicLSTM(nn.Module):
    """基于 LSTM 的简谱字符级语言模型。"""

    def __init__(
        self,
        vocab_size: int,
        embed_dim: int = 128,
        hidden_dim: int = 512,
        num_layers: int = 2,
        dropout: float = 0.3,
        device: str = 'cpu',
    ):
        super().__init__()

        self.vocab_size = vocab_size
        self.embed_dim = embed_dim
        self.hidden_dim = hidden_dim
        self.num_layers = num_layers
        self.dropout = dropout
        self.device = device

        # 词嵌入层
        self.embedding = nn.Embedding(vocab_size, embed_dim, padding_idx=0)

        # LSTM 层
        self.lstm = nn.LSTM(
            input_size=embed_dim,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            dropout=dropout if num_layers > 1 else 0.0,
            batch_first=True,
        )

        # 输出层
        self.fc = nn.Linear(hidden_dim, vocab_size)

        # Softmax
        self.softmax = nn.Softmax(dim=-1)

        self.to(device)

    def forward(self, x, hidden=None):
        """前向传播。

        Args:
            x: token 索引，shape (batch, seq_len)
            hidden: (h_0, c_0) 初始隐藏状态，shape (num_layers, batch, hidden_dim)

        Returns:
            output: 下一个字符的概率分布，shape (batch, seq_len, vocab_size)
            hidden: 最终隐藏状态
        """
        embedded = self.embedding(x)  # (batch, seq_len, embed_dim)
        lstm_out, hidden = self.lstm(embedded, hidden)  # (batch, seq_len, hidden_dim)
        output = self.fc(lstm_out)  # (batch, seq_len, vocab_size)
        return output, hidden

    def generate(
        self,
        vocab,
        indices: list[int],
        max_length: int = 128,
        temperature: float = 0.8,
    ) -> list[int]:
        """从给定种子 token 序列开始生成新 token。

        Args:
            vocab: Vocabulary 对象
            indices: 起始 token 索引列表
            max_length: 最大生成长度
            temperature: 采样温度（越低越保守）

        Returns:
            生成的 token 索引列表（包含种子）
        """
        generated = list(indices)

        with torch.no_grad():
            for _ in range(max_length):
                # 取最近 window_size 个 token 作为输入
                window_size = 256
                inputs = generated[-window_size:]

                # 转换为 tensor
                input_tensor = torch.tensor([inputs], dtype=torch.long, device=self.device)

                # 前向传播
                output, _ = self.forward(input_tensor)

                # 取最后一个时间步的输出
                probs = output[0, -1, :]  # (vocab_size,)

                # temperature 缩放
                probs = probs / temperature
                probs = nn.functional.softmax(probs, dim=-1)

                # 采样下一个 token
                next_idx = torch.multinomial(probs, 1).item()
                generated.append(next_idx)

                # EOS 终止
                eos_idx = vocab.token2idx.get('<EOS>', 0)
                if next_idx == eos_idx:
                    break

        return generated

    def save(self, filepath: str, output_mode: str = 'text'):
        """保存模型权重和配置。"""
        torch.save({
            'model_type': 'lstm',
            'vocab_size': self.vocab_size,
            'embed_dim': self.embed_dim,
            'hidden_dim': self.hidden_dim,
            'num_layers': self.num_layers,
            'dropout': self.dropout,
            'output_mode': output_mode,
            'state_dict': self.state_dict(),
        }, filepath)

    def load(self, filepath: str):
        """加载模型权重和配置。"""
        checkpoint = torch.load(filepath, map_location=self.device, weights_only=True)
        self.model_type = checkpoint.get('model_type', 'lstm')
        self.output_mode = checkpoint.get('output_mode', 'text')
        self.vocab_size = checkpoint['vocab_size']
        self.embed_dim = checkpoint['embed_dim']
        self.hidden_dim = checkpoint['hidden_dim']
        self.num_layers = checkpoint['num_layers']
        self.dropout = checkpoint['dropout']
        self.load_state_dict(checkpoint['state_dict'])
