package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多层追问配置，绑定 application.yml 的 interview.followup.* 节点。
 * <p>
 * 概率模型：第 k 层的条件触发概率 p_k = clamp(p0 * gamma^(k-1) * phi, 0.02, 0.98)，
 * 其中 phi 由当轮回答质量修正（答得空提高、答得满降低）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "interview.followup")
public class FollowUpProperties {

    /** 总开关；关闭后退回单层追问的旧行为 */
    private boolean enabled = true;

    /** 首层触发概率 */
    private double p0 = 0.90;

    /** 每深一层的衰减系数 */
    private double gamma = 0.55;

    /** 全局层数上限 */
    private int maxDepth = 4;

    /** 按难度（1/2/3）给出的层数上限，逗号分隔 */
    private String depthByDifficulty = "2,3,4";

    /** 剩余时间低于该值时不再追问 */
    private int minTimeLeftSeconds = 90;

    /** 回答质量最低时的修正系数（质量越低，越倾向继续追问） */
    private double qualityMin = 0.70;

    /** 回答质量最高时的修正系数 */
    private double qualityMax = 1.30;

    /** 抽样种子的固定盐值，保证同一场重放结果一致 */
    private String seedSalt = "offerpilot";

    /** 按难度取层数上限：难度 1 -> 2 层，2 -> 3 层，3 -> 4 层，并受全局上限约束 */
    public int maxDepthFor(int difficulty) {
        int idx = Math.max(1, Math.min(3, difficulty)) - 1;
        int depth = maxDepth;
        String[] parts = depthByDifficulty == null ? new String[0] : depthByDifficulty.split(",");
        if (idx < parts.length) {
            try {
                depth = Integer.parseInt(parts[idx].trim());
            } catch (NumberFormatException ignored) {
                // 配置写坏时退回全局上限
            }
        }
        return Math.max(1, Math.min(maxDepth, depth));
    }
}
