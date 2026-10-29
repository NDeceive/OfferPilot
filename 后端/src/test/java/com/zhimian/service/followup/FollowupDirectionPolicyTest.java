package com.zhimian.service.followup;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FollowupDirectionPolicyTest {

    private final FollowupDirectionPolicy policy = new FollowupDirectionPolicy();

    @Test
    void firstLayerPrefersClarify() {
        assertEquals(FollowupDimension.CLARIFY, policy.choose(1, Set.of()));
    }

    @Test
    void rotatesWhenDimensionAlreadyAsked() {
        Set<FollowupDimension> asked = EnumSet.of(FollowupDimension.CLARIFY);
        assertEquals(FollowupDimension.MECHANISM, policy.choose(1, asked));
    }

    @Test
    void deeperLayersMoveToTradeoffAndBoundary() {
        assertEquals(FollowupDimension.TRADEOFF, policy.choose(3, Set.of()));
        assertEquals(FollowupDimension.BOUNDARY, policy.choose(3, EnumSet.of(FollowupDimension.TRADEOFF)));
        assertEquals(FollowupDimension.TRANSFER, policy.choose(4, Set.of()));
    }

    @Test
    void fallsBackWhenEverythingAsked() {
        Set<FollowupDimension> all = EnumSet.of(FollowupDimension.CLARIFY, FollowupDimension.MECHANISM,
                FollowupDimension.TRADEOFF, FollowupDimension.BOUNDARY, FollowupDimension.TRANSFER);
        assertEquals(FollowupDimension.CLARIFY, policy.choose(3, all));
    }
}
