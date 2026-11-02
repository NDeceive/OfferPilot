package com.zhimian.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

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
        new ResourceDatabasePopulator(new ClassPathResource("db/voice_retention.sql"))
                .execute(jdbc.getDataSource());
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
