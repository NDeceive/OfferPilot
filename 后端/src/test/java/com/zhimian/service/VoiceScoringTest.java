package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.zhimian.config.VoiceProperties;
import com.zhimian.entity.InterviewAnswerAudio;
import com.zhimian.entity.InterviewVoiceAnalysis;
import com.zhimian.entity.InterviewVoiceSession;
import com.zhimian.mapper.InterviewVoiceAnalysisMapper;
import com.zhimian.mapper.InterviewVoiceSessionMapper;
import com.zhimian.service.voice.VoiceAnalysisClient;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 语音派生分的口径保护：不依赖数据库，纯函数。 */
class VoiceScoringTest {

    private static final String VALID = "{\"empty\":false,\"duration\":2,\"speech_ratio\":0.7,\"pause_count\":0,\"longest_pause\":0,\"rms_mean_db\":-20,\"rms_std_db\":2,\"frame_count\":40}";

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

    @Test
    void emptyMissingAndNonfiniteMetricsCannotBecomeScores() throws Exception {
        var empty = m("{\"duration\":0.1,\"empty\":true}");
        assertFalse(VoiceAnalysisService.validMetrics(empty));
        assertFalse(VoiceAnalysisService.validMetrics(m("{}")));
        assertThrows(IllegalArgumentException.class, () -> VoiceAnalysisService.fluency(empty));
        assertThrows(IllegalArgumentException.class, () -> VoiceAnalysisService.volume(empty));
        assertThrows(IllegalArgumentException.class, () -> VoiceAnalysisService.confidence(empty));
        assertTrue(VoiceAnalysisService.validMetrics(m(VALID)), "Missing pitch still permits real volume/pause metrics");
        assertThrows(IllegalArgumentException.class, () -> VoiceAnalysisService.confidence(m(VALID)));
        var invalid = (com.fasterxml.jackson.databind.node.ObjectNode)m(VALID);
        invalid.put("speech_ratio", 0);assertFalse(VoiceAnalysisService.validMetrics(invalid));
        invalid.put("speech_ratio", 1.2);assertFalse(VoiceAnalysisService.validMetrics(invalid));
        invalid.put("speech_ratio", Double.NaN);assertFalse(VoiceAnalysisService.validMetrics(invalid));
        invalid.put("speech_ratio", .7);invalid.remove("rms_std_db");
        assertFalse(VoiceAnalysisService.validMetrics(invalid));
    }

    @Test
    void invalidAnalysisIsNotPersistedAndHistoricalInvalidRowsAreExcluded() throws Exception {
        var client = mock(VoiceAnalysisClient.class);
        var audioService = mock(AnswerAudioService.class);
        var sessions = mock(InterviewVoiceSessionMapper.class);
        var analyses = mock(InterviewVoiceAnalysisMapper.class);
        var service = new VoiceAnalysisService(new VoiceProperties(),client,audioService,sessions,analyses);
        var state = new InterviewVoiceSession();state.setEnabled(1);
        when(sessions.selectById(1L)).thenReturn(state);
        var audio = new InterviewAnswerAudio();audio.setStatus("OK");audio.setByteSize(3244L);audio.setRoundNo(1);
        when(audioService.listBySession(1L)).thenReturn(List.of(audio));
        when(audioService.pathOf(audio)).thenReturn(Path.of("short.wav"));
        when(client.analyze(any())).thenReturn(m("{\"duration\":0.1,\"empty\":true}"));
        assertEquals("FAILED", service.analyzeSession(1L));
        verify(analyses, never()).insert(any(InterviewVoiceAnalysis.class));
        var old = new InterviewVoiceAnalysis();old.setMetricsJson("{\"duration\":0.1,\"empty\":true}");
        old.setFluency(BigDecimal.valueOf(100));old.setVolumeScore(BigDecimal.valueOf(68));old.setConfidenceEst(BigDecimal.valueOf(90));
        when(analyses.selectList(any())).thenReturn(List.of(old));
        assertTrue(service.results(1L).isEmpty());
        assertEquals(-1,service.overallScore(1L));
        old.setMetricsJson(VALID);
        assertEquals(-1,service.overallScore(1L), "Legacy default pitch score cannot survive missing pitch metrics");
        assertNull(old.getConfidenceEst());
    }
}
