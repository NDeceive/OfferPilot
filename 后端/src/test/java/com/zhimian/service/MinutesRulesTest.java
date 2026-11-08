package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MinutesRulesTest {
    @Test void windowsExcludeUnknownAndBoundaryMessages() {
        var windows=List.<Map<String,Object>>of(Map.of("id",7L,"opened_at",Timestamp.valueOf("2026-11-04 10:00:00"),"closed_at",Timestamp.valueOf("2026-11-04 10:01:00")));
        assertNull(InterviewMinutesService.windowAt(windows,null));
        assertNull(InterviewMinutesService.windowAt(windows,Timestamp.valueOf("2026-11-04 10:00:00")));
        assertEquals(7L,InterviewMinutesService.windowAt(windows,Timestamp.valueOf("2026-11-04 10:00:30")));
        assertNull(InterviewMinutesService.windowAt(windows,Timestamp.valueOf("2026-11-04 10:01:00")));
    }
    @Test void summariesAreSingleParagraphsAndInvalidFormatsCannotMasqueradeAsSuccess() throws Exception {
        assertEquals("第一句话。 第二句话。",MinutesSummaryJobService.normalizeParagraph(" 第一句话。\n 第二句话。 "));
        assertThrows(IllegalStateException.class,()->MinutesSummaryJobService.normalizeParagraph(" "));
        assertThrows(IllegalStateException.class,()->MinutesSummaryJobService.normalizeParagraph("{\"text\":\"不是纯文本\"}"));
        var root=new ObjectMapper().readTree("{\"overview\":[{\"text\":\"旧版概括\"}],\"topics\":[]}");
        assertEquals("旧版概括",MinutesSummaryJobService.storedParagraph(root));
    }
}
