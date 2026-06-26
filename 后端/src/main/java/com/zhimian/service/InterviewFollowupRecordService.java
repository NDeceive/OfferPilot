package com.zhimian.service;

import com.zhimian.entity.InterviewFollowupRecord;
import com.zhimian.mapper.InterviewFollowupRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
