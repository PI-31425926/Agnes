package com.bilibili.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MusicResponse {
    private String generatedText;  // 生成的简谱文本
    private String midiBase64;     // Base64 编码的 MIDI 数据
    private String modelType;      // 使用的模型类型
    private String style;          // 风格
}
