package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private static final String MAIN_SEGMENT = "{\"empty\":false,\"duration\":2,\"speech_ratio\":0.6,\"pause_count\":1,\"longest_pause\":1.2,\"rms_mean_db\":-24,\"rms_std_db\":3,\"frame_count\":40,\"f0_std\":20}";
    private static final String FOLLOW_SEGMENT = "{\"empty\":false,\"duration\":3,\"speech_ratio\":0.8,\"pause_count\":2,\"longest_pause\":1.5,\"rms_mean_db\":-18,\"rms_std_db\":5,\"frame_count\":60,\"f0_std\":30}";

    private static JsonNode m(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    private static InterviewAnswerAudio audio(int roundNo, int answerSeq) {
        var audio = new InterviewAnswerAudio();
        audio.setRoundNo(roundNo);
        audio.setAnswerSeq(answerSeq);
        audio.setStatus("OK");
        audio.setByteSize(1000L);
        return audio;
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

    @Test
    void followUpSegmentsAggregateByDurationWithoutInventingPitch() throws Exception {
        var both = VoiceAnalysisService.aggregate(List.of(m(MAIN_SEGMENT), m(FOLLOW_SEGMENT)));
        assertTrue(VoiceAnalysisService.validMetrics(both));
        assertEquals(5.0, both.path("duration").asDouble(), 0.001);
        assertEquals(0.72, both.path("speech_ratio").asDouble(), 0.001);
        assertEquals(3, both.path("pause_count").asInt());
        assertEquals(1.5, both.path("longest_pause").asDouble(), 0.001);
        assertEquals(-20.4, both.path("rms_mean_db").asDouble(), 0.001);
        assertEquals(4.2, both.path("rms_std_db").asDouble(), 0.001);
        assertEquals(100, both.path("frame_count").asInt());
        assertEquals(26.0, both.path("f0_std").asDouble(), 0.001);

        var noPitch = m("{\"empty\":false,\"duration\":1,\"speech_ratio\":0.5,\"pause_count\":0,"
                + "\"longest_pause\":0.3,\"rms_mean_db\":-20,\"rms_std_db\":2,\"frame_count\":20}");
        var mixed = VoiceAnalysisService.aggregate(List.of(m(MAIN_SEGMENT), noPitch));
        assertTrue(VoiceAnalysisService.validMetrics(mixed));
        assertFalse(mixed.has("f0_std"), "A missing measurement must stay missing after aggregation");
    }

    @Test
    void analyzeSessionWritesOneAggregatedRowForAMultiSegmentRound() throws Exception {
        var client = mock(VoiceAnalysisClient.class);
        var audioService = mock(AnswerAudioService.class);
        var sessions = mock(InterviewVoiceSessionMapper.class);
        var analyses = mock(InterviewVoiceAnalysisMapper.class);
        var service = new VoiceAnalysisService(new VoiceProperties(),client,audioService,sessions,analyses);
        var state = new InterviewVoiceSession();state.setEnabled(1);
        when(sessions.selectById(1L)).thenReturn(state);
        var main = audio(1, 0);
        var follow = audio(1, 1);
        when(audioService.listBySession(1L)).thenReturn(List.of(main, follow));
        when(audioService.pathOf(main)).thenReturn(Path.of("main.wav"));
        when(audioService.pathOf(follow)).thenReturn(Path.of("follow.wav"));
        when(client.analyze(anyString())).thenAnswer(call -> {
            var path = call.getArgument(0, String.class);
            return path.endsWith("main.wav") ? m(MAIN_SEGMENT) : m(FOLLOW_SEGMENT);
        });
        when(analyses.selectList(any())).thenReturn(List.<InterviewVoiceAnalysis>of());

        assertEquals("ANALYZED", service.analyzeSession(1L));

        var captor = ArgumentCaptor.forClass(InterviewVoiceAnalysis.class);
        verify(analyses).insert(captor.capture());
        var saved = captor.getValue();
        assertEquals(1, saved.getRoundNo());
        assertEquals(5.0, m(saved.getMetricsJson()).path("duration").asDouble(), 0.001);
        assertNotNull(saved.getFluency());
        assertNotNull(saved.getVolumeScore());
        assertNotNull(saved.getConfidenceEst());
    }
}
