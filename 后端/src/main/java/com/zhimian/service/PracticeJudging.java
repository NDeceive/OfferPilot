package com.zhimian.service;

import com.zhimian.common.BizException;
import java.util.*;

/** Versioned comparison rules. No implicit whitespace token folding. */
public final class PracticeJudging {
    private PracticeJudging() {}
    public static String status(int id) {
        return switch(id) {
            case 1 -> "QUEUED"; case 2 -> "RUNNING"; case 3 -> "ACCEPTED";
            case 4 -> "WRONG_ANSWER"; case 5 -> "TIME_LIMIT_EXCEEDED"; case 6 -> "COMPILE_ERROR";
            case 7,8,9,10,11 -> "RUNTIME_ERROR"; default -> "SYSTEM_ERROR";
        };
    }
    public static boolean matches(String actual, String expected, String mode) {
        String a = actual == null ? "" : actual, b = expected == null ? "" : expected;
        return switch(mode) {
            case "EXACT" -> a.equals(b);
            case "LINE_ENDING" -> lines(a).equals(lines(b));
            case "TOKENS" -> Arrays.equals(a.strip().split("\\s+"),b.strip().split("\\s+"));
            default -> throw new BizException("题目输出比较规则尚未支持");
        };
    }
    private static String lines(String value) { return value.replace("\r\n","\n").replaceAll("[ \\t]+(?=\n|$)","").replaceFirst("\n$",""); }
    public static String log(String value) { return value == null ? "" : value.length() > 32768 ? value.substring(0,32768)+"\n[输出已截断]" : value; }
    public static final Set<String> INFRASTRUCTURE = Set.of("SYSTEM_ERROR","SERVICE_UNAVAILABLE");
}
