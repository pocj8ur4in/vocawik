package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.VocalCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link VocalCharacter} persistence access. */
public interface VocalCharacterRepository extends JpaRepository<VocalCharacter, Long> {}
