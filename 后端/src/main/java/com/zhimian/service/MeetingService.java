package com.zhimian.service;

import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.entity.Meeting;
import com.zhimian.entity.MeetingAdvice;
import com.zhimian.mapper.MeetingAdviceMapper;
import com.zhimian.mapper.MeetingMapper;
import com.zhimian.ws.MeetingRoomRegistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 企业端会议：建会 / 进会预检 / 建议落库。
 * 所有越权判断按登录用户 ID 在本类完成（参照 TeachingService 的 ownClass 模式）。
 */
@Service
@RequiredArgsConstructor
public class MeetingService {

    public record CreateInput(@NotBlank(message = "会议标题不能为空") @Size(max = 80) String title, Long jobId) {
    }

    public record AdviceInput(@NotBlank(message = "建议内容不能为空") @Size(max = 2000) String content,
                              Long targetUserId) {
    }

    /** 无歧义字母表：去掉 I/O/0/1，避免口头报会议号时听错 */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate db;
    private final MeetingMapper meetings;
    private final MeetingAdviceMapper adviceMapper;
    private final MeetingRoomRegistry registry;

    // ---------- 会议号工具 ----------

    /** 归一化：大写、去掉空格与连字符（展示形态 3F7K-2Q9A 统一到这里比较） */
    public static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
    }

    /** 展示形态：3F7K-2Q9A（前四位-后四位） */
    public static String displayCode(String code) {
        return code == null || code.length() != CODE_LENGTH ? code : code.substring(0, 4) + "-" + code.substring(4);
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    // ---------- 会议 ----------

    /** 建会（企业/教师/管理员）。唯一索引兜底，撞号换号重试。 */
    public Map<String, Object> create(CreateInput input) {
        staff();
        if (input.jobId() != null
                && db.queryForObject("SELECT COUNT(*) FROM job_position WHERE id=?", Integer.class, input.jobId()) == 0) {
            throw new BizException("岗位不存在");
        }
        Meeting m = new Meeting();
        m.setTitle(input.title().trim());
        m.setJobId(input.jobId());
        m.setHostId(user());
        m.setStatus("OPEN");
        for (int attempt = 1; ; attempt++) {
            m.setId(null);
            m.setCode(randomCode());
            try {
                meetings.insert(m);
                break;
            } catch (DuplicateKeyException collision) {
                // 8 位 32 字母表约 1.1e12 组合，撞号概率极小，唯一索引兜底后重试三次
                if (attempt >= 3) {
                    throw new BizException("会议号生成失败，请重试");
                }
            }
        }
        db.update("UPDATE meeting SET started_at=NOW() WHERE id=?", m.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId());
        out.put("code", m.getCode());
        out.put("codeDisplay", displayCode(m.getCode()));
        out.put("title", m.getTitle());
        out.put("jobId", m.getJobId());
        out.put("hostId", m.getHostId());
        out.put("status", m.getStatus());
        out.put("participantCount", 0);
        return out;
    }

    /** 按会议号查会议（含发起人昵称）。格式错或不存在返回 null，由调用方决定错误文案。 */
    public Map<String, Object> findByCode(String rawCode) {
        String code = normalizeCode(rawCode);
        if (code.length() != CODE_LENGTH) {
            return null;
        }
        List<Map<String, Object>> found = rows(
                "SELECT m.*, COALESCE(NULLIF(u.nickname,''), u.username) host_name FROM meeting m "
                        + "JOIN sys_user u ON u.id = m.host_id WHERE m.code = ?", code);
        return found.isEmpty() ? null : found.get(0);
    }

    /** 进会预检（任意登录用户）：返回可进入性，不含任何建议内容。 */
    public Map<String, Object> preview(String rawCode) {
        Map<String, Object> m = findByCode(rawCode);
        if (m == null) {
            throw new BizException("会议号不存在");
        }
        boolean open = "OPEN".equals(m.get("status"));
        int count = registry.size(String.valueOf(m.get("code")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id(m, "id"));
        out.put("code", m.get("code"));
        out.put("codeDisplay", displayCode(String.valueOf(m.get("code"))));
        out.put("title", m.get("title"));
        out.put("status", m.get("status"));
        out.put("hostId", m.get("hostId"));
        out.put("hostName", m.get("hostName"));
        out.put("jobId", m.get("jobId"));
        out.put("participantCount", count);
        out.put("joinable", open && count < MeetingRoomRegistry.MAX_PEERS);
        return out;
    }

    /** 我参与的会议：我发起的 + 我参加过的。 */
    public Map<String, Object> mine() {
        long uid = user();
        List<Map<String, Object>> hosted = rows(
                "SELECT m.*, COALESCE(NULLIF(u.nickname,''), u.username) host_name, "
                        + "(SELECT COUNT(*) FROM meeting_participant p WHERE p.meeting_id = m.id) participant_count "
                        + "FROM meeting m JOIN sys_user u ON u.id = m.host_id WHERE m.host_id = ? ORDER BY m.id DESC LIMIT 50", uid);
        List<Map<String, Object>> joined = rows(
                "SELECT m.*, COALESCE(NULLIF(u.nickname,''), u.username) host_name, p.join_time, p.leave_time "
                        + "FROM meeting_participant p JOIN meeting m ON m.id = p.meeting_id "
                        + "JOIN sys_user u ON u.id = m.host_id WHERE p.user_id = ? ORDER BY p.id DESC LIMIT 50", uid);
        return Map.of("hosted", hosted, "joined", joined);
    }

    /** 会议详情（发起人 / 参会者 / ADMIN）：基本信息 + 参会记录 + 建议。 */
    public Map<String, Object> detail(long meetingId) {
        Map<String, Object> m = requireAccess(meetingId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id(m, "id"));
        out.put("code", m.get("code"));
        out.put("codeDisplay", displayCode(String.valueOf(m.get("code"))));
        out.put("title", m.get("title"));
        out.put("status", m.get("status"));
        out.put("hostId", m.get("hostId"));
        out.put("hostName", m.get("hostName"));
        out.put("jobId", m.get("jobId"));
        out.put("createdAt", m.get("createdAt"));
        out.put("endedAt", m.get("endedAt"));
        out.put("participants", rows(
                "SELECT p.*, COALESCE(NULLIF(u.nickname,''), u.username) name FROM meeting_participant p "
                        + "JOIN sys_user u ON u.id = p.user_id WHERE p.meeting_id = ? ORDER BY p.id", meetingId));
        out.put("advice", adviceRows(meetingId));
        return out;
    }

    /** 结束会议：仅发起人（ADMIN 可代为结束）；重复调用幂等。 */
    @Transactional
    public void end(long meetingId) {
        Map<String, Object> m = requireAccess(meetingId);
        if (id(m, "hostId") != user() && !"ADMIN".equals(role())) {
            throw new BizException("只有会议发起人可以结束会议");
        }
        if ("ENDED".equals(m.get("status"))) {
            return;
        }
        db.update("UPDATE meeting SET status='ENDED', ended_at=NOW() WHERE id=?", meetingId);
        db.update("UPDATE meeting_participant SET leave_time=NOW() WHERE meeting_id=? AND leave_time IS NULL", meetingId);
    }

    /** 参会记录（WS 进房时调用）：重复进房更新时间戳，不产生新行。 */
    public void touchParticipant(long meetingId, long userId, String name, String role) {
        db.update("INSERT INTO meeting_participant(meeting_id,user_id,display_name,role,join_time,leave_time) "
                        + "VALUES(?,?,?,?,NOW(),NULL) ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), "
                        + "role=VALUES(role), join_time=NOW(), leave_time=NULL",
                meetingId, userId, name, role);
    }

    /** 离场记录（WS 离房/断线时调用）：仅当尚未记录过离场时间。 */
    public void markLeft(long meetingId, long userId) {
        db.update("UPDATE meeting_participant SET leave_time=NOW() WHERE meeting_id=? AND user_id=? AND leave_time IS NULL",
                meetingId, userId);
    }

    // ---------- 建议 ----------

    /** 建议列表（发起人 / 参会者）。学生只看面向全体 + 点名给自己的。 */
    public List<Map<String, Object>> adviceList(long meetingId) {
        requireAccess(meetingId);
        return adviceRows(meetingId);
    }

    private List<Map<String, Object>> adviceRows(long meetingId) {
        String sql = "SELECT a.*, COALESCE(NULLIF(u.nickname,''), u.username) author_name FROM meeting_advice a "
                + "JOIN sys_user u ON u.id = a.author_id WHERE a.meeting_id = ?";
        if ("STUDENT".equals(role())) {
            return rows(sql + " AND (a.target_user_id IS NULL OR a.target_user_id = ?) ORDER BY a.id",
                    meetingId, user());
        }
        return rows(sql + " ORDER BY a.id", meetingId);
    }

    /** 写建议（企业/教师/管理员）：落库并返回含作者名的新行，供 WS 广播回显。
     *  会议结束后也允许补写（面试官事后整理点评是常态），权限仍是 host/participant/ADMIN。 */
    public Map<String, Object> postAdvice(long meetingId, AdviceInput input) {
        staff();
        requireAccess(meetingId);
        if (input.targetUserId() != null) {
            Integer isMember = db.queryForObject(
                    "SELECT COUNT(*) FROM meeting_participant WHERE meeting_id=? AND user_id=?",
                    Integer.class, meetingId, input.targetUserId());
            if (isMember == null || isMember == 0) {
                throw new BizException("建议对象不在该会议中");
            }
        }
        MeetingAdvice a = new MeetingAdvice();
        a.setMeetingId(meetingId);
        a.setAuthorId(user());
        a.setTargetUserId(input.targetUserId());
        a.setContent(input.content().trim());
        // 应用侧定时间：WS 广播回显带上 createdAt，与列表查询（a.*）字段一致，
        // 前端实时条目才不会显示「—」而刷新后又出现时间
        a.setCreatedAt(LocalDateTime.now());
        adviceMapper.insert(a);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", a.getId());
        out.put("meetingId", meetingId);
        out.put("authorId", a.getAuthorId());
        out.put("authorName", db.queryForObject(
                "SELECT COALESCE(NULLIF(nickname,''), username) FROM sys_user WHERE id=?", String.class, user()));
        out.put("targetUserId", a.getTargetUserId());
        out.put("content", a.getContent());
        out.put("createdAt", a.getCreatedAt());
        return out;
    }

    // ---------- 权限与工具 ----------

    /** 会议访问权：发起人 / 参会者 / ADMIN。 */
    private Map<String, Object> requireAccess(long meetingId) {
        List<Map<String, Object>> found = rows(
                "SELECT m.*, COALESCE(NULLIF(u.nickname,''), u.username) host_name FROM meeting m "
                        + "JOIN sys_user u ON u.id = m.host_id WHERE m.id = ?", meetingId);
        if (found.isEmpty()) {
            throw new BizException("会议不存在");
        }
        Map<String, Object> m = found.get(0);
        long uid = user();
        if (id(m, "hostId") == uid || "ADMIN".equals(role())) {
            return m;
        }
        Integer joined = db.queryForObject(
                "SELECT COUNT(*) FROM meeting_participant WHERE meeting_id=? AND user_id=?",
                Integer.class, meetingId, uid);
        if (joined == null || joined == 0) {
            throw new BizException("无权查看该会议");
        }
        return m;
    }

    /** 写建议等管理动作的身份要求（与 Controller 上的 @RequireRole 双保险） */
    private void staff() {
        if (!Set.of("ENTERPRISE", "TEACHER", "ADMIN").contains(String.valueOf(role()))) {
            throw new BizException("需要企业或教师身份");
        }
    }

    private long user() {
        return UserContext.getUserId();
    }

    private String role() {
        return UserContext.getRole();
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return db.queryForList(sql, args).stream().map(this::camel).toList();
    }

    private Map<String, Object> camel(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            StringBuilder key = new StringBuilder();
            boolean upper = false;
            for (char c : k.toCharArray()) {
                if (c == '_') {
                    upper = true;
                } else {
                    key.append(upper ? Character.toUpperCase(c) : c);
                    upper = false;
                }
            }
            out.put(key.toString(), v instanceof Timestamp ts ? ts.toLocalDateTime() : v);
        });
        return out;
    }

    private static long id(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
