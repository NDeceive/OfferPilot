package com.zhimian.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpeechTranscriptResponse {
    private String text;
    private String requestId;
    private String model;
    private Double duration;
}
