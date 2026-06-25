package com.zhimian.service;

import com.zhimian.dto.FollowUpResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 规则化追问生成器（无状态版本），作为 AI 不可用 / 调用失败 / 回答过短时的兜底。
 * <p>
 * 仅依赖岗位 / 原始问题 / 回答三个字符串，不触碰题库与会话，
 * 与 {@link InterviewFlowService} 内基于 Question 的规则逻辑相互独立、互不影响。
 * 返回的 {@link FollowUpResponse} 固定 source = RULE。
 */
@Component
public class RuleBasedFollowUpGenerator {

    /** 回答（去空白后）短于该长度视为过短，直接走规则兜底 */
    public static final int MIN_ANSWER_LENGTH = 15;

    private static final String SOURCE_RULE = "RULE";

    /** 含糊词：出现则追问其确定性 */
    private static final List<String> VAGUE_WORDS =
            Arrays.asList("大概", "可能", "应该", "不太清楚", "不知道", "不清楚", "忘了", "不会", "没用过", "不了解");

    /** 项目信号词：缺失则追问项目落地细节 */
    private static final List<String> PROJECT_SIGNALS =
            Arrays.asList("项目", "负责", "实现", "实践", "经历", "做过", "系统", "搭建", "设计");

    /** 常见技术名词词典：命中后可在追问中具体引用，使兜底问题更自然、更贴合回答 */
    private static final List<String> TECH_TERMS = Arrays.asList(
            "Redis", "MySQL", "Kafka", "RabbitMQ", "Spring Boot", "SpringBoot", "Spring", "MyBatis",
            "Elasticsearch", "Docker", "Kubernetes", "Nginx", "线程池", "缓存", "分布式",
            "消息队列", "事务", "索引", "JVM", "微服务", "高并发", "锁");

    /**
     * 生成规则化追问。命中的规则同时决定追问文案与 triggerReason。
     *
     * @param tooShort 调用方是否已判定回答过短（用于优先给出“过短”原因）
     */
    public FollowUpResponse generate(String position, String question, String answer, boolean tooShort) {
        String text = answer == null ? "" : answer.trim();

        // 1) 回答过短：请考生展开
        if (tooShort || text.length() < MIN_ANSWER_LENGTH) {
            return rule("你的回答比较简短，能否结合具体的经历或例子，详细说说你的思路和做法？",
                    "回答过于简短，缺少具体细节");
        }

        // 2) 命中技术名词：针对该技术点深入追问
        String hitTerm = TECH_TERMS.stream().filter(t -> containsIgnoreCase(text, t)).findFirst().orElse(null);
        if (hitTerm != null) {
            return rule("你刚才提到了「" + hitTerm + "」，可以具体说一下你在项目中是如何使用它、解决了什么问题吗？",
                    "回答提到了技术点但缺少具体实现细节");
        }

        // 3) 含糊表述：追问确定性
        String vague = VAGUE_WORDS.stream().filter(text::contains).findFirst().orElse(null);
        if (vague != null) {
            return rule("你提到「" + vague + "」，这部分能否给出更确定、更具体的说明？",
                    "回答中存在含糊表述");
        }

        // 4) 缺少项目信号：追问真实落地
        boolean hasProject = PROJECT_SIGNALS.stream().anyMatch(text::contains);
        if (!hasProject) {
            return rule("能否结合一个你真实参与的项目，具体说明你在其中的职责和落地细节？",
                    "回答缺少具体的项目或实现细节");
        }

        // 5) 默认：进一步追问难点与解决过程
        return rule("针对刚才的回答，能否进一步说明你在实现过程中遇到的难点以及是如何解决的？",
                "需要进一步了解具体实现与思考过程");
    }

    private FollowUpResponse rule(String followUpQuestion, String triggerReason) {
        return FollowUpResponse.of(followUpQuestion, SOURCE_RULE, triggerReason);
    }

    private boolean containsIgnoreCase(String text, String term) {
        return text.toLowerCase().contains(term.toLowerCase());
    }
}
