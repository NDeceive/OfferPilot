CREATE TABLE IF NOT EXISTS user_career_profile (
    user_id BIGINT PRIMARY KEY,
    stage VARCHAR(20) NOT NULL DEFAULT '',
    school VARCHAR(100) NOT NULL DEFAULT '',
    major VARCHAR(100) NOT NULL DEFAULT '',
    graduation_year VARCHAR(4) NOT NULL DEFAULT '',
    target_job_id BIGINT NULL,
    target_company VARCHAR(100) NOT NULL DEFAULT '',
    avatar_data MEDIUMBLOB NULL
);
