package com.zhimian.dto;

import com.zhimian.entity.InterviewFollowupRecord;
import lombok.Data;

import java.util.List;

/**
 * 追问记录分页查询结果（实验分析用）。
 * 项目未启用 MyBatis-Plus 分页插件，这里用轻量分页包装，避免引入新拦截器影响既有流程。
 */
@Data
public class FollowupRecordPageResponse {

    /** 当前页码（从 1 开始） */
    private Long pageNo;
    /** 每页条数 */
    private Long pageSize;
    /** 符合条件的总记录数 */
    private Long total;
    /** 当前页数据；无记录时为空数组而非 null */
    private List<InterviewFollowupRecord> list;
}
