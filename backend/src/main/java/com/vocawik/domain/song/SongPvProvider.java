package com.vocawik.domain.song;

import com.vocawik.common.pv.PvProvider;

/** Song PV provider. */
public enum SongPvProvider {
    YOUTUBE(PvProvider.YOUTUBE),
    NICONICO(PvProvider.NICONICO),
    BILIBILI(PvProvider.BILIBILI),
    PIAPRO(PvProvider.PIAPRO),
    SOUNDCLOUD(PvProvider.SOUNDCLOUD),
    VIMEO(PvProvider.VIMEO),
    BANDCAMP(PvProvider.BANDCAMP),
    OTHER(null);

    private final PvProvider common;

    SongPvProvider(PvProvider common) {
        this.common = common;
    }

    /** Returns the common provider when this value is shared across domains. */
    public PvProvider toCommon() {
        if (common == null) {
            throw new IllegalStateException("OTHER has no common PvProvider mapping");
        }
        return common;
    }

    /** Maps a shared provider to the song-domain enum. */
    public static SongPvProvider fromCommon(PvProvider provider) {
        return SongPvProvider.valueOf(provider.name());
    }
}
