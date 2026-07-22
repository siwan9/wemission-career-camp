-- mysql career_camp -e "SET @recruitment_id=1; SET @run_id='run01'; SOURCE verify-registration-race.sql;"

SELECT
    l.id AS lecture_id,
    l.type AS lecture_type,
    l.speaker_name,
    l.max_capacity,
    l.participant_count AS stored_participant_count,
    (
        SELECT COUNT(*)
        FROM participant_lectures pl
        WHERE pl.morning_lecture_id = l.id
           OR pl.afternoon_lecture_id = l.id
    ) AS actual_confirmed_count,
    (
        SELECT COUNT(*)
        FROM participant_lecture_drafts d
        WHERE d.lecture_id = l.id
          AND d.expires_at > NOW(6)
    ) AS active_draft_count,
    CASE
        WHEN l.participant_count > l.max_capacity THEN 'OVER_CAPACITY'
        WHEN l.participant_count <> (
            SELECT COUNT(*)
            FROM participant_lectures pl
            WHERE pl.morning_lecture_id = l.id
               OR pl.afternoon_lecture_id = l.id
        ) THEN 'COUNTER_MISMATCH'
        ELSE 'OK'
    END AS integrity_status
FROM lectures l
WHERE l.recruitment_id = @recruitment_id
ORDER BY l.type, l.sort_order, l.id;

SELECT
    COUNT(*) AS load_test_participant_count,
    COUNT(pl.id) AS load_test_application_count
FROM participants p
LEFT JOIN participant_lectures pl ON pl.participant_id = p.id
WHERE p.recruitment_id = @recruitment_id
  AND p.name LIKE CONCAT('loadtest-', @run_id, '-%');

SELECT
    COUNT(*) AS late_application_count
FROM participants p
JOIN participant_lectures pl ON pl.participant_id = p.id
WHERE p.recruitment_id = @recruitment_id
  AND p.name LIKE CONCAT('loadtest-', @run_id, '-late-%');

SELECT
    p.id AS participant_id,
    p.name,
    p.phone_number,
    pl.id AS participant_lecture_id,
    pl.morning_lecture_id,
    pl.afternoon_lecture_id
FROM participants p
LEFT JOIN participant_lectures pl ON pl.participant_id = p.id
WHERE p.recruitment_id = @recruitment_id
  AND p.name LIKE CONCAT('loadtest-', @run_id, '-%')
  AND pl.id IS NULL;
