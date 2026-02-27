-- users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    email VARCHAR(254) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    lang_code VARCHAR(10) NOT NULL DEFAULT 'UND',
    timezone VARCHAR(40) NOT NULL DEFAULT 'UTC',
    theme VARCHAR(20) NOT NULL DEFAULT 'UND',
    pv_provider VARCHAR(20) NOT NULL DEFAULT 'UND',
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_login_at TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_users_lang_code CHECK (lang_code IN ('KO', 'EN', 'JA', 'ZH', 'LA', 'UND')),
    CONSTRAINT chk_users_timezone_not_blank CHECK (timezone <> ''),
    CONSTRAINT chk_users_theme CHECK (theme IN ('LIGHT', 'DARK', 'UND')),
    CONSTRAINT chk_users_pv_provider CHECK (
        pv_provider IN (
            'YOUTUBE',
            'NICONICO',
            'BILIBILI',
            'PIAPRO',
            'SOUNDCLOUD',
            'VIMEO',
            'BANDCAMP',
            'UND'
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
    canonical_name VARCHAR(255) NOT NULL,
    thumbnail_url TEXT,
    view_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    resource_type VARCHAR(20) NOT NULL,
    CONSTRAINT chk_resources_view_count CHECK (view_count >= 0),
    CONSTRAINT chk_resources_status CHECK (status IN ('ACTIVE', 'DRAFT')),
    CONSTRAINT chk_resources_resource_type CHECK (
        resource_type IN ('SONG', 'ARTIST', 'VOCAL', 'VOICEBANK', 'PLAYLIST')
    )
);

-- resource_names
CREATE TABLE resource_names (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    resource_id BIGINT NOT NULL,
    lang_code VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_resource_names_resource FOREIGN KEY (resource_id) REFERENCES resources (id) ON DELETE RESTRICT,
    CONSTRAINT chk_resource_names_lang_code CHECK (lang_code IN ('KO', 'EN', 'JA', 'ZH', 'LA', 'UND')),
    CONSTRAINT chk_resource_names_sort_order CHECK (sort_order >= 0),
    CONSTRAINT uk_resource_names_resource_lang_name UNIQUE (resource_id, lang_code, name)
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

-- histories
CREATE TABLE histories (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    resource_id BIGINT NOT NULL,
    revision INTEGER NOT NULL,
    base_revision INTEGER NOT NULL DEFAULT 0,
    action_type VARCHAR(20) NOT NULL,
    actor_user_id BIGINT,
    actor_guest_id BIGINT,
    is_snapshot BOOLEAN NOT NULL DEFAULT FALSE,
    patch_data JSONB,
    snapshot_data JSONB,
    content_hash CHAR(64) NOT NULL,
    CONSTRAINT fk_histories_resource FOREIGN KEY (resource_id) REFERENCES resources (id) ON DELETE RESTRICT,
    CONSTRAINT fk_histories_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_histories_actor_guest FOREIGN KEY (actor_guest_id) REFERENCES guests (id) ON DELETE RESTRICT,
    CONSTRAINT uk_histories_resource_revision UNIQUE (resource_id, revision),
    CONSTRAINT chk_histories_action_type CHECK (
        action_type IN ('CREATE', 'UPDATE', 'DELETE', 'RESTORE')
    ),
    CONSTRAINT chk_histories_revision CHECK (revision > 0),
    CONSTRAINT chk_histories_base_revision CHECK (
        base_revision >= 0 AND base_revision < revision
    ),
    CONSTRAINT chk_histories_actor_exclusive CHECK (
        NOT (actor_user_id IS NOT NULL AND actor_guest_id IS NOT NULL)
    ),
    CONSTRAINT chk_histories_snapshot_or_patch CHECK (
        (is_snapshot = TRUE AND snapshot_data IS NOT NULL AND patch_data IS NULL)
        OR
        (is_snapshot = FALSE AND patch_data IS NOT NULL)
    )
);

-- songs
CREATE TABLE songs (
    id BIGINT PRIMARY KEY,
    content TEXT,
    links JSONB,
    published_at TIMESTAMPTZ,
    song_type VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    CONSTRAINT fk_songs_resource FOREIGN KEY (id) REFERENCES resources (id) ON DELETE RESTRICT,
    CONSTRAINT chk_songs_links_array CHECK (links IS NULL OR jsonb_typeof(links) = 'array'),
    CONSTRAINT chk_songs_song_type CHECK (
        song_type IN ('ORIGINAL', 'COVER', 'REMIX', 'REMASTER', 'MASHUP', 'OTHER')
    )
);

-- song_lyrics
CREATE TABLE song_lyrics (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    song_id BIGINT NOT NULL,
    lang_codes TEXT[] NOT NULL,
    lyrics JSONB NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_song_lyrics_song FOREIGN KEY (song_id) REFERENCES songs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_song_lyrics_lang_codes_not_empty CHECK (cardinality(lang_codes) > 0),
    CONSTRAINT chk_song_lyrics_lang_codes_valid CHECK (
        lang_codes <@ ARRAY['KO', 'EN', 'JA', 'ZH', 'LA', 'UND']::TEXT[]
    ),
    CONSTRAINT chk_song_lyrics_not_json_null CHECK (jsonb_typeof(lyrics) <> 'null'),
    CONSTRAINT chk_song_lyrics_sort_order CHECK (sort_order >= 0)
);

-- playlists
CREATE TABLE playlists (
    id BIGINT PRIMARY KEY,
    content TEXT,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_playlists_resource FOREIGN KEY (id) REFERENCES resources (id) ON DELETE RESTRICT
);

-- playlist_songs
CREATE TABLE playlist_songs (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    playlist_id BIGINT NOT NULL,
    song_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_playlist_songs_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE RESTRICT,
    CONSTRAINT fk_playlist_songs_song FOREIGN KEY (song_id) REFERENCES songs (id) ON DELETE RESTRICT,
    CONSTRAINT uk_playlist_songs_playlist_song UNIQUE (playlist_id, song_id),
    CONSTRAINT uk_playlist_songs_playlist_sort_order UNIQUE (playlist_id, sort_order),
    CONSTRAINT chk_playlist_songs_sort_order CHECK (sort_order >= 0)
);

-- song_relations
CREATE TABLE song_relations (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    source_song_id BIGINT NOT NULL,
    target_song_id BIGINT NOT NULL,
    CONSTRAINT fk_song_relations_source_song FOREIGN KEY (source_song_id) REFERENCES songs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_song_relations_target_song FOREIGN KEY (target_song_id) REFERENCES songs (id) ON DELETE RESTRICT,
    CONSTRAINT uk_song_relations_source_target UNIQUE (source_song_id, target_song_id),
    CONSTRAINT chk_song_relations_source_not_target CHECK (source_song_id <> target_song_id)
);

-- song_pvs
CREATE TABLE song_pvs (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    song_id BIGINT NOT NULL,
    service VARCHAR(20) NOT NULL,
    video_key VARCHAR(100) NOT NULL,
    title VARCHAR(255),
    thumbnail_url TEXT,
    uploader_key VARCHAR(100),
    duration_seconds INTEGER,
    is_official BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_song_pvs_song FOREIGN KEY (song_id) REFERENCES songs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_song_pvs_service CHECK (
        service IN ('YOUTUBE', 'NICONICO', 'BILIBILI', 'PIAPRO', 'SOUNDCLOUD', 'VIMEO', 'BANDCAMP', 'OTHER')
    ),
    CONSTRAINT chk_song_pvs_duration_seconds CHECK (
        duration_seconds IS NULL OR duration_seconds >= 0
    ),
    CONSTRAINT chk_song_pvs_sort_order CHECK (sort_order >= 0)
);

-- song_pv_views
CREATE TABLE song_pv_views (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    song_pv_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_song_pv_views_song_pv FOREIGN KEY (song_pv_id) REFERENCES song_pvs (id) ON DELETE RESTRICT,
    CONSTRAINT uk_song_pv_views_song_pv UNIQUE (song_pv_id),
    CONSTRAINT chk_song_pv_views_view_count CHECK (view_count >= 0)
);

-- artists
CREATE TABLE artists (
    id BIGINT PRIMARY KEY,
    content TEXT,
    links JSONB,
    CONSTRAINT fk_artists_resource FOREIGN KEY (id) REFERENCES resources (id) ON DELETE RESTRICT,
    CONSTRAINT chk_artists_links_array CHECK (links IS NULL OR jsonb_typeof(links) = 'array')
);

-- artist_groups
CREATE TABLE artist_groups (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    group_artist_id BIGINT NOT NULL,
    member_artist_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_artist_groups_group_artist FOREIGN KEY (group_artist_id) REFERENCES artists (id) ON DELETE RESTRICT,
    CONSTRAINT fk_artist_groups_member_artist FOREIGN KEY (member_artist_id) REFERENCES artists (id) ON DELETE RESTRICT,
    CONSTRAINT uk_artist_groups_group_member UNIQUE (group_artist_id, member_artist_id),
    CONSTRAINT uk_artist_groups_group_sort_order UNIQUE (group_artist_id, sort_order),
    CONSTRAINT chk_artist_groups_group_not_member CHECK (group_artist_id <> member_artist_id),
    CONSTRAINT chk_artist_groups_sort_order CHECK (sort_order >= 0)
);

-- song_artists
CREATE TABLE song_artists (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    song_id BIGINT NOT NULL,
    artist_id BIGINT NOT NULL,
    role TEXT[] NOT NULL,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_song_artists_song FOREIGN KEY (song_id) REFERENCES songs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_song_artists_artist FOREIGN KEY (artist_id) REFERENCES artists (id) ON DELETE RESTRICT,
    CONSTRAINT uk_song_artists_song_artist_role UNIQUE (song_id, artist_id, role),
    CONSTRAINT chk_song_artists_role_not_empty CHECK (cardinality(role) > 0),
    CONSTRAINT chk_song_artists_role CHECK (
        role <@ ARRAY['PRODUCER', 'ARRANGER', 'COMPOSER', 'LYRICIST', 'OTHER']::TEXT[]
    ),
    CONSTRAINT chk_song_artists_sort_order CHECK (sort_order >= 0)
);

-- vocal_characters
CREATE TABLE vocal_characters (
    id BIGINT PRIMARY KEY,
    content TEXT,
    links JSONB,
    CONSTRAINT fk_vocal_characters_resource FOREIGN KEY (id) REFERENCES resources (id) ON DELETE RESTRICT,
    CONSTRAINT chk_vocal_characters_links_array CHECK (links IS NULL OR jsonb_typeof(links) = 'array')
);

-- vocal_voicebanks
CREATE TABLE vocal_voicebanks (
    id BIGINT PRIMARY KEY,
    content TEXT,
    links JSONB,
    voicebank_typ VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    CONSTRAINT fk_vocal_voicebanks_resource FOREIGN KEY (id) REFERENCES resources (id) ON DELETE RESTRICT,
    CONSTRAINT chk_vocal_voicebanks_links_array CHECK (links IS NULL OR jsonb_typeof(links) = 'array'),
    CONSTRAINT chk_vocal_voicebanks_voicebank_typ CHECK (
        voicebank_typ IN (
            'VOCALOID',
            'UTAU',
            'CEVIO',
            'SYNTHESIZER_V',
            'NEUTRINO',
            'VOISONA',
            'VOICEROID',
            'VOICEVOX',
            'ACE',
            'AI_VOICE',
            'OTHER'
        )
    )
);

-- song_vocals
CREATE TABLE song_vocals (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    song_id BIGINT NOT NULL,
    vocal_id BIGINT NOT NULL,
    voicebank_id BIGINT,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_song_vocals_song FOREIGN KEY (song_id) REFERENCES songs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_song_vocals_vocal FOREIGN KEY (vocal_id) REFERENCES vocal_characters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_song_vocals_voicebank FOREIGN KEY (voicebank_id) REFERENCES vocal_voicebanks (id) ON DELETE RESTRICT,
    CONSTRAINT chk_song_vocals_sort_order CHECK (sort_order >= 0)
);
