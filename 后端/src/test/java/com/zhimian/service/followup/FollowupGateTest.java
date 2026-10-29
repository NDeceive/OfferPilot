package com.zhimian.service.followup;

import com.zhimian.config.FollowUpProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowupGateTest {

    private FollowupGate gate() {
        return new FollowupGate(new FollowUpProperties());
    }

    private static final String LONG_ANSWER =
            "这里用了本地缓存加互斥锁，因为缓存击穿时热点 key 会同时回源，"
            + "所以我用分布式锁限制只有一个请求回源，其余等待后重试，代价是首字节延迟变高，"
            + "边界情况是锁超时要降级，我加了 3 秒超时和兜底旧值。";

    @Test
    void probabilityDecaysWithLayer() {
        FollowupGate g = gate();
        double p1 = g.decide(1L, 2L, 1, 3, 600, LONG_ANSWER, false).getProbability();
        double p2 = g.decide(1L, 2L, 2, 3, 600, LONG_ANSWER, false).getProbability();
        double p3 = g.decide(1L, 2L, 3, 3, 600, LONG_ANSWER, false).getProbability();

        assertTrue(p1 > p2 && p2 > p3, "层数越深，条件概率应当越低");
        assertTrue(p1 > 0.70 && p1 < 0.90, "首层概率应落在质量修正区间内");
    }

    @Test
    void depthLimitFollowsDifficulty() {
        FollowupGate g = gate();
        assertEquals(GateDecision.STOP_DEPTH, g.decide(1L, 2L, 3, 1, 600, LONG_ANSWER, false).getGate());
        assertTrue(g.decide(1L, 2L, 3, 3, 600, LONG_ANSWER, false).getProbability() > 0);
    }

    @Test
    void stopsWhenTimeIsShort() {
        FollowupGate g = gate();
        assertEquals(GateDecision.STOP_NO_TIME, g.decide(1L, 2L, 1, 3, 10, LONG_ANSWER, false).getGate());
    }

    @Test
    void thinnerAnswerRaisesProbability() {
        FollowupGate g = gate();
        double thin = g.decide(1L, 2L, 1, 3, 600, "缓存击穿用互斥锁。", false).getProbability();
        double rich = g.decide(1L, 2L, 1, 3, 600, LONG_ANSWER, false).getProbability();
        assertTrue(thin > rich, "回答越空，越应该继续追问");
    }

    @Test
    void samplingIsDeterministic() {
        FollowupGate g = gate();
        boolean first = g.decide(9L, 77L, 2, 3, 600, LONG_ANSWER, false).isAsk();
        for (int i = 0; i < 20; i++) {
            assertEquals(first, g.decide(9L, 77L, 2, 3, 600, LONG_ANSWER, false).isAsk());
        }
    }

    @Test
    void contentGateStopsFromSecondLayer() {
        FollowupGate g = gate();
        assertEquals(GateDecision.STOP_NO_NEED, g.decide(1L, 2L, 2, 3, 600, LONG_ANSWER, true).getGate());
    }
}
