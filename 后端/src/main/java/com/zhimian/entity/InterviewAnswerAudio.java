package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 每题作答录音。只存元数据，文件落盘。 */
@Data
@TableName("interview_answer_audio")
public class InterviewAnswerAudio {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Integer roundNo;
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
