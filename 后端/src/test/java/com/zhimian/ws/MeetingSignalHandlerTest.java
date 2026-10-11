package com.zhimian.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.service.MeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 信令处理端到端（mock 连接层，真 registry）：入房 / 转发 / from 防伪 / 踢旧 / 满房 / 结束会议。
 */
class MeetingSignalHandlerTest {

    private static final String CODE = "ABCDEFGH";

    private final ObjectMapper json = new ObjectMapper();
    private MeetingRoomRegistry registry;
    private MeetingService meetingService;
    private MeetingSignalHandler handler;
    private int sessionSeq = 0;

    @BeforeEach
    void setUp() {
        registry = new MeetingRoomRegistry();
        meetingService = mock(MeetingService.class);
        when(meetingService.findByCode(CODE)).thenReturn(Map.of(
                "id", 7L, "code", CODE, "title", "演示会议", "status", "OPEN",
                "hostId", 1L, "hostName", "企业甲"));
        // Mockito 对 Map 返回类型默认给空 Map（不是 null），必须显式声明不存在
        when(meetingService.findByCode("ZZZZZZZZ")).thenReturn(null);
        handler = new MeetingSignalHandler(registry, meetingService, json);
    }

    // ---------- 工具 ----------

    private WebSocketSession session(long userId, String name, String role) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn("sess-" + (++sessionSeq));
        when(s.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(WsPrincipal.ATTR, new WsPrincipal(userId, name, role));
        when(s.getAttributes()).thenReturn(attrs);
        handler.afterConnectionEstablished(s);
        return s;
    }

    private void send(WebSocketSession from, String payload) {
        handler.handleTextMessage(from, new TextMessage(payload));
    }

    private List<Map<String, Object>> sent(WebSocketSession session) throws IOException {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeast(0)).sendMessage(captor.capture());
        List<Map<String, Object>> out = new ArrayList<>();
        for (TextMessage m : captor.getAllValues()) {
            out.add(json.readValue(m.getPayload(), Map.class));
        }
        return out;
    }

    private Map<String, Object> lastOfType(WebSocketSession session, String type) throws IOException {
        Map<String, Object> found = null;
        for (Map<String, Object> m : sent(session)) {
            if (type.equals(m.get("type"))) {
                found = m;
            }
        }
        return found;
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private static String join(String code) {
        return "{\"type\":\"join\",\"code\":\"" + code + "\"}";
    }

    // ---------- 用例 ----------

    @Test
    void joinSendsJoinedPayloadThenBroadcastsPeerJoined() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        WebSocketSession b = session(2L, "学生乙", "STUDENT");

        send(a, join(CODE));
        Map<String, Object> joinedA = lastOfType(a, "joined");
        assertNotNull(joinedA);
        assertEquals(1L, asLong(joinedA.get("selfId")));
        assertTrue(((List<?>) joinedA.get("peers")).isEmpty());
        assertNotNull(joinedA.get("meeting"));

        send(b, join(CODE));
        Map<String, Object> joinedB = lastOfType(b, "joined");
        List<?> peers = (List<?>) joinedB.get("peers");
        assertEquals(1, peers.size());
        assertEquals(1L, asLong(((Map<?, ?>) peers.get(0)).get("id")));
        assertEquals(1L, asLong(((Map<?, ?>) peers.get(0)).get("seq")));
        // A 收到 peer-joined（用于决定谁发 offer）
        Map<String, Object> peerJoined = lastOfType(a, "peer-joined");
        assertNotNull(peerJoined);
        assertEquals(2L, asLong(((Map<?, ?>) peerJoined.get("peer")).get("id")));
        assertEquals(2L, asLong(((Map<?, ?>) peerJoined.get("peer")).get("seq")));
    }

    @Test
    void relayRewritesFromAndTargetsOnlyRecipient() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        WebSocketSession b = session(2L, "学生乙", "STUDENT");
        WebSocketSession c = session(3L, "学生丙", "STUDENT");
        for (WebSocketSession s : List.of(a, b, c)) {
            send(s, join(CODE));
        }

        send(a, "{\"type\":\"offer\",\"to\":2,\"from\":999,\"sdp\":{\"type\":\"offer\",\"sdp\":\"fake\"}}");
        Map<String, Object> offered = lastOfType(b, "offer");
        assertNotNull(offered);
        assertEquals(1L, asLong(offered.get("from")), "伪造的 from 必须被服务端覆盖");
        assertNull(offered.get("to"), "to 不下发给接收方");
        assertTrue(offered.containsKey("sdp"), "业务字段原样透传");
        assertNull(lastOfType(c, "offer"), "只投递给目标，不能广播");
    }

    @Test
    void unknownTargetReturnsPeerGone() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        send(a, join(CODE));
        send(a, "{\"type\":\"ice\",\"to\":42,\"candidate\":null}");
        assertEquals("PEER_GONE", lastOfType(a, "error").get("code"));
    }

    @Test
    void signalingBeforeJoinReturnsNotJoined() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        send(a, "{\"type\":\"offer\",\"to\":1,\"sdp\":{}}");
        assertEquals("NOT_JOINED", lastOfType(a, "error").get("code"));
    }

    @Test
    void unknownMeetingCodeReturnsNotFound() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        send(a, join("ZZZZZZZZ"));
        assertEquals("NOT_FOUND", lastOfType(a, "error").get("code"));
    }

    @Test
    void micStateBroadcastCarriesServerSideFrom() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        WebSocketSession b = session(2L, "学生乙", "STUDENT");
        send(a, join(CODE));
        send(b, join(CODE));

        send(a, "{\"type\":\"mic\",\"on\":false}");
        Map<String, Object> state = lastOfType(b, "peer-state");
        assertNotNull(state);
        assertEquals(1L, asLong(state.get("id")), "状态以服务端身份广播");
        assertEquals(Boolean.FALSE, state.get("micOn"));
        assertNull(state.get("camOn"), "mic 消息只带 micOn");
        assertNull(lastOfType(a, "peer-state"), "状态只广播给其他人");

        send(b, "{\"type\":\"cam\",\"on\":false}");
        Map<String, Object> camState = lastOfType(a, "peer-state");
        assertNotNull(camState);
        assertEquals(2L, asLong(camState.get("id")));
        assertEquals(Boolean.FALSE, camState.get("camOn"));
    }

    @Test
    void sameUserSecondJoinKicksFirstConnectionWith4001() throws IOException {
        WebSocketSession first = session(1L, "企业甲", "ENTERPRISE");
        WebSocketSession second = session(1L, "企业甲", "ENTERPRISE");
        send(first, join(CODE));
        send(second, join(CODE));

        ArgumentCaptor<CloseStatus> closed = ArgumentCaptor.forClass(CloseStatus.class);
        verify(first).close(closed.capture());
        assertEquals(MeetingSignalHandler.CLOSE_KICKED, closed.getValue().getCode());
        assertEquals(1, registry.size(CODE), "同账号只占一个名册位");
        assertNotNull(lastOfType(second, "joined"));
        assertNull(lastOfType(first, "peer-left"), "顶替不广播离场（由 peer-joined 替换即可）");
    }

    @Test
    void roomFullSeventhPeerIsRejectedWith4005() throws IOException {
        for (long i = 1; i <= MeetingRoomRegistry.MAX_PEERS; i++) {
            send(session(i, "u" + i, "STUDENT"), join(CODE));
        }
        WebSocketSession late = session(99L, "迟到者", "STUDENT");
        send(late, join(CODE));

        assertEquals("ROOM_FULL", lastOfType(late, "error").get("code"));
        ArgumentCaptor<CloseStatus> closed = ArgumentCaptor.forClass(CloseStatus.class);
        verify(late).close(closed.capture());
        assertEquals(MeetingSignalHandler.CLOSE_ROOM_FULL, closed.getValue().getCode());
        assertEquals(MeetingRoomRegistry.MAX_PEERS, registry.size(CODE));
    }

    @Test
    void meetingEndedBroadcastClosesEveryoneWith4004AndDrainsRoom() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        WebSocketSession b = session(2L, "学生乙", "STUDENT");
        send(a, join(CODE));
        send(b, join(CODE));

        handler.pushMeetingEnded(7L);

        assertEquals(7L, asLong(lastOfType(a, "meeting-ended").get("meetingId")));
        assertEquals(7L, asLong(lastOfType(b, "meeting-ended").get("meetingId")));
        ArgumentCaptor<CloseStatus> closed = ArgumentCaptor.forClass(CloseStatus.class);
        verify(a).close(closed.capture());
        assertEquals(MeetingSignalHandler.CLOSE_MEETING_ENDED, closed.getValue().getCode());
        assertEquals(0, registry.size(CODE), "结束会议必须 drain 名册");
    }

    @Test
    void advicePushDeliversToEveryoneWhenOpenToAll() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        WebSocketSession b = session(2L, "学生乙", "STUDENT");
        send(a, join(CODE));
        send(b, join(CODE));

        Map<String, Object> advice = new HashMap<>();
        advice.put("id", 11L);
        advice.put("meetingId", 7L);
        advice.put("authorId", 1L);
        advice.put("authorName", "企业甲");
        advice.put("targetUserId", null);
        advice.put("content", "表达可以更聚焦项目成果");
        handler.pushAdvice(7L, advice);

        assertNotNull(lastOfType(a, "advice-new"));
        assertNotNull(lastOfType(b, "advice-new"));
    }

    @Test
    void targetedAdviceSkipsOtherStudents() throws IOException {
        WebSocketSession a = session(1L, "企业甲", "ENTERPRISE");
        WebSocketSession b = session(2L, "学生乙", "STUDENT");
        WebSocketSession c = session(3L, "学生丙", "STUDENT");
        for (WebSocketSession s : List.of(a, b, c)) {
            send(s, join(CODE));
        }

        Map<String, Object> advice = new HashMap<>();
        advice.put("id", 12L);
        advice.put("meetingId", 7L);
        advice.put("authorId", 1L);
        advice.put("authorName", "企业甲");
        advice.put("targetUserId", 2L);
        advice.put("content", "「学生乙」的自我介绍建议");
        handler.pushAdvice(7L, advice);

        assertNotNull(lastOfType(b, "advice-new"), "目标学生必须收到");
        assertNotNull(lastOfType(a, "advice-new"), "作者收到回显");
        assertNull(lastOfType(c, "advice-new"), "定向建议不能串给其他学生");
    }
}
