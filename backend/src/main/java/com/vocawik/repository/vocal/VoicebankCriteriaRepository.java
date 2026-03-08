package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.VocalVoicebank;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** Custom search repository for {@link VocalVoicebank}. */
public interface VoicebankCriteriaRepository {

    /**
     * Searches voicebanks with optional filters.
     *
     * @param criteria search criteria
     * @param pageable page/sort options
     * @return sliced voicebanks
     */
    Slice<VocalVoicebank> search(VoicebankCriteria criteria, Pageable pageable);
}
