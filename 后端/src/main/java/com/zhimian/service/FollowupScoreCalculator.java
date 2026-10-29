package com.zhimian.service;

import com.zhimian.entity.InterviewMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 追问维度评分（两条报告链路共用，避免口径漂移）。
 * <p>
 * 旧实现只看「追问应答比例」，多层追问之后该比例长期贴近 1，人人 90 分。
 * 这里改为按「被追问后是否补充了新信息」计分：与同一题此前回答的 2-gram 重合度越高，说明只是在复述，得分越低。
 * 零追问不再一律 95：只有内容闸门判断「确实无需追问」才给高分，随机未抽中给中性分。
 */
@Component
@RequiredArgsConstructor
public class FollowupScoreCalculator {

    private final static String ROLE_INTERVIEWER = "INTERVIEWER";
    private final static String ROLE_CANDIDATE = "CANDIDATE";
    private final static String MSG_MAIN = "MAIN";
    private final static String MSG_FOLLOWUP = "FOLLOWUP";
    private final static String MSG_ANSWER = "ANSWER";

    private static final double NEUTRAL_SCORE = 78;
    private static final double NO_NEED_SCORE = 95;

    private final InterviewFollowupChainService chainService;

    public double score(Long sessionId, List<InterviewMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        List<String> followupQuestions = new ArrayList<>();
        Map<Long, List<String>> answersByQuestion = new LinkedHashMap<>();
        Set<Long> questionKeys = new LinkedHashSet<>();
        Long currentQuestion = null;

        for (InterviewMessage msg : messages) {
            if (ROLE_INTERVIEWER.equals(msg.getRole()) && MSG_MAIN.equals(msg.getMsgType())) {
                currentQuestion = msg.getId();
                questionKeys.add(currentQuestion);
            } else if (ROLE_INTERVIEWER.equals(msg.getRole()) && MSG_FOLLOWUP.equals(msg.getMsgType())) {
                followupQuestions.add(msg.getContent());
            } else if (ROLE_CANDIDATE.equals(msg.getRole()) && MSG_ANSWER.equals(msg.getMsgType())
                    && currentQuestion != null) {
                answersByQuestion.computeIfAbsent(currentQuestion, k -> new ArrayList<>())
                        .add(msg.getContent() == null ? "" : msg.getContent().trim());
            }
        }

        if (followupQuestions.isEmpty()) {
            return noFollowupScore(sessionId, questionKeys.size());
        }

        double total = 0;
        for (List<String> answers : answersByQuestion.values()) {
            if (answers.size() <= 1) {
                continue;
            }
            for (int i = 1; i < answers.size(); i++) {
                total += answerQuality(answers.get(i), answers.subList(0, i));
            }
        }

        double mean = Math.max(0, Math.min(1, total / followupQuestions.size()));
        return 45 + 45 * mean;
    }

    /** 单条追问回答的质量：长度系数 ×（基础分 + 新颖度加成） */
    private double answerQuality(String answer, List<String> previous) {
        String text = answer == null ? "" : answer.trim();
        if (text.length() < 8) {
            return 0;
        }
        double lenFactor = Math.min(1.0, text.length() / 80.0);
        double novelty = 1.0 - jaccard(bigrams(text), bigrams(String.join(" ", previous)));
        return Math.max(0, Math.min(1, lenFactor * (0.55 + 0.45 * novelty)));
    }

    private double noFollowupScore(Long sessionId, int questionCount) {
        if (questionCount <= 0) {
            return 0;
        }
        long noNeed = sessionId == null ? 0 : chainService.countNoNeed(sessionId);
        if (noNeed * 2 >= questionCount) {
            return NO_NEED_SCORE;
        }
        return NEUTRAL_SCORE;
    }

    private Set<String> bigrams(String text) {
        Set<String> set = new HashSet<>();
        String normalized = text == null ? "" : text.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）《》]", "");
        for (int i = 0; i + 1 < normalized.length(); i++) {
            set.add(normalized.substring(i, i + 2));
        }
        return set;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String s : a) {
            if (b.contains(s)) {
                inter++;
            }
        }
        return (double) inter / (a.size() + b.size() - inter);
    }
}
