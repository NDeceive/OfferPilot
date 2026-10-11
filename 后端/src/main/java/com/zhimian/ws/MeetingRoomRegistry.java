package com.zhimian.ws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存态会议房间名册：谁在哪个会议里、进入顺序号（seq）。
 * <p>
 * 纯逻辑、不依赖 Spring，便于单测。演示规模（单机、个位数房间）下直接用对象级
 * synchronized 粗粒度锁：正确性优先，锁内只做 Map 操作、绝不调 send，不会成为瓶颈。
 * <p>
 * seq 房间内单调递增、永不回收：前端约定「seq 大的一方向 seq 小的发 offer」，
 * 复用旧 seq 会让双方同时发起（glare）。服务重启名册即清空——房间状态以重新
 * join 为准（演示场景可接受，不做持久化恢复）。
 */
public class MeetingRoomRegistry {

    /** 单房间上限：mesh 全互联，人数越多每人上行越吃紧，6 人封顶 */
    public static final int MAX_PEERS = 6;

    /** 房间内成员快照。seq 为进入顺序号，入房时分配、终生不变。 */
    public record Peer(long userId, String name, String role, long seq) {
    }

    /** join 结果：新 peer + 被顶替的旧 peer（同账号重复进入时非空，用于踢旧连接） */
    public record JoinOutcome(Peer peer, Peer evicted) {
    }

    private static final class Room {
        final Map<Long, Peer> peers = new LinkedHashMap<>();
        long nextSeq = 1;
    }

    private final Map<String, Room> rooms = new HashMap<>();

    /**
     * 入房。房间已满（>= {@link #MAX_PEERS} 且本账号不在房内）返回 {@code null}；
     * 同一 userId 再次入房：顶替旧 peer 并放入 {@link JoinOutcome#evicted()}。
     */
    public synchronized JoinOutcome join(String code, long userId, String name, String role) {
        Room room = rooms.computeIfAbsent(code, k -> new Room());
        boolean already = room.peers.containsKey(userId);
        if (!already && room.peers.size() >= MAX_PEERS) {
            return null;
        }
        Peer evicted = room.peers.remove(userId);
        Peer peer = new Peer(userId, name, role, room.nextSeq++);
        room.peers.put(userId, peer);
        return new JoinOutcome(peer, evicted);
    }

    /** 离房。返回被移除的 peer；不在房内返回 null。房间空了即移除（懒清理）。 */
    public synchronized Peer leave(String code, long userId) {
        Room room = rooms.get(code);
        if (room == null) {
            return null;
        }
        Peer removed = room.peers.remove(userId);
        if (room.peers.isEmpty()) {
            rooms.remove(code);
        }
        return removed;
    }

    /** 房间当前成员快照（含自己），按进入顺序。 */
    public synchronized List<Peer> peers(String code) {
        Room room = rooms.get(code);
        return room == null ? List.of() : new ArrayList<>(room.peers.values());
    }

    public synchronized int size(String code) {
        Room room = rooms.get(code);
        return room == null ? 0 : room.peers.size();
    }

    /** 结束会议：清空房间名册并返回全部成员（供调用方逐个关闭连接/广播）。 */
    public synchronized List<Peer> drain(String code) {
        Room room = rooms.remove(code);
        if (room == null) {
            return List.of();
        }
        List<Peer> all = new ArrayList<>(room.peers.values());
        room.peers.clear();
        return all;
    }
}
