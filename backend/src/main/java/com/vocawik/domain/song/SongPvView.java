package com.vocawik.domain.song;

import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Aggregate view counter row per song PV. */
@Getter
@Entity
@Table(name = "song_pv_views")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongPvView extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_pv_id", nullable = false, unique = true)
    private SongPv songPv;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    /**
     * Creates a view row for a song PV.
     *
     * @param songPv target song pv
     * @return created row
     */
    public static SongPvView create(SongPv songPv) {
        if (songPv == null) {
            throw new IllegalArgumentException("songPv is required");
        }

        SongPvView songPvView = new SongPvView();
        songPvView.songPv = songPv;
        return songPvView;
    }
}
