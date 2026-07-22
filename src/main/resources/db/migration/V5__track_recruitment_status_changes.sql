ALTER TABLE recruitments
    ADD COLUMN status_changed_at DATETIME(6) NULL AFTER status;

UPDATE recruitments
SET status_changed_at = CURRENT_TIMESTAMP(6)
WHERE status_changed_at IS NULL;

ALTER TABLE recruitments
    MODIFY COLUMN status_changed_at DATETIME(6) NOT NULL;
