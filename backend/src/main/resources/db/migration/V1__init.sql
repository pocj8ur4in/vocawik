-- users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    email VARCHAR(254) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    lang_code VARCHAR(10) NOT NULL DEFAULT 'UNSET',
    timezone VARCHAR(40) NOT NULL DEFAULT 'UTC',
    theme VARCHAR(20) NOT NULL DEFAULT 'UNSET',
    song_pv_provider VARCHAR(20) NOT NULL DEFAULT 'UNSET',
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_login_at TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_users_lang_code CHECK (lang_code IN ('KO', 'EN', 'JA', 'ZH', 'UNSET')),
    CONSTRAINT chk_users_timezone_not_blank CHECK (timezone <> ''),
    CONSTRAINT chk_users_theme CHECK (theme IN ('LIGHT', 'DARK', 'UNSET')),
    CONSTRAINT chk_users_song_pv_provider CHECK (
        song_pv_provider IN (
            'YOUTUBE',
            'NICONICO',
            'BILIBILI',
            'PIAPRO',
            'SOUNDCLOUD',
            'VIMEO',
            'BANDCAMP',
            'UNSET'
        )
    )
);

CREATE UNIQUE INDEX uk_users_email_lower_live
    ON users (LOWER(email))
    WHERE is_deleted = FALSE;

-- user_auth_providers
CREATE TABLE user_auth_providers (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
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

-- guests
CREATE TABLE guests (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMP,
    ip_hash VARCHAR(64) NOT NULL,
    CONSTRAINT chk_guests_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE UNIQUE INDEX uk_guests_ip_hash_live
    ON guests (ip_hash)
    WHERE is_deleted = FALSE;

-- resources
CREATE TABLE resources (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    resource_type VARCHAR(20) NOT NULL,
    CONSTRAINT chk_resources_view_count CHECK (view_count >= 0),
    CONSTRAINT chk_resources_status CHECK (status IN ('ACTIVE', 'DRAFT')),
    CONSTRAINT chk_resources_resource_type CHECK (
        resource_type IN ('SONG', 'ARTIST', 'VOCAL', 'PLAYLIST')
    )
);

-- acls
CREATE TABLE acls (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    resource_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    subject_value VARCHAR(191) NOT NULL DEFAULT '',
    effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
    priority INTEGER NOT NULL DEFAULT 100,
    expires_at TIMESTAMP,
    CONSTRAINT fk_acls_resource FOREIGN KEY (resource_id) REFERENCES resources (id) ON DELETE RESTRICT,
    CONSTRAINT chk_acls_action CHECK (
        action IN ('READ', 'EDIT', 'EDIT_REQUEST', 'DELETE', 'DEBATE_CREATE', 'DEBATE_COMMENT', 'ACL')
    ),
    CONSTRAINT chk_acls_subject_type CHECK (
        subject_type IN (
            'ANONYMOUS',
            'USER',
            'USER_15',
            'USER_VERIFIED',
            'ADMIN',
            'USER_ID',
            'GUEST_ID',
            'ACL_GROUP'
        )
    ),
    CONSTRAINT chk_acls_subject_value CHECK (
        (
            subject_type IN ('ANONYMOUS', 'USER', 'USER_15', 'USER_VERIFIED', 'ADMIN')
            AND subject_value = ''
        )
        OR (
            subject_type IN ('USER_ID', 'GUEST_ID', 'ACL_GROUP')
            AND subject_value <> ''
        )
    ),
    CONSTRAINT chk_acls_effect CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT chk_acls_priority CHECK (priority >= 0),
    CONSTRAINT uk_acls_resource_action_subject_priority UNIQUE (
        resource_id, action, subject_type, subject_value, priority
    )
);
-- debates
CREATE TABLE debates (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    resource_id BIGINT NOT NULL,
    actor_user_id BIGINT,
    actor_guest_id BIGINT,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_debates_resource FOREIGN KEY (resource_id) REFERENCES resources (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debates_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debates_actor_guest FOREIGN KEY (actor_guest_id) REFERENCES guests (id) ON DELETE RESTRICT,
    CONSTRAINT chk_debates_actor_exclusive CHECK (
        NOT (actor_user_id IS NOT NULL AND actor_guest_id IS NOT NULL)
    ),
    CONSTRAINT chk_debates_actor_required CHECK (
        actor_user_id IS NOT NULL OR actor_guest_id IS NOT NULL
    ),
    CONSTRAINT chk_debates_status CHECK (status IN ('OPEN', 'CLOSED', 'ARCHIVED'))
);

-- debate_comments
CREATE TABLE debate_comments (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    debate_id BIGINT NOT NULL,
    parent_comment_id BIGINT,
    actor_user_id BIGINT,
    actor_guest_id BIGINT,
    content TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_debate_comments_debate FOREIGN KEY (debate_id) REFERENCES debates (id) ON DELETE RESTRICT,
    CONSTRAINT uk_debate_comments_id_debate UNIQUE (id, debate_id),
    CONSTRAINT fk_debate_comments_parent_same_debate FOREIGN KEY (parent_comment_id, debate_id)
        REFERENCES debate_comments (id, debate_id) ON DELETE RESTRICT,
    CONSTRAINT fk_debate_comments_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debate_comments_actor_guest FOREIGN KEY (actor_guest_id) REFERENCES guests (id) ON DELETE RESTRICT,
    CONSTRAINT chk_debate_comments_actor_exclusive CHECK (
        NOT (actor_user_id IS NOT NULL AND actor_guest_id IS NOT NULL)
    ),
    CONSTRAINT chk_debate_comments_actor_required CHECK (
        actor_user_id IS NOT NULL OR actor_guest_id IS NOT NULL
    ),
    CONSTRAINT chk_debate_comments_parent_not_self CHECK (
        parent_comment_id IS NULL OR parent_comment_id <> id
    ),
    CONSTRAINT chk_debate_comments_revision CHECK (revision > 0)
);
