package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.Resume;
import com.zhimian.mapper.JobPositionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumeWorkbenchServiceTest {
    @AfterEach void clearUser() { UserContext.clear(); }

    @Test void generatedVersionRequiresCandidateConfirmedFacts() {
        UserContext.set(7L, "STUDENT");
        var service = new ResumeWorkbenchService(null, new ObjectMapper(), null, null, oneJob(), null) {
            @Override public List<ClaimView> claims() {
                return List.of(new ClaimView(4L, "PROJECT", "订单模块", "原始事实", "负责订单模块",
                        "MODULE", "", "PENDING", "", LocalDateTime.now()));
            }
        };
        BizException error = assertThrows(BizException.class,
                () -> service.generate(new ResumeWorkbenchService.GenerateInput("岗位简历", 1L, "张三", "", "")));
        assertTrue(error.getMessage().contains("核实"));
    }

    @Test void reviewRejectsVersionBoundToAnotherJob() {
        UserContext.set(7L, "STUDENT");
        var content = new ResumeWorkbenchService.ResumeContent("张三", "", "", "Java", "", "", "", "项目经历", "", "");
        var service = new ResumeWorkbenchService(null, new ObjectMapper(), null, null, oneJob(), null) {
            @Override public VersionView version(long id) {
                return new VersionView(id, 2L, "另一岗位版本", "MANUAL", content, LocalDateTime.now());
            }
        };
        assertThrows(BizException.class, () -> service.review(9L, 1L));
    }

    @Test void versionLookupScopesTheQueryToCurrentUser() {
        UserContext.set(7L, "STUDENT");
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                assertTrue(sql.contains("id=? AND user_id=?"));
                assertEquals(9L, args[0]);
                assertEquals(7L, args[1]);
                return List.of();
            }
        };
        var service = new ResumeWorkbenchService(jdbc, new ObjectMapper(), null, null, null, null);
        assertThrows(BizException.class, () -> service.version(9L));
    }

    @Test void extractionExplainsMissingUploadedResume() {
        ResumeService emptyResumes = new ResumeService(null, null) {
            @Override public Resume getMine() { return null; }
        };
        var service = new ResumeWorkbenchService(null, new ObjectMapper(), null, emptyResumes, null, null);
        BizException error = assertThrows(BizException.class, service::suggestionsFromCurrentResume);
        assertTrue(error.getMessage().contains("上传"));
    }

    private JobPositionMapper oneJob() {
        JobPosition job = new JobPosition();
        job.setId(1L);
        job.setName("Java 后端开发");
        job.setStatus(1);
        return (JobPositionMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { JobPositionMapper.class },
                (proxy, method, args) -> "selectById".equals(method.getName()) ? job : null);
    }
}
