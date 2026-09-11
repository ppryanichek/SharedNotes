package com.eugene.sharednotes.user;

import com.eugene.sharednotes.common.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    /** Нативный запрос, чтобы обойти {@code @SQLRestriction} на UserEntity. */
    @Modifying
    @Query(value = "DELETE FROM users WHERE deleted_at < :cutoff", nativeQuery = true)
    int deleteSoftDeletedBefore(@Param("cutoff") Instant cutoff);

    @Query(value = "SELECT * FROM users WHERE  email = :email AND deleted_at IS NOT NULL", nativeQuery = true)
    Optional<UserEntity> findSoftDeletedByEmail(@Param("email") String email);
}
