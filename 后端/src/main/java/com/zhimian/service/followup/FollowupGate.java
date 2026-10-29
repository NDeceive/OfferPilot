package com.zhimian.service.followup;

import com.zhimian.config.FollowUpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 多层追问闸门：决定「这一层还问不问」。
 * <p>
 * 顺序：总开关 -> 层数 -> 剩余时间 -> 概率抽样。任一条不通过都直接进入下一题，不调用大模型。
 * 抽样使用 (sessionId, questionId, layer, salt) 的确定性哈希，同一场重放结果一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowupGate {

    private static final String[] MECHANISM_WORDS = {
            "因为", "所以", "原理", "底层", "机制", "实现", "流程", "源码", "线程", "内存", "索引", "协议"
    };
    private static final String[] TRADEOFF_WORDS = {
            "但是", "不过", "权衡", "代价", "取舍", "成本", "性能", "折中", "相比", "优势", "局限"
    };
    private static final String[] BOUNDARY_WORDS = {
            "异常", "边界", "极端", "降级", "容错", "超时", "并发", "失效", "兜底", "限制"
    };

    private final FollowUpProperties props;

    /**
     * @param layer          候选层号，从 1 开始
     * @param difficulty     题目难度 1..3
     * @param remaining      剩余面试秒数
     * @param answer         本轮考生回答
     * @param uncoveredEmpty 上一轮模型回报「已无未覆盖要点」时为 true
     */
    public GateDecision decide(Long sessionId, Long questionId, int layer, int difficulty,
                               int remaining, String answer, boolean uncoveredEmpty) {
        if (!props.isEnabled()) {
            return GateDecision.stop(GateDecision.STOP_DISABLED, 0, 0);
        }
        if (layer > props.maxDepthFor(difficulty)) {
            return GateDecision.stop(GateDecision.STOP_DEPTH, 0, 0);
        }
        if (remaining < props.getMinTimeLeftSeconds()) {
            return GateDecision.stop(GateDecision.STOP_NO_TIME, 0, 0);
        }

        double quality = answerQuality(answer);
        double p = props.getP0() * Math.pow(props.getGamma(), layer - 1) * qualityFactor(quality);
        p = Math.max(0.02, Math.min(0.98, p));

        // 第二层起，若模型判断已无未覆盖要点，直接收手
        if (layer >= 2 && uncoveredEmpty) {
            return GateDecision.stop(GateDecision.STOP_NO_NEED, p, quality);
        }

        double u = unitHash(sessionId, questionId, layer, props.getSeedSalt());
        if (u >= p) {
            return GateDecision.stop(GateDecision.STOP_NOT_SAMPLED, p, quality);
        }
        return GateDecision.ask(p, quality);
    }

    /** 回答质量 0..1：长度、机制词、权衡词、边界词与量化数字的加权 */
    public double answerQuality(String answer) {
        String text = answer == null ? "" : answer.trim();
        if (text.isEmpty()) {
            return 0;
        }
        double lenScore = Math.min(1.0, text.length() / 260.0);
        int signals = hits(text, MECHANISM_WORDS) + hits(text, TRADEOFF_WORDS) + hits(text, BOUNDARY_WORDS);
        double signalScore = Math.min(1.0, signals / 4.0);
        int numbers = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(text);
        while (m.find()) {
            numbers++;
        }
        double numScore = Math.min(1.0, numbers / 3.0);
        return Math.max(0, Math.min(1, 0.55 * lenScore + 0.35 * signalScore + 0.10 * numScore));
    }

    /** 质量越低，phi 越大（越倾向继续追问） */
    private double qualityFactor(double quality) {
        double q = Math.max(0, Math.min(1, quality));
        double phi = props.getQualityMax() - (props.getQualityMax() - props.getQualityMin()) * q;
        return Math.max(props.getQualityMin(), Math.min(props.getQualityMax(), phi));
    }

    private int hits(String text, String[] words) {
        int n = 0;
        for (String w : words) {
            if (text.contains(w)) {
                n++;
            }
        }
        return n;
    }

    /** FNV-1a，跨进程稳定，不随 JVM 变化 */
    static double unitHash(Long sessionId, Long questionId, int layer, String salt) {
        String s = (salt == null ? "" : salt) + "|" + sessionId + "|" + questionId + "|" + layer;
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        long v = (h >>> 11) & 0x7fffffffffffffffL;
        return (v % 1000003L) / 1000003.0;
    }
}
