package com.vocawik.domain.artist;

import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Mapping entity between group artist and member artist. */
@Getter
@Entity
@Table(
        name = "artist_groups",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_artist_groups_group_member",
                    columnNames = {"group_artist_id", "member_artist_id"}),
            @UniqueConstraint(
                    name = "uk_artist_groups_group_sort_order",
                    columnNames = {"group_artist_id", "sort_order"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_artist_id", nullable = false)
    private Artist groupArtist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_artist_id", nullable = false)
    private Artist memberArtist;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates an artist-group mapping row.
     *
     * @param groupArtist group artist
     * @param memberArtist member artist
     * @param sortOrder order inside group
     * @return created mapping row
     */
    public static ArtistGroup create(Artist groupArtist, Artist memberArtist, int sortOrder) {
        if (groupArtist == null) {
            throw new IllegalArgumentException("groupArtist is required");
        }
        if (memberArtist == null) {
            throw new IllegalArgumentException("memberArtist is required");
        }
        if (groupArtist.equals(memberArtist)) {
            throw new IllegalArgumentException("groupArtist and memberArtist must be different");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        ArtistGroup artistGroup = new ArtistGroup();
        artistGroup.groupArtist = groupArtist;
        artistGroup.memberArtist = memberArtist;
        artistGroup.sortOrder = sortOrder;
        return artistGroup;
    }

    /**
     * Updates display order in the group.
     *
     * @param sortOrder updated sort order
     */
    public void updateSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }
        this.sortOrder = sortOrder;
    }
}
