package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.dto.AiPrepRequest;
import com.zhimian.service.ai.AiPrepPromptBuilder;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.PrepReplySplitter;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 面试教练：把当前阶段翻译成一句中文口语，以 SSE 流式吐给前端。
 * <p>
 * <b>只负责「说话」和「听懂」，不负责「决定」</b>：流程由前端状态机
 * （GREET → PICK_JOB → UPLOAD → DONE）驱动，每推进一步调一次本服务。
 * 模型对用户那句话的理解通过 {@code act} 事件回传，但那只是<b>提议</b>——
 * 前端拿着自己的白名单和状态机去裁决，非法的一律忽略。模型既不决定下一步，
 * 也不参与出题、评分、报告。为把这点钉死在依赖层面，本类只注入
 * {@link DeepSeekClient} 与 {@link AiPrepPromptBuilder}，不持有任何 Mapper / 业务 Service
 * ——物理上无法写库。
 * <p>
 * 可用性契约：<b>任何情况下都不抛异常给调用方，也不指望 HTTP 状态码表达失败。</b>
 * AI 不可用（未配置 / 报错 / 并发已满）时发一帧 {@code fallback} 后照常 {@code done}，
 * 前端凭「一个字都没收到」自动换用预置文案，所以默认交付形态（无 key）永远可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPrepService {

    private static final String EVENT_META = "meta";
    private static final String EVENT_DELTA = "delta";
    private static final String EVENT_ACT = "act";
    private static final String EVENT_FALLBACK = "fallback";
    private static final String EVENT_DONE = "done";

    private static final String MODE_AI = "AI";
    private static final String MODE_RULE = "RULE";

    /** 模型可以说出来的意图。不在这个集合里的一律按 OTHER 处理，不让它自创取值。 */
    private static final Set<String> ALLOWED_INTENTS = new HashSet<>(Arrays.asList(
            "READY", "PICK_JOB", "NO_RESUME", "CONFIRM", "QUESTION", "OTHER"));

    private static final String INTENT_OTHER = "OTHER";
    private static final String CONFIDENCE_HIGH = "HIGH";
    private static final String CONFIDENCE_LOW = "LOW";

    /** 并发上限：超出立刻降级而不是排队，宁可少点 AI 味也不能让用户干等 */
    private static final int MAX_CONCURRENT = 8;

    /**
     * SSE 写事件用的 UTF-8 text/plain：显式带 charset，避免 StringHttpMessageConverter
     * 在默认字符集下把中文写成乱码（SSE 规定必须 UTF-8）。
     */
    private static final MediaType TEXT_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final DeepSeekClient deepSeekClient;
    private final AiPrepPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 自带线程池，<b>刻意不注册成 Spring 的 Executor Bean</b>：
     * {@code TaskExecutionAutoConfiguration} 带 {@code @ConditionalOnMissingBean(Executor.class)}，
     * 容器里一旦出现 Executor，Spring Boot 的默认线程池配置会整体退避。
     * 用守护线程是为了不阻塞 JVM 退出（正常路径仍由 {@link #shutdown()} 收尾）。
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT, r -> {
        Thread t = new Thread(r, "ai-prep-sse");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /** 供前端顶部徽标判断当前是 AI 还是演示模式 */
    public Map<String, Object> status() {
        return Map.of("mode", deepSeekClient.isUsable() ? MODE_AI : MODE_RULE);
    }

    /**
     * 开始一段流式话术。方法本身立即返回，真正的 IO 在独立线程池里跑。
     *
     * @param userId 当前登录用户，仅供日志追踪。注意必须由调用方在 Tomcat 线程上读好传入：
     *               {@code UserContext} 是 ThreadLocal，worker 线程里取到的一律是 null。
     */
    public void streamPrep(AiPrepRequest req, Long userId, SseEmitter emitter) {
        // 客户端断开的信号。置位后所有写操作静默跳过，并让 DeepSeek 侧尽快停止读流。
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            completeQuietly(emitter);
        });

        try {
            executor.execute(() -> {
                try {
                    runStream(req, userId, emitter, closed);
                } catch (Exception e) {
                    // 兜底：无论如何都要把 emitter 收掉，否则连接会一直挂到 60s 超时
                    log.error("面试教练话术流处理异常：stage={}", req.getStage(), e);
                    finish(emitter, closed);
                }
            });
        } catch (RejectedExecutionException e) {
            // 并发已满：不排队、不挂起，直接按脚本模式收尾，前端表现与 AI 未启用时一致
            log.warn("面试教练线程池已满（{} 线程），本次降级为脚本话术", MAX_CONCURRENT);
            send(emitter, closed, EVENT_META, Map.of("mode", MODE_RULE, "stage", req.getStage()));
            send(emitter, closed, EVENT_FALLBACK, Map.of("reason", "BUSY"));
            send(emitter, closed, EVENT_DONE, Map.of());
            finish(emitter, closed);
        }
    }

    private void runStream(AiPrepRequest req, Long userId, SseEmitter emitter, AtomicBoolean closed) {
        String stage = req.getStage();

        if (!deepSeekClient.isUsable()) {
            // 未配置 key：一帧 delta 都不发，前端凭「一字未收」自动使用预置文案
            log.debug("AI 未启用，面试教练走脚本话术：userId={} stage={}", userId, stage);
            send(emitter, closed, EVENT_META, Map.of("mode", MODE_RULE, "stage", stage));
            send(emitter, closed, EVENT_DONE, Map.of());
            finish(emitter, closed);
            return;
        }

        // 先告诉前端「这次是真 AI」，中途失败才好区分是降级还是本来就没开
        send(emitter, closed, EVENT_META, Map.of("mode", MODE_AI, "stage", stage));

        PrepReplySplitter splitter = new PrepReplySplitter();
        AtomicBoolean actSent = new AtomicBoolean(false);

        boolean ok = deepSeekClient.chatStream(
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(req),
                closed::get, // 浏览器断开后不再继续读 DeepSeek 的流，省下配额
                delta -> {
                    String text = splitter.feed(delta);
                    // 意图头一解析出来就抢先发：头只有几十字符，通常几百毫秒就到，
                    // 前端不必等整段话生成完才知道用户想干什么
                    String header = splitter.headerJson();
                    if (!actSent.get() && header != null) {
                        actSent.set(true);
                        send(emitter, closed, EVENT_ACT, buildAct(req, header));
                    }
                    if (!text.isEmpty()) {
                        send(emitter, closed, EVENT_DELTA, Map.of("t", text));
                    }
                });

        if (ok) {
            // 头一直没解析出来时整段都在缓冲区里，这里补吐，否则最后一段永远不显示
            String tail = splitter.flush();
            if (!tail.isEmpty()) {
                send(emitter, closed, EVENT_DELTA, Map.of("t", tail));
            }
        } else if (!closed.get()) {
            // 失败且不是客户端主动断开：发 fallback，前端会丢掉半截话改播预置文案
            log.warn("面试教练话术生成失败，前端将回退预置文案：userId={} stage={}", userId, stage);
            send(emitter, closed, EVENT_FALLBACK, Map.of("reason", "AI_ERROR"));
        }

        send(emitter, closed, EVENT_DONE, Map.of());
        finish(emitter, closed);
    }

    /**
     * 把模型给的意图头变成一帧 {@code act}。
     * <p>
     * 这里是「AI 提议、前端裁决」的中间那道闸：<b>模型说的任何标识符都不直接信</b>——
     * jobCode 必须在调用方给的清单里出现过，family 必须来自清单里的某个岗位族，
     * intent 必须在白名单里。编出来的值一律抹掉，宁可退化成「没听懂」，
     * 也不能让一个不存在的岗位 code 流到前端去查题库。
     * <p>
     * confidence 由 jobCode 是否定得下来 + 模型自己的说法共同决定，不单听模型的：
     * 它几乎总说自己 HIGH，而「高置信直接跳、低置信出确认条」全押在这个字段上。
     */
    private Map<String, Object> buildAct(AiPrepRequest req, String headerJson) {
        JsonNode node;
        try {
            node = objectMapper.readTree(headerJson);
        } catch (Exception e) {
            return act(INTENT_OTHER, null, null, CONFIDENCE_LOW);
        }

        String intent = text(node, "intent");
        if (intent == null || !ALLOWED_INTENTS.contains(intent)) {
            intent = INTENT_OTHER;
        }

        String jobCode = text(node, "jobCode");
        if (jobCode != null && !isOffered(req, jobCode)) {
            log.debug("模型给出的 jobCode 不在岗位清单里，已丢弃：{}", jobCode);
            jobCode = null;
        }

        String family = text(node, "family");
        if (family != null && !isKnownFamily(req, family)) {
            family = null;
        }

        // confidence 只对 PICK_JOB 有意义：「高置信」的定义就是「用户明确说了某个具体岗位」，
        // 前端据此直接跳转。别的意图也必须卡死——实测模型会在说「我没有简历」时顺手把
        // 当前岗位回填进 jobCode，只看 jobCode 的话这句问话就变成高置信，弹窗自己就弹出来了。
        boolean modelSure = CONFIDENCE_HIGH.equalsIgnoreCase(text(node, "confidence"));
        String confidence = ("PICK_JOB".equals(intent) && jobCode != null && modelSure)
                ? CONFIDENCE_HIGH : CONFIDENCE_LOW;

        return act(intent, jobCode, family, confidence);
    }

    private Map<String, Object> act(String intent, String jobCode, String family, String confidence) {
        // 用 LinkedHashMap 而不是 Map.of：后两个字段允许为 null，Map.of 会直接 NPE
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intent", intent);
        m.put("jobCode", jobCode);
        m.put("family", family);
        m.put("confidence", confidence);
        return m;
    }

    private boolean isOffered(AiPrepRequest req, String code) {
        List<AiPrepRequest.JobOption> options = req.getJobOptions();
        if (options == null) {
            return false;
        }
        for (AiPrepRequest.JobOption o : options) {
            if (o != null && code.equals(o.getCode())) {
                return true;
            }
        }
        return false;
    }

    /** 岗位族以清单里出现过的为准，不另外要求调用方再传一份 */
    private boolean isKnownFamily(AiPrepRequest req, String family) {
        List<AiPrepRequest.JobOption> options = req.getJobOptions();
        if (options == null) {
            return false;
        }
        for (AiPrepRequest.JobOption o : options) {
            if (o != null && family.equals(o.getFamily())) {
                return true;
            }
        }
        return false;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * 写一帧 SSE。文本统一用 JSON 包裹：裸文本里的换行会被 SseEmitter 拆成多行 data:，
     * 前端拼回来容易出错，而 JSON 会把换行转义成字面量 \n。
     * <p>
     * 写失败即认为对端已断开，置位 closed 让后续全部静默，避免连串异常刷日志。
     */
    private void send(SseEmitter emitter, AtomicBoolean closed, String event, Object payload) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(payload), TEXT_UTF8));
        } catch (Exception e) {
            closed.set(true);
        }
    }

    private void finish(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.getAndSet(true)) {
            return;
        }
        completeQuietly(emitter);
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 已结束 / 已断开都会走到这里，没有需要补救的动作
        }
    }
}
