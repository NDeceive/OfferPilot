package com.zhimian.service.followup;

import lombok.Getter;

/**
 * 追问闸门的判定结果。
 */
@Getter
public class GateDecision {

    public static final String ASK = "ASK";
    public static final String STOP_DISABLED = "STOP_DISABLED";
    public static final String STOP_DEPTH = "STOP_DEPTH";
    public static final String STOP_NO_TIME = "STOP_NO_TIME";
    public static final String STOP_NO_NEED = "STOP_NO_NEED";
    public static final String STOP_NOT_SAMPLED = "STOP_NOT_SAMPLED";

    private final boolean ask;
    private final String gate;
    private final double probability;
    private final double quality;

    private GateDecision(boolean ask, String gate, double probability, double quality) {
        this.ask = ask;
        this.gate = gate;
        this.probability = probability;
        this.quality = quality;
    }

    public static GateDecision ask(double probability, double quality) {
        return new GateDecision(true, ASK, probability, quality);
    }

    public static GateDecision stop(String gate, double probability, double quality) {
        return new GateDecision(false, gate, probability, quality);
    }
}
