package com.zhimian.config;

import com.zhimian.controller.TeachingController;
import com.zhimian.controller.TeacherAnalyticsController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TeachingRoleInterceptorTest {
    private final RoleInterceptor interceptor = new RoleInterceptor();
    private final TeachingController controller = new TeachingController(null, null, null);

    @AfterEach void clear() { UserContext.clear(); }

    @Test void teacherAndStudentEndpointsReturnForbiddenToWrongRole() throws Exception {
        Map<String, String> restricted = Map.ofEntries(
                Map.entry("create", "TEACHER"), Map.entry("edit", "TEACHER"),
                Map.entry("invite", "TEACHER"), Map.entry("archive", "TEACHER"),
                Map.entry("members", "TEACHER"), Map.entry("membership", "TEACHER"),
                Map.entry("students", "TEACHER"), Map.entry("summary", "TEACHER"),
                Map.entry("tasks", "TEACHER"), Map.entry("createTask", "TEACHER"),
                Map.entry("editTask", "TEACHER"), Map.entry("discard", "TEACHER"),
                Map.entry("task", "TEACHER"), Map.entry("supplement", "TEACHER"),
                Map.entry("override", "TEACHER"), Map.entry("publish", "TEACHER"),
                Map.entry("deadline", "TEACHER"), Map.entry("end", "TEACHER"),
                Map.entry("remind", "TEACHER"), Map.entry("review", "TEACHER"),
                Map.entry("join", "STUDENT"), Map.entry("mine", "STUDENT"),
                Map.entry("assignment", "STUDENT"), Map.entry("retry", "STUDENT"));
        for (var entry : restricted.entrySet()) {
            Method method = method(entry.getKey());
            assertNotNull(method.getAnnotation(RequireRole.class), entry.getKey());
            String allowed = entry.getValue();
            String denied = "STUDENT".equals(allowed) ? "TEACHER" : "STUDENT";
            assertAccess(method, denied, false);
            assertAccess(method, allowed, true);
            if ("TEACHER".equals(allowed)) assertAccess(method, "ADMIN", true);
        }
    }

    @Test void sharedEndpointsRemainAvailableAndUseServiceLevelResourceChecks() throws Exception {
        for (String name : new String[]{"classes", "report", "reviews", "messages", "read"}) {
            assertNull(method(name).getAnnotation(RequireRole.class), name);
            assertAccess(method(name), "STUDENT", true);
            assertAccess(method(name), "TEACHER", true);
        }
    }

    @Test void analyticsEndpointsRejectStudents() throws Exception {
        TeacherAnalyticsController analytics = new TeacherAnalyticsController(null);
        for (Method method : TeacherAnalyticsController.class.getDeclaredMethods()) {
            if (!method.getName().equals("overview") && !method.getName().equals("records")) continue;
            UserContext.set(1L, "STUDENT");
            MockHttpServletResponse forbidden = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(new MockHttpServletRequest(), forbidden,
                    new HandlerMethod(analytics, method)));
            assertEquals(403, forbidden.getStatus());
            UserContext.set(2L, "TEACHER");
            assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    new HandlerMethod(analytics, method)));
        }
    }

    private Method method(String name) {
        return java.util.Arrays.stream(TeachingController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    private void assertAccess(Method method, String role, boolean expected) throws Exception {
        UserContext.set(1L, role);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean actual = interceptor.preHandle(new MockHttpServletRequest(), response,
                new HandlerMethod(controller, method));
        assertEquals(expected, actual, method.getName() + " for " + role);
        if (!expected) assertEquals(403, response.getStatus());
    }
}
