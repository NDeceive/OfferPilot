package com.zhimian.controller;

import com.zhimian.common.BizException;
import com.zhimian.common.Result;
import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.export.ExportException;
import com.zhimian.export.ExportService;
import com.zhimian.service.ImprovementPlanService;
import com.zhimian.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 能力报告接口（Phase 2 规则化 + 导出）。
 */
@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ExportService exportService;
    private final ImprovementPlanService improvementPlanService;

    /** 报告详情：仅本人可查看（服务层按 UserContext 校验归属） */
    @GetMapping("/{reportId}")
    public Result<ReportDetailResponse> detail(@PathVariable Long reportId) {
        return Result.success(reportService.getDetail(reportId));
    }

    /** 个性化提升建议：基于候选人实际回答生成，非套话模板 */
    @GetMapping("/{reportId}/improvement-path")
    public Result<Map<String, ImprovementPlanService.SuggestionItem>> improvementPath(@PathVariable Long reportId) {
        return Result.success(improvementPlanService.generate(reportId));
    }

    /**
     * 导出报告为 PDF 或 Word 文件。
     * GET /api/report/{reportId}/export?format=pdf|docx
     * <p>
     * 此端点直接写入 HttpServletResponse（二进制流），不使用 Result&lt;T&gt; 包装。
     * 所有异常在方法内 try-catch 并手动写 JSON 错误，避免触发 GlobalExceptionHandler。
     * </p>
     */
    @GetMapping("/{reportId}/export")
    public void export(@PathVariable Long reportId,
                       @RequestParam(defaultValue = "pdf") String format,
                       HttpServletResponse response) throws IOException {

        // 1. 校验 format
        if (!"pdf".equals(format) && !"docx".equals(format)) {
            writeJsonError(response, 400, "不支持的导出格式，仅支持 pdf / docx");
            return;
        }

        try {
            // 2. 获取报告数据（内部校验归属，非本人抛 BizException）
            ReportDetailResponse detail = reportService.getDetail(reportId);

            // 3. 生成文件
            byte[] fileBytes = exportService.export(reportId, format);

            // 4. 设置响应头
            String ext = "pdf".equals(format) ? ".pdf" : ".docx";
            String safeJobName = detail.getJobName() != null
                    ? detail.getJobName().replaceAll("[\\\\/:*?\"<>|]", "_") : "报告";
            String fileName = "面试报告_" + safeJobName + "_" + detail.getReportId() + ext;
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            String contentType = "pdf".equals(format)
                    ? "application/pdf"
                    : "application/msword";

            response.setContentType(contentType);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            response.setContentLength(fileBytes.length);

            // 5. 输出
            response.getOutputStream().write(fileBytes);
            response.getOutputStream().flush();

            log.info("报告导出成功 reportId={} format={} size={}bytes", reportId, format, fileBytes.length);

        } catch (BizException e) {
            log.warn("报告导出权限错误 reportId={}: {}", reportId, e.getMessage());
            writeJsonError(response, 400, e.getMessage());
        } catch (ExportException e) {
            log.warn("报告导出失败 reportId={}: {}", reportId, e.getMessage());
            writeJsonError(response, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("报告导出异常 reportId={}", reportId, e);
            writeJsonError(response, 500, "导出失败，请确认已安装 Microsoft Word 2007+ 且未在启动前打开 Word");
        }
    }

    /** 向 HttpServletResponse 写入 JSON 错误（不抛异常，避免触发 GlobalExceptionHandler） */
    private void writeJsonError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code >= 500 ? 500 : 400);
        response.setContentType("application/json;charset=UTF-8");
        // 手动构造简单 JSON，避免依赖 ObjectMapper 序列化（减少开销）
        String json = "{\"code\":" + code + ",\"message\":\"" + escapeJson(message) + "\"}";
        response.getWriter().write(json);
        response.getWriter().flush();
    }

    /** 简单 JSON 字符串转义 */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
