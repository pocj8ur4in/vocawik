-- users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    nickname VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'UNSET',
    timezone VARCHAR(40) NOT NULL DEFAULT 'UNSET',
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_login_at TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT chk_users_locale_not_blank CHECK (locale <> ''),
    CONSTRAINT chk_users_timezone_not_blank CHECK (timezone <> '')
);

-- user_auth_providers
CREATE TABLE user_auth_providers (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(191) NOT NULL,
    email VARCHAR(254),
    CONSTRAINT fk_user_auth_providers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_user_auth_provider_provider_provider_user_id UNIQUE (provider, provider_user_id),
    CONSTRAINT chk_user_auth_providers_provider CHECK (provider IN ('GOOGLE'))
);

CREATE INDEX idx_user_auth_providers_user_id ON user_auth_providers (user_id);

-- guests
CREATE TABLE guests (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_seen_at TIMESTAMP,
    ip_hash VARCHAR(64) NOT NULL,
    CONSTRAINT chk_guests_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE UNIQUE INDEX uk_guests_ip_hash ON guests (ip_hash);
