package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.VocalVoicebank;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link VocalVoicebank} persistence access. */
public interface VocalVoicebankRepository extends JpaRepository<VocalVoicebank, Long> {}
