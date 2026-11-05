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
        var first=new InterviewAnswerAudio();first.setId(7L);first.setRoundNo(1);first.setAnswerSeq(0);first.setStatus("OK");first.setDurationMs(1000);
        var follow=new InterviewAnswerAudio();follow.setId(9L);follow.setRoundNo(1);follow.setAnswerSeq(1);follow.setStatus("OK");follow.setDurationMs(2000);
        var missing=new InterviewAnswerAudio();missing.setId(8L);missing.setRoundNo(2);missing.setStatus("OK");
        var file=directory.resolve("1.wav");Files.write(file,new byte[]{1,2,3});
        var followFile=directory.resolve("1-1.wav");Files.write(followFile,new byte[]{4,5,6});
        when(audio.listBySession(1L)).thenReturn(List.of(first,follow,missing));
        when(audio.pathOf(first)).thenReturn(file);when(audio.pathOf(follow)).thenReturn(followFile);
        when(audio.pathOf(missing)).thenReturn(directory.resolve("missing.wav"));
        var rows=controller.recordings(1L).getData();
        assertEquals(3,rows.size());assertEquals(7L,rows.get(0).get("audioId"));
        assertEquals(0,rows.get(0).get("answerSeq"));assertEquals(1,rows.get(1).get("answerSeq"));
        assertEquals(true,rows.get(0).get("available"));assertEquals(false,rows.get(2).get("available"));
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
