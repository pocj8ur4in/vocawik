package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.Playlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Search contract for playlist listing. */
public interface PlaylistCriteriaRepository {

    /** Returns a paged playlist search result. */
    Page<Playlist> search(PlaylistCriteria criteria, Pageable pageable);
}
