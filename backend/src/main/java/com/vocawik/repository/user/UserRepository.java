package com.vocawik.repository.user;

import com.vocawik.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for {@link User} persistence access. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCaseAndIsDeletedFalse(String email);

    Optional<User> findByUuidAndIsDeletedFalse(UUID uuid);

    @Query("select u.nickname from User u where u.isDeleted = false")
    List<String> findAllNicknamesByIsDeletedFalse();
}
