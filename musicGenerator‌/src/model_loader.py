"""统一的模型加载器。

支持按风格（hot/sad/fairy）和模型类型（rnn/lstm/gru）加载模型和词表。
启动时预加载所有模型到内存，运行时复用。
"""

import os
import torch
from typing import Optional

from src.model_rnn import MusicRNN
from src.model import MusicLSTM
from src.model_gru import MusicGRU


# 默认模型路径配置
DEFAULT_MODEL_DIR = 'results'
STYLES = ['hot', 'sad', 'fairy']
MODEL_TYPES = ['rnn', 'lstm', 'gru']

# 各风格的模型路径映射
STYLE_MODEL_PATHS = {
    'hot': 'hot_rnn_compare/models/best_rnn.pth',
    'sad': 'sad_compare/models/best_rnn.pth',
    'fairy': 'fairy_compare/models/best_rnn.pth',
}

# MIDI 原生模型（不按风格分类）
MIDI_MODEL_PATH = 'midi_rnn/models/best_rnn.pth'


class ModelLoader:
    """管理所有预加载的模型和词表。"""

    def __init__(self, model_dir: str = None):
        if model_dir is None:
            current_file_dir = os.path.dirname(os.path.abspath(__file__))
            # Check if running from prod/ or project root
            if os.path.basename(current_file_dir) == 'src':
                parent = os.path.basename(os.path.dirname(current_file_dir))
                if parent == 'prod':
                    project_root = os.path.dirname(current_file_dir)  # prod/
                else:
                    project_root = os.path.dirname(current_file_dir)  # project root
            else:
                project_root = current_file_dir
            model_dir = os.path.join(project_root, 'results')

        self.model_dir = os.path.abspath(model_dir)
        print(f"[ModelLoader] Using model directory: {self.model_dir}")

        self.models = {}       # {(style, model_type): model}
        self.vocabs = {}       # {(style, model_type): vocab}
        self.load_status = {}  # {style: {'loaded': bool, 'type': str}}
        self._load_all()

    def _get_model_class(self, model_type: str):
        """根据类型返回对应的模型类。"""
        classes = {
            'rnn': MusicRNN,
            'lstm': MusicLSTM,
            'gru': MusicGRU,
        }
        return classes.get(model_type)

    def _load_style_model(self, style: str, model_type: str = 'rnn'):
        """加载指定风格的模型和词表。"""
        key = (style, model_type)
        path_template = STYLE_MODEL_PATHS.get(style)

        if not path_template:
            print(f"  [WARN] No model path for style={style}, type={model_type}")
            self.load_status[style] = {'loaded': False, 'type': model_type, 'output_mode': 'text'}
            return False

        model_path = os.path.join(self.model_dir, path_template)

        if not os.path.exists(model_path):
            print(f"  [WARN] Model not found: {model_path}")
            self.load_status[style] = {'loaded': False, 'type': model_type, 'output_mode': 'text'}
            return False

        try:
            checkpoint = torch.load(model_path, map_location='cpu', weights_only=True)
            model_cls = self._get_model_class(model_type)
            if model_cls is None:
                return False

            if model_type == 'rnn':
                model = model_cls(
                    vocab_size=checkpoint['vocab_size'],
                    embed_dim=checkpoint['embed_dim'],
                    hidden_dim=checkpoint['hidden_dim'],
                    num_layers=checkpoint['num_layers'],
                    dropout=checkpoint['dropout'],
                    device='cpu',
                )
            elif model_type == 'lstm':
                model = model_cls(
                    vocab_size=checkpoint['vocab_size'],
                    embed_dim=checkpoint['embed_dim'],
                    hidden_dim=checkpoint['hidden_dim'],
                    num_layers=checkpoint['num_layers'],
                    dropout=checkpoint['dropout'],
                    device='cpu',
                )
            else:  # gru
                model = model_cls(
                    vocab_size=checkpoint['vocab_size'],
                    embed_dim=checkpoint['embed_dim'],
                    hidden_dim=checkpoint['hidden_dim'],
                    num_layers=checkpoint['num_layers'],
                    dropout=checkpoint['dropout'],
                    device='cpu',
                )

            model.load_state_dict(checkpoint['state_dict'])
            model.eval()

            # 读取 output_mode
            output_mode = checkpoint.get('output_mode', 'text')

            # 加载词表 — 尝试多种命名模式
            vocab = None
            model_dir = os.path.dirname(model_path)
            base_name = os.path.basename(model_path).replace('.pth', '')
            candidates = [
                base_name + '_vocab.pt',           # best_rnn_vocab.pt
                base_name.replace('best', 'latest') + '_vocab.pt',  # latest_rnn_vocab.pt
                base_name + '_rnn_vocab.pt',       # best_rnn_rnn_vocab.pt (wrong but try)
                'latest_rnn_vocab.pt',             # fallback
            ]
            for cand in candidates:
                cand_path = os.path.join(model_dir, cand)
                if os.path.exists(cand_path):
                    vocab = torch.load(cand_path, map_location='cpu', weights_only=False)
                    print(f"    Loaded vocab from {cand}")
                    break
            if vocab is None:
                print(f"    WARN: No vocab found for {model_path}")

            self.models[key] = model
            self.vocabs[key] = vocab
            self.load_status[style] = {'loaded': True, 'type': model_type, 'output_mode': output_mode}
            return True

        except Exception as e:
            print(f"  [ERROR] Failed to load {style}/{model_type}: {e}")
            self.load_status[style] = {'loaded': False, 'type': model_type}
            return False

    def _load_all(self):
        """预加载所有模型（风格RNN + MIDI原生模型）。"""
        print("Loading models...")
        for style in STYLES:
            success = self._load_style_model(style, 'rnn')
            if success:
                print(f"  [OK] {style} (RNN)")
            else:
                print(f"  [FAIL] {style} - no model loaded")
                if style not in self.load_status:
                    self.load_status[style] = {'loaded': False, 'type': 'rnn', 'output_mode': 'text'}

        # 加载MIDI原生模型
        midi_success = self._load_midi_model()
        if midi_success:
            print(f"  [OK] midi (RNN)")
        else:
            print(f"  [FAIL] midi - no model loaded")
            self.load_status['midi'] = {'loaded': False, 'type': 'rnn', 'output_mode': 'text'}

        loaded_count = sum(1 for s in self.load_status.values() if s.get('loaded', False))
        print(f"Models loaded: {loaded_count}/{len(STYLES) + 1}")

    def _load_midi_model(self):
        """加载MIDI原生模型。"""
        key = ('midi', 'rnn')
        model_path = os.path.join(self.model_dir, MIDI_MODEL_PATH)

        if not os.path.exists(model_path):
            print(f"  [WARN] MIDI model not found: {model_path}")
            self.load_status['midi'] = {'loaded': False, 'type': 'rnn', 'output_mode': 'text'}
            return False

        try:
            checkpoint = torch.load(model_path, map_location='cpu', weights_only=True)
            model_cls = self._get_model_class('rnn')
            if model_cls is None:
                return False

            model = model_cls(
                vocab_size=checkpoint['vocab_size'],
                embed_dim=checkpoint['embed_dim'],
                hidden_dim=checkpoint['hidden_dim'],
                num_layers=checkpoint['num_layers'],
                dropout=checkpoint['dropout'],
                device='cpu',
            )
            model.load_state_dict(checkpoint['state_dict'])
            model.eval()
            model.output_mode = checkpoint.get('output_mode', 'text')

            # 加载词表
            vocab = None
            model_dir = os.path.dirname(model_path)
            candidates = [
                'best_rnn_vocab.pt',
                'latest_rnn_vocab.pt',
            ]
            for cand in candidates:
                cand_path = os.path.join(model_dir, cand)
                if os.path.exists(cand_path):
                    vocab = torch.load(cand_path, map_location='cpu', weights_only=False)
                    print(f"    Loaded vocab from {cand}")
                    break
            if vocab is None:
                print(f"    WARN: No vocab found for {model_path}")

            self.models[key] = model
            self.vocabs[key] = vocab
            self.load_status['midi'] = {'loaded': True, 'type': 'rnn', 'output_mode': model.output_mode}
            return True

        except Exception as e:
            print(f"  [ERROR] Failed to load midi model: {e}")
            self.load_status['midi'] = {'loaded': False, 'type': 'rnn', 'output_mode': 'text'}
            return False

    def get_model(self, style: str, model_type: str = 'rnn'):
        """获取指定风格和类型的模型。"""
        key = (style, model_type)
        if key not in self.models:
            # 尝试加载
            self._load_style_model(style, model_type)
        return self.models.get(key)

    def get_vocab(self, style: str, model_type: str = 'rnn'):
        """获取指定风格和类型的词表。"""
        key = (style, model_type)
        if key not in self.vocabs:
            self._load_style_model(style, model_type)
        return self.vocabs.get(key)

    def get_health(self):
        """获取健康检查信息。"""
        return {
            status: info for status, info in self.load_status.items()
        }
