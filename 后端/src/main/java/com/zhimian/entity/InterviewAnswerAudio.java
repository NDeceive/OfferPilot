package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 每题作答录音。只存元数据，文件落盘。 */
@Data
@TableName("interview_round_audio")
public class InterviewAnswerAudio {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Integer roundNo;
    /** 同一题内的第几段作答：0 主回答，1 起为追问回答 */
    private Integer answerSeq;
    private Long questionId;
    /** 相对存储根目录的路径，如 12/3.wav */
    private String filePath;
    private Integer durationMs;
    private Long byteSize;
    /** OK / FAILED */
    private String status;
    private String failureReason;
    private LocalDateTime createTime;
}
