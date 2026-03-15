package com.vocawik.domain.song;

import com.vocawik.domain.BaseEntity;
import com.vocawik.domain.artist.Artist;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mapping entity between songs and artists. */
@Getter
@Entity
@Table(
        name = "song_artists",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_song_artists_song_artist_role",
                    columnNames = {"song_id", "artist_id", "role"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongArtist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "role", nullable = false, columnDefinition = "text[]")
    private String[] roleValues;

    @Column(name = "is_main", nullable = false)
    private boolean isMain;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a song-artist mapping row.
     *
     * @param song target song
     * @param artist target artist
     * @param roles participation roles
     * @param isMain whether artist is main participant
     * @param sortOrder display order
     * @return created mapping row
     */
    public static SongArtist create(
            Song song, Artist artist, Set<SongArtistRole> roles, boolean isMain, int sortOrder) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (artist == null) {
            throw new IllegalArgumentException("artist is required");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        SongArtist songArtist = new SongArtist();
        songArtist.song = song;
        songArtist.artist = artist;
        songArtist.roleValues = normalizeRoles(roles);
        songArtist.isMain = isMain;
        songArtist.sortOrder = sortOrder;
        return songArtist;
    }

    /**
     * Returns participation roles as set.
     *
     * @return normalized role set
     */
    public Set<SongArtistRole> getRoles() {
        if (roleValues == null || roleValues.length == 0) {
            return Set.of();
        }
        Set<SongArtistRole> normalized =
                java.util.Arrays.stream(roleValues)
                        .map(SongArtistRole::valueOf)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return java.util.Collections.unmodifiableSet(normalized);
    }

    /**
     * Updates participation roles.
     *
     * @param roles updated roles
     */
    public void updateRoles(Set<SongArtistRole> roles) {
        this.roleValues = normalizeRoles(roles);
    }

    /**
     * Updates participant flags and ordering.
     *
     * @param isMain updated main flag
     * @param sortOrder updated sort order
     */
    public void updateParticipation(boolean isMain, int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }
        this.isMain = isMain;
        this.sortOrder = sortOrder;
    }

    private static String[] normalizeRoles(Set<SongArtistRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles is required");
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (SongArtistRole role : roles) {
            if (role == null) {
                throw new IllegalArgumentException("roles contains null");
            }
            normalized.add(role.name());
        }
        return normalized.toArray(new String[0]);
    }
}
