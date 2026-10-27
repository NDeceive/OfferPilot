package com.zhimian.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 面试教练的单轮请求。
 * <p>
 * 前端状态机是流程的权威（GREET → PICK_JOB → UPLOAD → DONE），每次推进一个阶段就调一次。
 * 模型做两件事：读懂用户刚说的话（{@link #message}），然后把回应写出来。
 * <b>它只「提议」，不「执行」</b>——返回的意图由前端拿着自己的状态机去裁决，非法的一律忽略。
 * <p>
 * {@link #history} 保留最近的对话，让助手能结合已聊过的兴趣、经历和顾虑继续引导。
 * {@link #jobOptions} 提供「Java 后端」和 code {@code BE-JAVA} 的对照。清单由前端从自己的 {@code READY_JOBS} 白名单里出，
 * 后端再拿这份清单反过来校验模型输出，防止它编出一个不存在的岗位。
 */
@Data
public class AiPrepRequest {

    /** 当前阶段，取值与前端状态机一一对应 */
    @NotBlank(message = "stage 不能为空")
    @Pattern(regexp = "GREET|PICK_JOB|UPLOAD|DONE", message = "stage 取值非法")
    private String stage;

    /** 用户刚发的一条消息；为空表示这轮是点按钮推进的，没有用户输入 */
    @Size(max = 500, message = "消息不能超过 500 字")
    private String message;

    /** 最近的对话，不含 message；仅用于理解上下文，不是流程指令。 */
    @Size(max = 20, message = "对话历史不能超过 20 条")
    @Valid
    private List<HistoryMessage> history;

    /** 用户已选中的岗位名，PICK_JOB 之后才有 */
    private String jobName;

    /** 可选岗位方向（岗位族名），供 GREET / PICK_JOB 阶段参考 */
    private List<String> families;

    /**
     * 可面试岗位清单，只含前端 READY_JOBS 白名单里的岗位。
     * 上限 30 条：这是提示词素材，不能让调用方塞进来一大坨把上下文顶爆。
     */
    @Size(max = 30, message = "岗位清单过长")
    @Valid
    private List<JobOption> jobOptions;

    /** 简历解析出的技能标签，UPLOAD 之后才有 */
    private List<String> skills;

    /** 简历文件名，UPLOAD 之后才有 */
    private String resumeFilename;

    @Data
    public static class HistoryMessage {
        @NotBlank
        @Pattern(regexp = "user|assistant", message = "历史消息角色非法")
        private String role;

        @NotBlank
        @Size(max = 1500, message = "历史消息过长")
        private String content;
    }

    /** 岗位清单里的一项 */
    @Data
    public static class JobOption {
        /** 岗位 code，如 BE-JAVA */
        @Size(max = 32)
        private String code;
        /** 岗位全称，如 Java后端开发工程师 */
        @Size(max = 64)
        private String name;
        /** 所属岗位族，如 后端开发 */
        @Size(max = 32)
        private String family;
    }
}
