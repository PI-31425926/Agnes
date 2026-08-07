"""GRU 字符级语言模型。

架构：
  Embedding -> GRU(x2) -> Dropout -> Linear -> Softmax

GRU 是 LSTM 的轻量变体，参数量更少，训练更快。
接口与 MusicLSTM / MusicTransformer 保持一致。
"""

import torch
import torch.nn as nn


class MusicGRU(nn.Module):
    """基于 GRU 的简谱字符级语言模型。"""

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

        # GRU 层
        self.gru = nn.GRU(
            input_size=embed_dim,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            dropout=dropout if num_layers > 1 else 0.0,
            batch_first=True,
        )

        # 输出层
        self.fc = nn.Linear(hidden_dim, vocab_size)

        self.to(device)

    def forward(self, x, hidden=None):
        """前向传播。

        Args:
            x: token 索引，shape (batch, seq_len)
            hidden: 初始隐藏状态

        Returns:
            output: (batch, seq_len, vocab_size)
            hidden: 最终隐藏状态
        """
        embedded = self.embedding(x)  # (batch, seq_len, embed_dim)
        gru_out, hidden = self.gru(embedded, hidden)  # (batch, seq_len, hidden_dim)
        output = self.fc(gru_out)  # (batch, seq_len, vocab_size)
        return output, hidden

    def generate(
        self,
        vocab,
        indices: list[int],
        max_length: int = 128,
        temperature: float = 0.8,
    ) -> list[int]:
        """从给定种子 token 序列开始生成新 token。"""
        generated = list(indices)

        with torch.no_grad():
            for _ in range(max_length):
                window_size = 256
                inputs = generated[-window_size:]

                input_tensor = torch.tensor(
                    [inputs], dtype=torch.long, device=self.device
                )

                output, _ = self.forward(input_tensor)

                probs = output[0, -1, :] / temperature
                probs = nn.functional.softmax(probs, dim=-1)

                next_idx = torch.multinomial(probs, 1).item()
                generated.append(next_idx)

                eos_idx = vocab.token2idx.get('<EOS>', 0)
                if next_idx == eos_idx:
                    break

        return generated

    def save(self, filepath: str, output_mode: str = 'text'):
        """保存模型权重和配置。"""
        torch.save({
            'model_type': 'gru',
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
        self.model_type = checkpoint.get('model_type', 'gru')
        self.output_mode = checkpoint.get('output_mode', 'text')
        self.vocab_size = checkpoint['vocab_size']
        self.embed_dim = checkpoint['embed_dim']
        self.hidden_dim = checkpoint['hidden_dim']
        self.num_layers = checkpoint['num_layers']
        self.dropout = checkpoint['dropout']
        self.load_state_dict(checkpoint['state_dict'])
