package com.vocawik.domain.user;

import com.vocawik.common.pv.PvProvider;

/** Preferred PV provider for user settings. */
public enum UserPvProvider {
    YOUTUBE(PvProvider.YOUTUBE),
    NICONICO(PvProvider.NICONICO),
    BILIBILI(PvProvider.BILIBILI),
    PIAPRO(PvProvider.PIAPRO),
    SOUNDCLOUD(PvProvider.SOUNDCLOUD),
    VIMEO(PvProvider.VIMEO),
    BANDCAMP(PvProvider.BANDCAMP),
    UNSET(null);

    private final PvProvider common;

    UserPvProvider(PvProvider common) {
        this.common = common;
    }

    /** Returns the common provider when this value is shared across domains. */
    public PvProvider toCommon() {
        if (common == null) {
            throw new IllegalStateException("UNSET has no common PvProvider mapping");
        }
        return common;
    }

    /** Maps a shared provider to the user-domain enum. */
    public static UserPvProvider fromCommon(PvProvider provider) {
        return UserPvProvider.valueOf(provider.name());
    }
}
