package com.zhimian.service;

import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.mapper.MeetingAdviceMapper;
import com.zhimian.mapper.MeetingMapper;
import com.zhimian.ws.MeetingRoomRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetingDeleteTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final MeetingService service = new MeetingService(db, mock(MeetingMapper.class),
            mock(MeetingAdviceMapper.class), mock(MeetingRoomRegistry.class));

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    private void meeting(String status) {
        when(db.queryForList(anyString(), eq(7L)))
                .thenReturn(List.of(Map.of("id", 7L, "host_id", 1L, "status", status)));
    }

    @Test
    void hostDeletesEndedMeetingAndAssociatedRecords() {
        UserContext.set(1L, "ENTERPRISE");
        meeting("ENDED");
        service.delete(7L);
        var order = inOrder(db);
        order.verify(db).queryForList(anyString(), eq(7L));
        order.verify(db).update("DELETE FROM meeting_advice WHERE meeting_id=?", 7L);
        order.verify(db).update("DELETE FROM meeting_participant WHERE meeting_id=?", 7L);
        order.verify(db).update("DELETE FROM meeting WHERE id=?", 7L);
    }

    @Test
    void openMeetingCannotBeDeleted() {
        UserContext.set(1L, "ENTERPRISE");
        meeting("OPEN");
        assertThrows(BizException.class, () -> service.delete(7L));
        verify(db, never()).update(anyString(), anyLong());
    }

    @Test
    void participantCannotDeleteAnotherHostsMeeting() {
        UserContext.set(2L, "ENTERPRISE");
        meeting("ENDED");
        when(db.queryForObject(anyString(), eq(Integer.class), eq(7L), eq(2L))).thenReturn(1);
        assertThrows(BizException.class, () -> service.delete(7L));
        verify(db, never()).update(anyString(), anyLong());
    }
}
