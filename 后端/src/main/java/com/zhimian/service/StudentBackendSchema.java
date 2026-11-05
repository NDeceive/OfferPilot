package com.zhimian.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Additive schema only; never runs the development seed/reset script. */
@Component
@RequiredArgsConstructor
public class StudentBackendSchema {
    private final JdbcTemplate jdbc;

    @PostConstruct
    void initialize() {
        separateLegacyAudioTable();
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_interview_stages.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_interview_plan.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_expressions.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/student_backend.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_teaching_linkage.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/teaching_alignment.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/resume_workbench.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/resume_analysis.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_meeting.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_followup_chain.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_voice_analysis.sql"))
                .execute(jdbc.getDataSource());
        ensureAnswerAudioSchema();
        new ResourceDatabasePopulator(new ClassPathResource("db/voice_retention.sql"))
                .execute(jdbc.getDataSource());
    }

    /**
     * 旧库的作答音频表按 (session_id, round_no) 唯一，追问回答会覆盖主回答。
     * 这里把唯一键升级为 (session_id, round_no, answer_seq)，同时兼容已手工改过的库。
     */
    void ensureAnswerAudioSchema() {
        Integer tables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='interview_round_audio'", Integer.class);
        if (!Integer.valueOf(1).equals(tables)) return;

        Integer answerSeqColumns = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='interview_round_audio' AND column_name='answer_seq'",
                Integer.class);
        if (!Integer.valueOf(1).equals(answerSeqColumns)) {
            jdbc.execute("ALTER TABLE interview_round_audio "
                    + "ADD COLUMN answer_seq INT NOT NULL DEFAULT 0 AFTER round_no");
        }

        List<Map<String, Object>> uniqueIndexes = jdbc.queryForList(
                "SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols "
                        + "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                        + "AND table_name='interview_round_audio' AND non_unique=0 GROUP BY index_name");
        boolean segmentKey = false;
        for (Map<String, Object> index : uniqueIndexes) {
            String columns = String.valueOf(index.get("cols"));
            if ("session_id,round_no".equals(columns)) {
                jdbc.execute("ALTER TABLE interview_round_audio DROP INDEX `" + index.get("index_name") + "`");
            } else if ("session_id,round_no,answer_seq".equals(columns)) {
                segmentKey = true;
            }
        }
        if (!segmentKey) {
            jdbc.execute("ALTER TABLE interview_round_audio ADD UNIQUE KEY uk_audio_session_round_seq "
                    + "(session_id, round_no, answer_seq)");
        }
    }

    /** The old prompt/blob store and the current per-round file store used the same name. */
    void separateLegacyAudioTable() {
        Integer roundColumns = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='interview_answer_audio' AND column_name='round_no'", Integer.class);
        Integer newTables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='interview_round_audio'", Integer.class);
        if (Integer.valueOf(1).equals(roundColumns) && Integer.valueOf(0).equals(newTables)) {
            jdbc.execute("RENAME TABLE interview_answer_audio TO interview_round_audio");
        }
    }
}
