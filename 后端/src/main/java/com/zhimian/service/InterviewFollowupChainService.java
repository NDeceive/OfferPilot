package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhimian.entity.InterviewFollowupChain;
import com.zhimian.mapper.InterviewFollowupChainMapper;
import com.zhimian.service.followup.FollowupDimension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 多层追问链的读写。与追问记录一样，落库失败只告警，绝不影响面试主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewFollowupChainService {

    private final InterviewFollowupChainMapper chainMapper;

    public void saveSafely(InterviewFollowupChain chain) {
        try {
            chainMapper.insert(chain);
        } catch (Exception e) {
            log.warn("[追问链] 保存失败 sessionId={}, questionId={}, layer={}, err={}",
                    chain.getSessionId(), chain.getQuestionId(), chain.getLayerNo(), e.getMessage());
        }
    }

    public List<InterviewFollowupChain> listByRound(Long sessionId, int roundNo) {
        try {
            return chainMapper.selectList(new LambdaQueryWrapper<InterviewFollowupChain>()
                    .eq(InterviewFollowupChain::getSessionId, sessionId)
                    .eq(InterviewFollowupChain::getRoundNo, roundNo)
                    .orderByAsc(InterviewFollowupChain::getLayerNo));
        } catch (Exception e) {
            // 表还没建好也不能拖垮面试主流程
            log.warn("[追问链] 读取失败 sessionId={}, round={}, err={}", sessionId, roundNo, e.getMessage());
            return java.util.List.of();
        }
    }

    /** 考生回答上一层追问后，把答案补回对应链记录 */
    public void recordAnswer(Long sessionId, int roundNo, int layerNo, String answer) {
        try {
            InterviewFollowupChain patch = new InterviewFollowupChain();
            patch.setAnswer(answer);
            chainMapper.update(patch, new LambdaUpdateWrapper<InterviewFollowupChain>()
                    .eq(InterviewFollowupChain::getSessionId, sessionId)
                    .eq(InterviewFollowupChain::getRoundNo, roundNo)
                    .eq(InterviewFollowupChain::getLayerNo, layerNo));
        } catch (Exception e) {
            log.warn("[追问链] 回填答案失败 sessionId={}, round={}, layer={}, err={}",
                    sessionId, roundNo, layerNo, e.getMessage());
        }
    }

    /** 本轮已经问过的方向，用于轮转 */
    public Set<FollowupDimension> askedDimensions(Long sessionId, int roundNo) {
        Set<FollowupDimension> set = new LinkedHashSet<>();
        for (InterviewFollowupChain c : listByRound(sessionId, roundNo)) {
            FollowupDimension d = FollowupDimension.fromCode(c.getDimension());
            if (d != null) {
                set.add(d);
            }
        }
        return set;
    }

    /** 会话内「内容判断为无需追问」的次数，供评分区分「答得完整」与「骰子没抽中」 */
    public long countNoNeed(Long sessionId) {
        try {
            Long n = chainMapper.selectCount(new LambdaQueryWrapper<InterviewFollowupChain>()
                    .eq(InterviewFollowupChain::getSessionId, sessionId)
                    .eq(InterviewFollowupChain::getGate, "STOP_NO_NEED"));
            return n == null ? 0 : n;
        } catch (Exception e) {
            log.warn("[追问链] 统计失败 sessionId={}, err={}", sessionId, e.getMessage());
            return 0;
        }
    }

    public void deleteBySession(Long sessionId) {
        chainMapper.delete(new LambdaQueryWrapper<InterviewFollowupChain>()
                .eq(InterviewFollowupChain::getSessionId, sessionId));
    }
}
