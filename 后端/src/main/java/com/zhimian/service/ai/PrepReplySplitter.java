package com.zhimian.service.ai;

/**
 * 把模型的流式输出切成「首行意图 JSON」与「正文」两段。
 * <p>
 * 为什么不干脆让模型只输出一个 JSON、把回复也当成里面的一个字段：
 * 那样就得边流边从 JSON 字符串里往外抠正文，一路上要处理换行、引号、Unicode 三类转义，
 * 而转义序列随时会被切在两个 chunk 中间，边界情况极难写对。
 * 让正文是<b>纯文本</b>就完全绕开了这个问题：头解析完，后面来什么就转什么。
 * <p>
 * 代价是模型可能不按格式来，所以这里全程走「解析不了就降级」：
 * 首行不是 JSON 就把整段当正文吐出去——功能少个意图，但用户看到的对话永远正常。
 * 同理，首行迟迟等不到换行（超过 {@link #MAX_HEADER_CHARS}）也放弃，防止模型写长文时前几百字被一直扣着不显示。
 * <p>
 * 无状态依赖、不用加锁：一个实例只服务一条流，全程在同一个 worker 线程上调用。
 */
public class PrepReplySplitter {

    /** 首行最多缓冲多少字符还没等到换行就放弃解析（正常 JSON 头只有几十字符） */
    private static final int MAX_HEADER_CHARS = 300;

    private final StringBuilder pending = new StringBuilder();
    private boolean resolved = false;
    private String headerJson = null;
    /**
     * 头是抢在换行之前解析出来的（闭合花括号先到），此时分隔用的那个换行还在后面的块里。
     * 记一下，等它来了直接吃掉，别让正文以一个空行开头。
     */
    private boolean swallowNextNewline = false;

    /**
     * 喂一段增量。
     *
     * @return 这段增量里属于「正文」的部分；返回空串表示还在解析头（或本段全是头），
     *         调用方不要把它当成正文发出去
     */
    public String feed(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        if (resolved) {
            return swallowNextNewline ? eatLeadingNewlines(chunk) : chunk;
        }

        pending.append(chunk);
        while (true) {
            int nl = pending.indexOf("\n");
            if (nl < 0) {
                // 还没等到换行。头本身是完整的 JSON，闭合花括号一到就能定，
                // 不必非等换行——模型偶尔会把头和正文挤在同一行，
                // 硬等下去会一直等到流结束，整段被当成正文，意图就白丢了。
                String t = pending.toString().trim();
                if (t.startsWith("{")) {
                    int close = t.indexOf('}');
                    if (close >= 0) {
                        headerJson = t.substring(0, close + 1);
                        resolved = true;
                        pending.setLength(0);
                        // 同一行后面可能已经跟了正文，接着吐
                        String tail = stripLeadingFence(t.substring(close + 1).trim());
                        // 什么都没跟：分隔的换行还在后面的块里，下一个块要吃掉它
                        swallowNextNewline = tail.isEmpty();
                        return tail;
                    }
                }
                // 攒太多了说明不是头，兜底放行
                return pending.length() > MAX_HEADER_CHARS ? giveUp() : "";
            }

            String line = pending.substring(0, nl).trim();
            String rest = pending.substring(nl + 1);
            pending.setLength(0);

            // 空行与代码围栏不算首行，继续往下找（模型偶尔会给 ```json 包一下）
            if (line.isEmpty() || line.startsWith("```")) {
                pending.append(rest);
                continue;
            }

            int close = line.startsWith("{") ? line.indexOf('}') : -1;
            if (close < 0) {
                // 首行不是 JSON 头：模型没按格式来，整段当正文，丢的只是一个意图
                resolved = true;
                return join(line, rest);
            }

            headerJson = line.substring(0, close + 1);
            resolved = true;
            // 头后面可能同一行还跟了正文（模型把两行写一起了），补回来
            return stripLeadingFence(join(line.substring(close + 1).trim(), rest));
        }
    }

    /** 流结束时调用：把还扣在缓冲区里的内容交出去，避免最后一段永远不显示 */
    public String flush() {
        if (resolved) {
            return "";
        }
        return giveUp();
    }

    public boolean resolved() {
        return resolved;
    }

    /** 解析出的首行 JSON 原文；没解析到（降级了）返回 null */
    public String headerJson() {
        return headerJson;
    }

    /** 吃掉开头连续的换行（含 \r），只吃这一次，之后原样透传 */
    private String eatLeadingNewlines(String chunk) {
        swallowNextNewline = false;
        int i = 0;
        while (i < chunk.length() && (chunk.charAt(i) == '\n' || chunk.charAt(i) == '\r')) {
            i++;
        }
        // 整块都是换行：可能后面还有，继续等下一块来判断
        swallowNextNewline = (i == chunk.length());
        return chunk.substring(i);
    }

    private String giveUp() {
        resolved = true;
        headerJson = null;
        String out = pending.toString();
        pending.setLength(0);
        return stripLeadingFence(out);
    }

    private String join(String head, String tail) {
        if (head.isEmpty()) return tail;
        if (tail.isEmpty()) return head;
        return head + "\n" + tail;
    }

    /** 正文开头若残留 ``` 围栏，把那一行去掉 */
    private String stripLeadingFence(String s) {
        if (!s.startsWith("```")) {
            return s;
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? "" : s.substring(nl + 1);
    }
}
