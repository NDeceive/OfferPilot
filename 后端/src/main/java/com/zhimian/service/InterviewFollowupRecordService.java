package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.config.UserContext;
import com.zhimian.dto.FollowupRecordPageResponse;
import com.zhimian.dto.FollowupRecordStats;
import com.zhimian.entity.InterviewFollowupRecord;
import com.zhimian.mapper.InterviewFollowupRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/**
 * 追问记录保存服务（实验分析用）。
 * <p>
 * 核心约束：落库失败绝不能影响面试主流程。因此这里统一吞掉异常，
 * 仅打印告警日志，由调用方继续正常返回追问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewFollowupRecordService {

    private final InterviewFollowupRecordMapper recordMapper;

    /**
     * 安全保存一条追问记录：成功打 info 日志，失败打 warn 日志并吞掉异常，
     * 保证不会因为落库失败而中断面试。
     */
    public void saveSafely(InterviewFollowupRecord record) {
        try {
            recordMapper.insert(record);
            log.info("[追问记录] 保存成功 id={}, sessionId={}, questionId={}, source={}",
                    record.getId(), record.getSessionId(), record.getQuestionId(), record.getSource());
        } catch (Exception e) {
            // 规则 5：保存失败只告警、不抛出，面试照常进行
            log.warn("[追问记录] 保存失败 sessionId={}, questionId={}, source={}, err={}",
                    record.getSessionId(), record.getQuestionId(), record.getSource(), e.getMessage());
        }
    }

    /**
     * 分页查询追问记录（实验分析用），按 id 倒序（最新在前）。
     * 项目未启用分页插件，这里用 selectCount + LIMIT offset,size 手动分页，
     * 与代码库既有的 .last("LIMIT ...") 风格一致，避免引入新拦截器影响既有流程。
     *
     * @param pageNo    页码，从 1 开始
     * @param pageSize  每页条数
     * @param source    可选，AI / RULE 过滤
     * @param sessionId 可选，会话过滤
     * @param jobId     可选，岗位过滤
     */
    public FollowupRecordPageResponse pageQuery(long pageNo, long pageSize,
                                                String source, Long sessionId, Long jobId) {
        // 入参纠偏：页码/页大小兜底，页大小设上限防止一次性拉取过多
        long safePageNo = pageNo < 1 ? 1 : pageNo;
        long safePageSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long offset = (safePageNo - 1) * safePageSize;

        LambdaQueryWrapper<InterviewFollowupRecord> wrapper = new LambdaQueryWrapper<>();
        // 安全：追问记录含原始面试题与考生回答，必须按当前登录用户隔离。
        // 强制以服务端 UserContext 的 userId 过滤，忽略任何前端传入的 userId，
        // 每个用户只能查询本人的追问记录。
        wrapper.eq(InterviewFollowupRecord::getUserId, UserContext.getUserId());
        if (StringUtils.hasText(source)) {
            wrapper.eq(InterviewFollowupRecord::getSource, source.trim());
        }
        if (sessionId != null) {
            wrapper.eq(InterviewFollowupRecord::getSessionId, sessionId);
        }
        if (jobId != null) {
            wrapper.eq(InterviewFollowupRecord::getJobId, jobId);
        }

        // 先取总数：无记录时返回空列表 + total=0，而不是报错
        Long total = recordMapper.selectCount(wrapper);
        FollowupRecordPageResponse resp = new FollowupRecordPageResponse();
        resp.setPageNo(safePageNo);
        resp.setPageSize(safePageSize);
        resp.setTotal(total);

        if (total == null || total == 0) {
            resp.setList(Collections.emptyList());
            log.info("[追问记录查询] 命中 0 条 source={}, sessionId={}, jobId={}", source, sessionId, jobId);
            return resp;
        }

        // 最新在前，再按 offset/size 截取当前页
        wrapper.orderByDesc(InterviewFollowupRecord::getId)
                .last("LIMIT " + offset + ", " + safePageSize);
        List<InterviewFollowupRecord> list = recordMapper.selectList(wrapper);
        resp.setList(list);
        log.info("[追问记录查询] total={}, 返回 {} 条, pageNo={}, pageSize={}, source={}, sessionId={}, jobId={}",
                total, list.size(), safePageNo, safePageSize, source, sessionId, jobId);
        return resp;
    }

    /**
     * 统计追问记录的 AI / RULE 数量与占比（供论文写作用）。
     * 无记录时各计数为 0、占比为 0，不报错。
     */
    public FollowupRecordStats stats() {
        long total = countBySource(null);
        long aiCount = countBySource("AI");
        long ruleCount = countBySource("RULE");

        FollowupRecordStats stats = new FollowupRecordStats();
        stats.setTotalCount(total);
        stats.setAiCount(aiCount);
        stats.setRuleCount(ruleCount);
        // 占比以总数为分母，保留 4 位小数；总数为 0 时占比为 0，避免除零
        stats.setAiRatio(ratio(aiCount, total));
        stats.setRuleRatio(ratio(ruleCount, total));

        log.info("[追问记录统计] total={}, ai={}, rule={}, aiRatio={}, ruleRatio={}",
                total, aiCount, ruleCount, stats.getAiRatio(), stats.getRuleRatio());
        return stats;
    }

    /** 按来源计数；source 为 null 表示统计当前用户全部 */
    private long countBySource(String source) {
        LambdaQueryWrapper<InterviewFollowupRecord> wrapper = new LambdaQueryWrapper<>();
        // 安全：统计同样按当前登录用户隔离，只统计本人的追问记录。
        wrapper.eq(InterviewFollowupRecord::getUserId, UserContext.getUserId());
        if (source != null) {
            wrapper.eq(InterviewFollowupRecord::getSource, source);
        }
        Long count = recordMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /** 计算占比（part/total），保留 4 位小数；total 为 0 返回 0，避免除零 */
    private double ratio(long part, long total) {
        if (total <= 0) {
            return 0d;
        }
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
