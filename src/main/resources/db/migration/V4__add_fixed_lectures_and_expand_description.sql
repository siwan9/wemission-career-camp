ALTER TABLE lectures
    MODIFY COLUMN description TEXT NOT NULL;

CREATE TABLE recruitment_participant_type_fixed_lectures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recruitment_participant_type_id BIGINT NOT NULL,
    lecture_type VARCHAR(16) NOT NULL,
    lecture_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rpt_fixed_lectures_rule_type (recruitment_participant_type_id, lecture_type),
    UNIQUE KEY uk_rpt_fixed_lectures_rule_lecture (recruitment_participant_type_id, lecture_id),
    INDEX idx_rpt_fixed_lectures_lecture (lecture_id),
    CONSTRAINT fk_rpt_fixed_lectures_rule
        FOREIGN KEY (recruitment_participant_type_id) REFERENCES recruitment_participant_types (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_rpt_fixed_lectures_lecture
        FOREIGN KEY (lecture_id) REFERENCES lectures (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
