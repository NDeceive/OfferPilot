package com.zhimian.service.followup;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 追问方向策略：按层数先验与「已问维度」轮转，避免连续两问同一个角度。
 */
@Component
public class FollowupDirectionPolicy {

    /** 层数先验：越深越往权衡、边界、迁移走 */
    public Map<FollowupDimension, Double> prior(int layer) {
        Map<FollowupDimension, Double> m = new LinkedHashMap<>();
        switch (Math.max(1, Math.min(4, layer))) {
            case 1 -> {
                m.put(FollowupDimension.CLARIFY, 0.60);
                m.put(FollowupDimension.MECHANISM, 0.40);
            }
            case 2 -> {
                m.put(FollowupDimension.MECHANISM, 0.55);
                m.put(FollowupDimension.TRADEOFF, 0.45);
            }
            case 3 -> {
                m.put(FollowupDimension.TRADEOFF, 0.55);
                m.put(FollowupDimension.BOUNDARY, 0.45);
            }
            default -> {
                m.put(FollowupDimension.TRANSFER, 0.60);
                m.put(FollowupDimension.BOUNDARY, 0.40);
            }
        }
        return m;
    }

    /**
     * 选方向：先按先验排序，跳过已经问过的维度；全问过时退回补全澄清。
     */
    public FollowupDimension choose(int layer, Set<FollowupDimension> asked) {
        FollowupDimension best = null;
        double bestScore = -1;
        for (Map.Entry<FollowupDimension, Double> e : prior(layer).entrySet()) {
            if (e.getKey() == FollowupDimension.CORRECT) {
                continue;
            }
            if (asked != null && asked.contains(e.getKey())) {
                continue;
            }
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }
        return best == null ? FollowupDimension.CLARIFY : best;
    }
}
