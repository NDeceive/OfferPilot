package com.zhimian.dto;

import lombok.Data;

/**
 * 用户训练统计（首页/个人中心展示）。
 * 所有字段均来自真实数据，无任何模拟值；没有数据时为 0。
 */
@Data
public class UserStats {
    /** 有有效回答且报告就绪的训练次数；无效教学提交不计入 */
    private long finishedInterviews;
    /** 进行中的面试场次 */
    private long ongoingInterviews;
    /** 平均得分（无报告时为 0） */
    private double averageScore;
    /** 最高得分 */
    private double highestScore;
    /** 生成的报告数量 */
    private long reportCount;
    /** 已保存简历的技能关键词数（个人画像规模） */
    private int skillCount;
}
