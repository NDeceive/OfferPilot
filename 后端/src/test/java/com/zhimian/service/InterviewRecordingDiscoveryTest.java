package com.zhimian.service;

import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.controller.InterviewAudioController;
import com.zhimian.entity.InterviewAnswerAudio;
import com.zhimian.entity.InterviewSession;
import com.zhimian.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewRecordingDiscoveryTest {
    @TempDir Path directory;
    @AfterEach void clear() { UserContext.clear(); }

    @Test void recordingsAreListedWithoutAnalysisAndPrivatePathsAreNotReturned() throws Exception {
        var audio=mock(AnswerAudioService.class);var sessions=mock(InterviewSessionMapper.class);
        var controller=new InterviewAudioController(audio,mock(VoiceRetentionService.class),sessions);
        var session=new InterviewSession();session.setUserId(10L);
        when(sessions.selectById(1L)).thenReturn(session);UserContext.set(10L,"STUDENT");
        var first=new InterviewAnswerAudio();first.setId(7L);first.setRoundNo(1);first.setStatus("OK");first.setDurationMs(1000);
        var missing=new InterviewAnswerAudio();missing.setId(8L);missing.setRoundNo(2);missing.setStatus("OK");
        var file=directory.resolve("1.wav");Files.write(file,new byte[]{1,2,3});
        when(audio.listBySession(1L)).thenReturn(List.of(first,missing));
        when(audio.pathOf(first)).thenReturn(file);when(audio.pathOf(missing)).thenReturn(directory.resolve("missing.wav"));
        var rows=controller.recordings(1L).getData();
        assertEquals(2,rows.size());assertEquals(7L,rows.get(0).get("audioId"));
        assertEquals(true,rows.get(0).get("available"));assertEquals(false,rows.get(1).get("available"));
        assertFalse(rows.get(0).containsKey("filePath"));
        assertEquals(1000,rows.get(0).get("durationMs"));
    }

    @Test void anotherUserCannotDiscoverSessionRecordings() {
        var audio=mock(AnswerAudioService.class);var sessions=mock(InterviewSessionMapper.class);
        var controller=new InterviewAudioController(audio,mock(VoiceRetentionService.class),sessions);
        var session=new InterviewSession();session.setUserId(10L);when(sessions.selectById(1L)).thenReturn(session);
        UserContext.set(20L,"STUDENT");
        assertThrows(BizException.class,()->controller.recordings(1L));
        verifyNoInteractions(audio);
    }
}
