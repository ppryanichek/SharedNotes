package com.eugene.sharednotes.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
            SET t.revoked = true
            WHERE t.user.id = :userId AND t.revoked = false
            """)
    int revokeAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM RefreshToken t
            WHERE t.expiresAt < :now
            """)
    int deleteExpired(@Param("now") Instant now);

}
