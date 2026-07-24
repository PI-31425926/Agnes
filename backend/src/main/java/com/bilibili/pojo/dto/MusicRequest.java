package com.bilibili.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MusicRequest {
    private String style;          // hot / sad / fairy
    private String inputText;      // 起始简谱文本，可为空
    private Integer outputLength;  // 输出字符数 (16-512)
    private Double temperature;    // 采样温度 (0.1-2.0)
    private String modelType;      // rnn / lstm / gru
    private String instrument;     // 乐器名称或编号
    private String key;            // G / C
    private Integer bpm;           // MIDI BPM (40-300)
    private Boolean returnMidi;    // 是否返回 Base64 MIDI
}
