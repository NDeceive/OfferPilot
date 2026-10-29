package com.zhimian.service.followup;

/**
 * 追问方向。前五个是常规轮转维度，CORRECT 是纠错支线。
 */
public enum FollowupDimension {

    CLARIFY("补全澄清"),
    MECHANISM("原理机制"),
    TRADEOFF("权衡取舍"),
    BOUNDARY("边界故障"),
    TRANSFER("迁移泛化"),
    CORRECT("纠错");

    private final String label;

    FollowupDimension(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 兼容模型输出的代码；无法识别时返回 null，由调用方回退到策略选定的方向 */
    public static FollowupDimension fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (FollowupDimension d : values()) {
            if (d.name().equals(normalized) || d.label.equals(code.trim())) {
                return d;
            }
        }
        return null;
    }
}
