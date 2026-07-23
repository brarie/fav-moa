CREATE TABLE users (
    id         uuid PRIMARY KEY,
    email      varchar(320) NOT NULL UNIQUE,
    name       varchar(100),
    avatar_url text,
    google_id  varchar(64)  NOT NULL UNIQUE,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    family     uuid        NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
-- 재사용 감지 시 회전 체인(family) 전체를 무효화할 때 사용
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family);
