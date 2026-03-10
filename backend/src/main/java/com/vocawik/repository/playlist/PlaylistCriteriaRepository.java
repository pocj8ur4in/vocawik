package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.Playlist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** Search contract for playlist listing. */
public interface PlaylistCriteriaRepository {

    /** Returns a sliced playlist search result. */
    Slice<Playlist> search(PlaylistCriteria criteria, Pageable pageable);
}
