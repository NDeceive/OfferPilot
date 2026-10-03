package com.zhimian.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@DependsOn("studentBackendSchema")
@RequiredArgsConstructor
public class AccountTokenService {
    private final JdbcTemplate jdbc;

    public long version(long userId) {
        var versions = jdbc.query("SELECT version FROM account_token_version WHERE user_id=?",
                (rs, n) -> rs.getLong(1), userId);
        return versions.isEmpty() ? 0 : versions.get(0);
    }

    public void revoke(long userId) {
        jdbc.update("INSERT INTO account_token_version(user_id,version) VALUES (?,1) " +
                "ON DUPLICATE KEY UPDATE version=version+1", userId);
    }
}
