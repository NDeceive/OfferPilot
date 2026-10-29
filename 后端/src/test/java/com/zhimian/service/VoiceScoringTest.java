package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 语音派生分的口径保护：不依赖数据库，纯函数。 */
class VoiceScoringTest {

    private static JsonNode m(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Test
    void fluentAnswerScoresHigherThanChoppyOne() throws Exception {
        double fluent = VoiceAnalysisService.fluency(m("{\"pause_count\":0,\"longest_pause\":0,\"speech_ratio\":0.85}"));
        double choppy = VoiceAnalysisService.fluency(m("{\"pause_count\":5,\"longest_pause\":2.4,\"speech_ratio\":0.4}"));
        assertTrue(fluent > choppy, "停顿多的应该更低: " + fluent + " vs " + choppy);
        assertTrue(fluent <= 100 && choppy >= 30, "分数要落在 30-100");
    }

    @Test
    void volumePrefersComfortableLoudness() throws Exception {
        double good = VoiceAnalysisService.volume(m("{\"rms_mean_db\":-20,\"rms_std_db\":2}"));
        double quiet = VoiceAnalysisService.volume(m("{\"rms_mean_db\":-42,\"rms_std_db\":2}"));
        double shaky = VoiceAnalysisService.volume(m("{\"rms_mean_db\":-20,\"rms_std_db\":12}"));
        assertTrue(good > quiet, "太小应该扣分");
        assertTrue(good > shaky, "忽大忽小应该扣分");
    }

    @Test
    void confidenceRewardsModeratePitchVariation() throws Exception {
        double steady = VoiceAnalysisService.confidence(m("{\"f0_std\":22,\"rms_std_db\":3,\"pause_count\":0}"));
        double flat = VoiceAnalysisService.confidence(m("{\"f0_std\":2,\"rms_std_db\":3,\"pause_count\":0}"));
        assertTrue(steady > flat, "一直一个调应该更低");
    }
}
