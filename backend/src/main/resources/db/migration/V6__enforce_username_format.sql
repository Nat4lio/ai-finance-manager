DO $$
DECLARE
    short_user RECORD;
    candidate_username VARCHAR(64);
    suffix INTEGER;
BEGIN
    FOR short_user IN
        SELECT id
        FROM app_user
        WHERE length(username) < 3
        ORDER BY id
    LOOP
        candidate_username := 'u' || replace(short_user.id::text, '-', '');
        suffix := 0;
        WHILE EXISTS (
            SELECT 1
            FROM app_user
            WHERE lower(username) = lower(candidate_username)
              AND id <> short_user.id
        ) LOOP
            suffix := suffix + 1;
            candidate_username := 'u' || replace(short_user.id::text, '-', '') || '_' || suffix::text;
        END LOOP;

        UPDATE app_user
        SET username = candidate_username
        WHERE id = short_user.id;
    END LOOP;
END $$;

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_username_format
    CHECK (username ~ '^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$');