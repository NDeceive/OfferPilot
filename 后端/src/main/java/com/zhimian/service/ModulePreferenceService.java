package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.entity.InterviewModulePreference;
import com.zhimian.entity.InterviewScoreConfigSnapshot;
import com.zhimian.entity.ScoreModule;
import com.zhimian.mapper.InterviewModulePreferenceMapper;
import com.zhimian.mapper.InterviewScoreConfigSnapshotMapper;
import com.zhimian.mapper.ScoreModuleMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 模块偏好配置服务：模块字典查询、偏好CRUD、权重计算（含并列逻辑）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModulePreferenceService {

    private final ScoreModuleMapper scoreModuleMapper;
    private final InterviewModulePreferenceMapper preferenceMapper;
    private final InterviewScoreConfigSnapshotMapper snapshotMapper;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 排名权重池 */
    private static final Map<Integer, Double> RANK_WEIGHT_POOL = Map.of(
            1, 0.30, 2, 0.25, 3, 0.20, 4, 0.15, 5, 0.10
    );

    /** 等级 → 目标线 */
    private static final Map<Integer, Integer> LEVEL_TARGET = Map.of(
            1, 65, 2, 75, 3, 85
    );

    // ============================ 模块字典 ============================

    /** 查询全部10个模块 */
    public List<ScoreModule> listModules() {
        return scoreModuleMapper.selectList(
                new LambdaQueryWrapper<ScoreModule>()
                        .orderByAsc(ScoreModule::getSortOrder));
    }

    // ============================ 偏好CRUD ============================

    /** 保存用户的模块选择（含权重快照） */
    public void savePreference(Long sessionId, Long userId, List<PreferenceItem> items) {
        // 1. 计算权重
        Map<String, Double> weights = calculateWeights(items);
        Map<String, Integer> targets = new LinkedHashMap<>();
        for (PreferenceItem item : items) {
            targets.put(item.getCode(), getTargetScore(item.getLevel()));
        }

        // 2. 保存偏好
        InterviewModulePreference pref = new InterviewModulePreference();
        pref.setSessionId(sessionId);
        pref.setUserId(userId);
        pref.setModulesJson(toJson(items));
        preferenceMapper.insert(pref);

        // 3. 保存权重+目标线快照
        InterviewScoreConfigSnapshot snap = new InterviewScoreConfigSnapshot();
        snap.setSessionId(sessionId);
        snap.setWeightsJson(toJson(weights));
        snap.setTargetsJson(toJson(targets));
        snapshotMapper.insert(snap);

        log.info("模块偏好已保存 sessionId={}, modules={}", sessionId, items.size());
    }

    /** 查询某次面试的模块偏好 */
    public List<PreferenceItem> getPreference(Long sessionId) {
        InterviewModulePreference pref = preferenceMapper.selectOne(
                new LambdaQueryWrapper<InterviewModulePreference>()
                        .eq(InterviewModulePreference::getSessionId, sessionId)
                        .last("LIMIT 1"));
        if (pref == null || pref.getModulesJson() == null) {
            return Collections.emptyList();
        }
        return parsePreferenceItems(pref.getModulesJson());
    }

    /** 查询权重快照 */
    public Map<String, Double> getWeightsSnapshot(Long sessionId) {
        InterviewScoreConfigSnapshot snap = snapshotMapper.selectOne(
                new LambdaQueryWrapper<InterviewScoreConfigSnapshot>()
                        .eq(InterviewScoreConfigSnapshot::getSessionId, sessionId)
                        .last("LIMIT 1"));
        if (snap == null || snap.getWeightsJson() == null) {
            return Collections.emptyMap();
        }
        return parseDoubleMap(snap.getWeightsJson());
    }

    /** 查询目标线快照 */
    public Map<String, Integer> getTargetsSnapshot(Long sessionId) {
        InterviewScoreConfigSnapshot snap = snapshotMapper.selectOne(
                new LambdaQueryWrapper<InterviewScoreConfigSnapshot>()
                        .eq(InterviewScoreConfigSnapshot::getSessionId, sessionId)
                        .last("LIMIT 1"));
        if (snap == null || snap.getTargetsJson() == null) {
            return Collections.emptyMap();
        }
        Map<String, Double> raw = parseDoubleMap(snap.getTargetsJson());
        Map<String, Integer> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(k, v.intValue()));
        return result;
    }

    // ============================ 权重计算 ============================

    /**
     * 按文档公式计算权重，支持并列。
     * <p>
     * 并列组内每个模块权重 = 并列组占据名次的权重池总和 / 并列组模块数。
     * 全部并列（均衡训练模式）时每个 = 20%。
     */
    public Map<String, Double> calculateWeights(List<PreferenceItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        // 按 rank 分组
        Map<Integer, List<PreferenceItem>> groups = new LinkedHashMap<>();
        for (PreferenceItem item : items) {
            groups.computeIfAbsent(item.getRank(), k -> new ArrayList<>()).add(item);
        }

        // 均衡训练模式：全部并列
        if (groups.size() == 1) {
            Map<String, Double> result = new LinkedHashMap<>();
            double each = 1.0 / items.size();
            for (PreferenceItem item : items) {
                result.put(item.getCode(), round(each));
            }
            return result;
        }

        // 为每个并排组分配权重
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<PreferenceItem>> entry : groups.entrySet()) {
            int rank = entry.getKey();
            List<PreferenceItem> group = entry.getValue();
            int groupSize = group.size();

            // 该组占据的排名范围：[rank, rank + groupSize - 1]
            double poolSum = 0;
            for (int r = rank; r < rank + groupSize; r++) {
                poolSum += RANK_WEIGHT_POOL.getOrDefault(r, 0.0);
            }

            double each = poolSum / groupSize;
            for (PreferenceItem item : group) {
                result.put(item.getCode(), round(each));
            }
        }
        return result;
    }

    /** 等级 → 目标线 */
    public int getTargetScore(int level) {
        return LEVEL_TARGET.getOrDefault(level, 75);
    }

    // ============================ 工具方法 ============================

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON序列化失败", e);
            return "{}";
        }
    }

    private List<PreferenceItem> parsePreferenceItems(String json) {
        try {
            return JSON.readValue(json, new TypeReference<List<PreferenceItem>>() {});
        } catch (Exception e) {
            log.warn("偏好JSON解析失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    private Map<String, Double> parseDoubleMap(String json) {
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.warn("快照JSON解析失败: {}", json, e);
            return Collections.emptyMap();
        }
    }

    // ============================ 偏好条目 ============================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferenceItem {
        /** 模块编码 */
        private String code;
        /** 排位 1-5，相同数字表示并列 */
        private int rank;
        /** 目标等级: 1=简单关注 2=重点提升 3=核心突破 */
        private int level;
    }
}
