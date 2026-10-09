package com.zhimian.ws;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeetingRoomRegistryTest {

    @Test
    void joinAssignsMonotonicSeqAndKeepsArrivalOrder() {
        var registry = new MeetingRoomRegistry();
        var a = registry.join("ROOM1", 1L, "甲", "ENTERPRISE");
        var b = registry.join("ROOM1", 2L, "乙", "STUDENT");
        assertEquals(1L, a.peer().seq());
        assertEquals(2L, b.peer().seq());
        assertNull(a.evicted());
        assertEquals(List.of(1L, 2L),
                registry.peers("ROOM1").stream().map(MeetingRoomRegistry.Peer::userId).toList());
    }

    @Test
    void rejoiningSameUserEvictsOldPeerAndNeverReusesSeq() {
        var registry = new MeetingRoomRegistry();
        registry.join("ROOM1", 1L, "甲", "ENTERPRISE");
        var again = registry.join("ROOM1", 1L, "甲改名", "ENTERPRISE");
        assertNotNull(again.evicted());
        assertEquals(1L, again.evicted().userId());
        assertEquals(2L, again.peer().seq(), "seq 必须单调递增，复用会让两端同时发 offer（glare）");
        assertEquals(1, registry.size("ROOM1"));
        assertEquals("甲改名", registry.peers("ROOM1").get(0).name());
    }

    @Test
    void roomFullRejectsNewcomerButAcceptsRejoinOfExistingMember() {
        var registry = new MeetingRoomRegistry();
        for (long i = 1; i <= MeetingRoomRegistry.MAX_PEERS; i++) {
            registry.join("ROOM1", i, "u" + i, "STUDENT");
        }
        assertNull(registry.join("ROOM1", 99L, "溢出", "STUDENT"), "满房必须拒绝新成员");
        assertNotNull(registry.join("ROOM1", 3L, "老成员重进", "STUDENT"), "在房账号重进出不算新增");
        assertEquals(MeetingRoomRegistry.MAX_PEERS, registry.size("ROOM1"));
    }

    @Test
    void leaveRemovesPeerAndDropsEmptyRoom() {
        var registry = new MeetingRoomRegistry();
        registry.join("ROOM1", 1L, "甲", "STUDENT");
        assertEquals(1L, registry.leave("ROOM1", 1L).userId());
        assertEquals(0, registry.size("ROOM1"));
        assertNull(registry.leave("ROOM1", 1L), "空房已被移除，重复离房应安全返回 null");
        assertNull(registry.leave("NOPE", 1L));
    }

    @Test
    void drainReturnsAllMembersAndLeavesOtherRoomsIntact() {
        var registry = new MeetingRoomRegistry();
        registry.join("ROOM1", 1L, "甲", "STUDENT");
        registry.join("ROOM1", 2L, "乙", "STUDENT");
        registry.join("ROOM2", 3L, "丙", "STUDENT");
        var drained = registry.drain("ROOM1");
        assertEquals(2, drained.size());
        assertEquals(0, registry.size("ROOM1"));
        assertEquals(1, registry.size("ROOM2"));
        assertEquals(List.of(), registry.drain("ROOM1"), "重复 drain 安全返回空");
    }

    @Test
    void roomsAreIsolatedByCode() {
        var registry = new MeetingRoomRegistry();
        registry.join("AAAA", 1L, "甲", "STUDENT");
        registry.join("BBBB", 2L, "乙", "STUDENT");
        assertEquals(1, registry.size("AAAA"));
        assertEquals(1, registry.size("BBBB"));
        assertNull(registry.leave("AAAA", 2L), "跨房间 leave 不能命中");
    }
}
