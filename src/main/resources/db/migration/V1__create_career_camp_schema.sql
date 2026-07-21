CREATE TABLE IF NOT EXISTS recruitments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    notice VARCHAR(255) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_registration_slot TINYINT
        GENERATED ALWAYS AS (CASE WHEN status IN ('OPEN', 'WAITING') THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recruitments_single_active (active_registration_slot),
    INDEX idx_recruitments_status_id (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participant_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_participant_types_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS recruitment_churches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recruitment_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_recruitment_churches_recruitment_sort (recruitment_id, sort_order, id),
    CONSTRAINT fk_recruitment_churches_recruitment
        FOREIGN KEY (recruitment_id) REFERENCES recruitments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS recruitment_participant_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recruitment_id BIGINT NOT NULL,
    participant_type_id BIGINT NOT NULL,
    can_select_morning_lecture BOOLEAN NOT NULL,
    can_select_afternoon_lecture BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recruitment_participant_types_recruitment_type (recruitment_id, participant_type_id),
    CONSTRAINT fk_recruitment_participant_types_recruitment
        FOREIGN KEY (recruitment_id) REFERENCES recruitments (id),
    CONSTRAINT fk_recruitment_participant_types_participant_type
        FOREIGN KEY (participant_type_id) REFERENCES participant_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lectures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recruitment_id BIGINT NOT NULL,
    speaker_name VARCHAR(255) NOT NULL,
    speaker_job VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    type VARCHAR(16) NOT NULL,
    is_open BOOLEAN NOT NULL,
    max_capacity INT NOT NULL,
    participant_count INT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_lectures_recruitment_type_open_sort (recruitment_id, type, is_open, sort_order, id),
    CONSTRAINT fk_lectures_recruitment
        FOREIGN KEY (recruitment_id) REFERENCES recruitments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    recruitment_church_id BIGINT NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    recruitment_id BIGINT NOT NULL,
    participant_type_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_participants_lookup (recruitment_id, recruitment_church_id, name, phone_number),
    CONSTRAINT fk_participants_recruitment
        FOREIGN KEY (recruitment_id) REFERENCES recruitments (id),
    CONSTRAINT fk_participants_recruitment_church
        FOREIGN KEY (recruitment_church_id) REFERENCES recruitment_churches (id),
    CONSTRAINT fk_participants_participant_type
        FOREIGN KEY (participant_type_id) REFERENCES participant_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participant_lectures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    participant_id BIGINT NOT NULL,
    morning_lecture_id BIGINT NULL,
    morning_lecture_apply_at DATETIME(6) NULL,
    afternoon_lecture_id BIGINT NULL,
    afternoon_lecture_apply_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_participant_lectures_participant (participant_id),
    INDEX idx_participant_lectures_morning (morning_lecture_id),
    INDEX idx_participant_lectures_afternoon (afternoon_lecture_id),
    CONSTRAINT fk_participant_lectures_participant
        FOREIGN KEY (participant_id) REFERENCES participants (id),
    CONSTRAINT fk_participant_lectures_morning
        FOREIGN KEY (morning_lecture_id) REFERENCES lectures (id),
    CONSTRAINT fk_participant_lectures_afternoon
        FOREIGN KEY (afternoon_lecture_id) REFERENCES lectures (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participant_lecture_drafts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    draft_token VARCHAR(255) NOT NULL,
    participant_id BIGINT NULL,
    recruitment_id BIGINT NOT NULL,
    lecture_id BIGINT NOT NULL,
    lecture_type VARCHAR(16) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_participant_lecture_drafts_token_type (draft_token, lecture_type),
    INDEX idx_participant_lecture_drafts_lecture_expires (lecture_id, expires_at),
    INDEX idx_participant_lecture_drafts_token_expires (draft_token, expires_at),
    INDEX idx_participant_lecture_drafts_expires (expires_at),
    INDEX idx_participant_lecture_drafts_recruitment_expires_lecture (recruitment_id, expires_at, lecture_id),
    INDEX idx_participant_lecture_drafts_participant (participant_id),
    CONSTRAINT fk_participant_lecture_drafts_participant
        FOREIGN KEY (participant_id) REFERENCES participants (id),
    CONSTRAINT fk_participant_lecture_drafts_recruitment
        FOREIGN KEY (recruitment_id) REFERENCES recruitments (id),
    CONSTRAINT fk_participant_lecture_drafts_lecture
        FOREIGN KEY (lecture_id) REFERENCES lectures (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admins (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_admins_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO participant_types (type)
SELECT 'STUDENT'
WHERE NOT EXISTS (SELECT 1 FROM participant_types WHERE type = 'STUDENT');

INSERT INTO participant_types (type)
SELECT 'TEACHER'
WHERE NOT EXISTS (SELECT 1 FROM participant_types WHERE type = 'TEACHER');
