UPDATE lectures lecture
LEFT JOIN (
    SELECT application.lecture_id, COUNT(*) AS participant_count
    FROM (
        SELECT morning_lecture_id AS lecture_id
        FROM participant_lectures
        WHERE morning_lecture_id IS NOT NULL

        UNION ALL

        SELECT afternoon_lecture_id AS lecture_id
        FROM participant_lectures
        WHERE afternoon_lecture_id IS NOT NULL
    ) application
    GROUP BY application.lecture_id
) actual_count ON actual_count.lecture_id = lecture.id
SET lecture.participant_count = COALESCE(actual_count.participant_count, 0)
WHERE lecture.participant_count <> COALESCE(actual_count.participant_count, 0);
