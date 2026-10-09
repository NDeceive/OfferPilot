package com.zhimian.ws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.service.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会议信令 WebSocket（/ws/meeting）：原生 JSON 信令，配合浏览器原生 RTCPeerConnection（mesh）。
 * <p>
 * 协议（顶层平铺，无嵌套 payload）：
 * <ul>
 *   <li>C→S：join{code} / offer|answer|ice{to,...} / mic|cam{on} / leave</li>
 *   <li>S→C：joined{selfId,you,meeting,peers[]} / peer-joined{peer} / peer-left{id} /
 *       peer-state{from,...} / offer|answer|ice{from,...} / advice-new{advice} /
 *       meeting-ended{meetingId} / error{code,message}</li>
 * </ul>
 * {@code from} 一律由服务端写死（忽略客户端传入），杜绝伪造身份。
 * <p>
 * 并发模型：同一 session 的消息由 Spring 串行回调、不同 session 并发。入房/离房涉及
 * 「registry + 三张索引表」的组合变更，统一收敛到本对象的 synchronized 方法；锁内绝不
 * send（close 除外——close 同步触发对端的 afterConnectionClosed 也只是重入本锁，安全）。
 * 发送一律经 {@link ConcurrentWebSocketSessionDecorator}：REST 线程 pushAdvice 与 WS
 * 线程广播可能同时写同一连接，裸 session 会 TEXT_PARTIAL_WRITING 丢帧。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingSignalHandler extends TextWebSocketHandler {

    /** 关闭码（4000-4999 为应用自定义区间），与前端约定一致 */
    public static final int CLOSE_KICKED = 4001;
    public static final int CLOSE_MEETING_ENDED = 4004;
    public static final int CLOSE_ROOM_FULL = 4005;

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    private final MeetingRoomRegistry registry;
    private final MeetingService meetingService;
    private final ObjectMapper json;

    /** sessionId → 已入房状态 */
    private final Map<String, Joined> joinedSessions = new ConcurrentHashMap<>();
    /** meetingId → (userId → 连接)：广播 / 定向转发 / 结束会议用 */
    private final Map<Long, Map<Long, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    /** meetingId → 会议号（registry 以会议号分房） */
    private final Map<Long, String> meetingCodes = new ConcurrentHashMap<>();
    /** sessionId → 并发安全的发送包装 */
    private final Map<String, WebSocketSession> senders = new ConcurrentHashMap<>();

    private record Joined(long meetingId, String code, long userId) {
    }

    /** unregister 结果：确实从名册移除（需要广播 peer-left）时才非空 */
    private record LeaveInfo(long meetingId, long userId) {
    }

    // ---------- 连接生命周期 ----------

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        senders.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WsPrincipal me = (WsPrincipal) session.getAttributes().get(WsPrincipal.ATTR);
        if (me == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        Map<String, Object> msg;
        try {
            msg = json.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception badJson) {
            sendError(session, "BAD_MESSAGE", "消息格式不正确");
            return;
        }
        String type = msg.get("type") instanceof String s ? s : "";
        try {
            switch (type) {
                case "join" -> handleJoin(session, me, msg);
                case "offer", "answer", "ice" -> relayToPeer(session, me, msg);
                case "mic", "cam" -> broadcastState(session, me, msg);
                case "leave" -> handleLeave(session, me);
                default -> sendError(session, "BAD_MESSAGE", "未知消息类型：" + type);
            }
        } catch (Exception e) {
            log.warn("[会议信令] 处理失败 type={} session={}", type, session.getId(), e);
            sendError(session, "BAD_MESSAGE", "消息处理失败，请重试");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        senders.remove(session.getId());
        LeaveInfo info = unregister(session);
        if (info != null) {
            meetingService.markLeft(info.meetingId(), info.userId());
            broadcast(info.meetingId(), info.userId(), Map.of("type", "peer-left", "id", info.userId()));
            log.info("[会议信令] 用户 {} 离开会议 {}", info.userId(), info.meetingId());
        }
    }

    // ---------- 消息处理 ----------

    private void handleJoin(WebSocketSession session, WsPrincipal me, Map<String, Object> msg) {
        if (joinedSessions.containsKey(session.getId())) {
            sendError(session, "BAD_MESSAGE", "已在会议中，请勿重复加入");
            return;
        }
        String rawCode = msg.get("code") instanceof String s ? s : "";
        Map<String, Object> meeting = meetingService.findByCode(rawCode);
        if (meeting == null) {
            sendError(session, "NOT_FOUND", "会议号不存在，请核对后重试");
            return;
        }
        if (!"OPEN".equals(meeting.get("status"))) {
            sendError(session, "MEETING_ENDED", "会议已结束");
            return;
        }
        long meetingId = ((Number) meeting.get("id")).longValue();
        String code = String.valueOf(meeting.get("code"));
        MeetingRoomRegistry.JoinOutcome outcome = registerJoin(session, meetingId, code, me);
        if (outcome == null) {
            sendError(session, "ROOM_FULL", "会议人数已满（最多 " + MeetingRoomRegistry.MAX_PEERS + " 人）");
            closeQuietly(session, new CloseStatus(CLOSE_ROOM_FULL, "房间已满"));
            return;
        }
        meetingService.touchParticipant(meetingId, me.userId(), me.name(), me.role());
        Map<String, Object> you = peerJson(outcome.peer());
        List<Map<String, Object>> peers = registry.peers(code).stream()
                .filter(p -> p.userId() != me.userId())
                .map(this::peerJson)
                .toList();
        Map<String, Object> joined = new LinkedHashMap<>();
        joined.put("type", "joined");
        joined.put("selfId", me.userId());
        joined.put("you", you);
        joined.put("meeting", meetingBrief(meeting));
        joined.put("peers", peers);
        send(session, joined);
        broadcast(meetingId, me.userId(), Map.of("type", "peer-joined", "peer", you));
        log.info("[会议信令] 用户 {}（{}）加入会议 {}，当前 {} 人", me.name(), me.userId(), code, peers.size() + 1);
    }

    /** offer / answer / ice：只投递给指定目标，from 由服务端写死。 */
    private void relayToPeer(WebSocketSession session, WsPrincipal me, Map<String, Object> msg) {
        Joined st = joinedSessions.get(session.getId());
        if (st == null) {
            sendError(session, "NOT_JOINED", "请先加入会议");
            return;
        }
        long to = msg.get("to") instanceof Number n ? n.longValue() : -1;
        if (to <= 0) {
            sendError(session, "BAD_MESSAGE", "缺少消息目标");
            return;
        }
        Map<Long, WebSocketSession> room = roomSessions.get(st.meetingId());
        WebSocketSession target = room == null ? null : room.get(to);
        if (target == null) {
            sendError(session, "PEER_GONE", "对方已离开会议");
            return;
        }
        Map<String, Object> relay = new LinkedHashMap<>(msg);
        relay.remove("to");
        relay.put("from", me.userId());
        sendRaw(target, serialize(relay));
    }

    /** mic / cam 开关：广播给房内其他人（不触发重协商，对方只换图标）。 */
    private void broadcastState(WebSocketSession session, WsPrincipal me, Map<String, Object> msg) {
        Joined st = joinedSessions.get(session.getId());
        if (st == null) {
            sendError(session, "NOT_JOINED", "请先加入会议");
            return;
        }
        boolean on = Boolean.TRUE.equals(msg.get("on"));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("type", "peer-state");
        state.put("id", me.userId());
        if ("mic".equals(msg.get("type"))) {
            state.put("micOn", on);
        } else {
            state.put("camOn", on);
        }
        broadcast(st.meetingId(), me.userId(), state);
    }

    private void handleLeave(WebSocketSession session, WsPrincipal me) {
        LeaveInfo info = unregister(session);
        if (info != null) {
            meetingService.markLeft(info.meetingId(), info.userId());
            broadcast(info.meetingId(), info.userId(), Map.of("type", "peer-left", "id", info.userId()));
        }
        closeQuietly(session, CloseStatus.NORMAL);
    }

    // ---------- 名册维护（同步块：组合变更 + 顶替判定） ----------

    /** 入房：顶替同账号旧连接（close 4001）；房间满返回 null。 */
    private synchronized MeetingRoomRegistry.JoinOutcome registerJoin(WebSocketSession session, long meetingId,
                                                                     String code, WsPrincipal me) {
        MeetingRoomRegistry.JoinOutcome outcome = registry.join(code, me.userId(), me.name(), me.role());
        if (outcome == null) {
            return null;
        }
        Map<Long, WebSocketSession> room = roomSessions.computeIfAbsent(meetingId, k -> new ConcurrentHashMap<>());
        WebSocketSession stale = room.get(me.userId());
        if (stale != null && stale != session) {
            room.remove(me.userId(), stale);
            closeQuietly(stale, new CloseStatus(CLOSE_KICKED, "账号已在其他窗口进入会议"));
        }
        room.put(me.userId(), session);
        meetingCodes.put(meetingId, code);
        joinedSessions.put(session.getId(), new Joined(meetingId, code, me.userId()));
        return outcome;
    }

    /**
     * 离房：只有索引仍指向本连接才算真正离开（需要广播）；被新连接顶替的旧连接静默退出。
     * 返回 null 表示无需广播（未入房 / 已被顶替 / 会议已被 drain）。
     */
    private synchronized LeaveInfo unregister(WebSocketSession session) {
        Joined st = joinedSessions.remove(session.getId());
        if (st == null) {
            return null;
        }
        Map<Long, WebSocketSession> room = roomSessions.get(st.meetingId());
        if (room != null) {
            boolean current = room.remove(st.userId(), session);
            if (room.isEmpty()) {
                roomSessions.remove(st.meetingId(), room);
            }
            if (!current) {
                return null;
            }
        }
        MeetingRoomRegistry.Peer left = registry.leave(st.code(), st.userId());
        return left == null ? null : new LeaveInfo(st.meetingId(), st.userId());
    }

    // ---------- REST 线程调用（建议推送 / 结束会议） ----------

    /** 新建议推送给房内：定向建议只发给目标与作者（作者用于单播回显）。 */
    public void pushAdvice(long meetingId, Map<String, Object> advice) {
        Map<Long, WebSocketSession> room = roomSessions.get(meetingId);
        if (room == null || room.isEmpty()) {
            return;
        }
        Number target = (Number) advice.get("targetUserId");
        Number author = (Number) advice.get("authorId");
        String payload = serialize(Map.of("type", "advice-new", "advice", advice));
        room.forEach((userId, session) -> {
            if (target == null || target.longValue() == userId
                    || (author != null && author.longValue() == userId)) {
                sendRaw(session, payload);
            }
        });
    }

    /** 会议结束：广播 meeting-ended 后关闭全部连接（各连接的 afterConnectionClosed 幂等兜底）。 */
    public void pushMeetingEnded(long meetingId) {
        String code = meetingCodes.remove(meetingId);
        if (code != null) {
            registry.drain(code);
        }
        Map<Long, WebSocketSession> room = roomSessions.remove(meetingId);
        if (room == null || room.isEmpty()) {
            return;
        }
        String payload = serialize(Map.of("type", "meeting-ended", "meetingId", meetingId));
        room.forEach((userId, session) -> {
            sendRaw(session, payload);
            closeQuietly(session, new CloseStatus(CLOSE_MEETING_ENDED, "会议已结束"));
        });
        log.info("[会议信令] 会议 {} 已结束，关闭 {} 个连接", meetingId, room.size());
    }

    // ---------- 发送与序列化 ----------

    private void broadcast(long meetingId, long excludeUserId, Map<String, Object> message) {
        Map<Long, WebSocketSession> room = roomSessions.get(meetingId);
        if (room == null || room.isEmpty()) {
            return;
        }
        String payload = serialize(message);
        room.forEach((userId, session) -> {
            if (userId != excludeUserId) {
                sendRaw(session, payload);
            }
        });
    }

    private void send(WebSocketSession session, Map<String, Object> message) {
        sendRaw(session, serialize(message));
    }

    private void sendError(WebSocketSession session, String code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "error");
        out.put("code", code);
        out.put("message", message);
        send(session, out);
    }

    private String serialize(Map<String, Object> message) {
        try {
            return json.writeValueAsString(message);
        } catch (Exception e) {
            log.warn("[会议信令] 序列化失败", e);
            return "{\"type\":\"error\",\"code\":\"BAD_MESSAGE\",\"message\":\"消息序列化失败\"}";
        }
    }

    private void sendRaw(WebSocketSession session, String payload) {
        WebSocketSession sender = senders.getOrDefault(session.getId(), session);
        try {
            sender.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            // 对方断开等场景：静默，其 afterConnectionClosed 会完成清理
            log.debug("[会议信令] 发送失败 session={} err={}", session.getId(), e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("[会议信令] 关闭失败 session={} err={}", session.getId(), e.getMessage());
        }
    }

    private Map<String, Object> peerJson(MeetingRoomRegistry.Peer p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.userId());
        out.put("name", p.name());
        out.put("role", p.role());
        out.put("seq", p.seq());
        return out;
    }

    private Map<String, Object> meetingBrief(Map<String, Object> meeting) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", meeting.get("id"));
        out.put("code", meeting.get("code"));
        out.put("codeDisplay", MeetingService.displayCode(String.valueOf(meeting.get("code"))));
        out.put("title", meeting.get("title"));
        out.put("status", meeting.get("status"));
        out.put("hostId", meeting.get("hostId"));
        out.put("hostName", meeting.get("hostName"));
        out.put("jobId", meeting.get("jobId"));
        return out;
    }
}
