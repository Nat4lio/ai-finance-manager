ALTER TABLE app_user ADD COLUMN username VARCHAR(64);

WITH legacy_candidates AS (
    SELECT
        id,
        COALESCE(
            NULLIF(
                regexp_replace(
                    regexp_replace(lower(split_part(email, '@', 1)), '[^a-z0-9._-]', '', 'g'),
                    '^[^a-z0-9]+',
                    '',
                    'g'
                ),
                ''
            ),
            'user'
        ) AS base_username
    FROM app_user
), legacy_users AS (
    SELECT
        id,
        left(base_username, 64) AS base_username,
        left(base_username, 24) || '_' || replace(id::text, '-', '') AS fallback_username,
        count(*) OVER (PARTITION BY left(base_username, 64)) AS base_count
    FROM legacy_candidates
)
UPDATE app_user AS app_user
SET username = CASE
        WHEN legacy_users.base_count = 1
            AND NOT EXISTS (
                SELECT 1
                FROM legacy_users AS generated_names
                WHERE generated_names.fallback_username = legacy_users.base_username
            )
        THEN legacy_users.base_username
        ELSE legacy_users.fallback_username
    END
FROM legacy_users
WHERE app_user.id = legacy_users.id;

ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;
CREATE UNIQUE INDEX uq_app_user_username_lower ON app_user (lower(username));

DROP TABLE auth_action_code;
ALTER TABLE app_user DROP COLUMN email_verified;
ALTER TABLE app_user DROP COLUMN email;