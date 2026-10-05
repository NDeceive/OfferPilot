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
        new ResourceDatabasePopulator(new ClassPathResource("db/student_backend.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_teaching_linkage.sql"))
                .execute(jdbc.getDataSource());
        new ResourceDatabasePopulator(new ClassPathResource("db/teaching_alignment.sql"))
                .execute(jdbc.getDataSource());
    }
}
