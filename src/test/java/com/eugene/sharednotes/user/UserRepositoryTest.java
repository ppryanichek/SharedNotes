package com.eugene.sharednotes.user;

import com.eugene.sharednotes.RepositoryTestBase;
import com.eugene.sharednotes.common.Role;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends RepositoryTestBase {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity user(String username, String email, Instant deletedAt) {
        return UserEntity.builder()
                .username(username)
                .email(email)
                .password("hashed")
                .role(Role.USER)
                .deletedAt(deletedAt)
                .build();
    }

    private UserEntity persist(UserEntity user) {
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();
        return user;
    }

    @Nested
    class SoftDeletedUser {

        private UserEntity deletedUser;

        @BeforeEach
        void setUp() {
            deletedUser = persist(user("Bob", "bob@mail.ru", FIXED_NOW));
        }

        @Test
        void shouldNotFind_byId() {
            assertThat(userRepository.findById(deletedUser.getId())).isEmpty();
        }

        @Test
        void shouldNotFind_byEmail() {
            assertThat(userRepository.findByEmail("bob@mail.ru")).isEmpty();
        }

        @Test
        void shouldNotFind_byUsername() {
            assertThat(userRepository.findByUsername("Bob")).isEmpty();
        }

        @Test
        void shouldReturnFalse_whenCheckedByEmail() {
            assertThat(userRepository.existsByEmail("bob@mail.ru")).isFalse();
        }

        @Test
        void shouldReturnFalse_whenCheckedByUsername() {
            assertThat(userRepository.existsByUsername("Bob")).isFalse();
        }

        @Test
        void shouldNotInclude_inFindAll() {
            assertThat(userRepository.findAll()).isEmpty();
        }

        @Test
        void shouldNotAllowDuplicateEmail() {
            assertThatThrownBy(() -> persist(user("Eugene", "bob@mail.ru", null)))
                    .isInstanceOf(ConstraintViolationException.class);
        }

        @Test
        void shouldNotAllowDuplicateUsername() {
            assertThatThrownBy(() -> persist(user("Bob", "eugene@mail.ru", null)))
                    .isInstanceOf(ConstraintViolationException.class);
        }
    }


    @Nested
    class ActiveUser {

        @BeforeEach
        void setUp() {
            persist(user("Bob", "bob@mail.ru", null));
        }

        @Test
        void shouldFindActiveUser() {
            assertThat(userRepository.findByEmail("bob@mail.ru")).isPresent();
        }

        @Test
        void shouldRejectDuplicateEmail_whenBothUsersAreActive() {
            assertThatThrownBy(() -> persist(user("Eugene", "bob@mail.ru", null)))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("email");
        }

        @Test
        void shouldRejectDuplicateUsername_whenBothUsersAreActive() {
            assertThatThrownBy(() -> persist(user("Bob", "eugene@mail.ru", null)))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("username");
        }
    }

    @Nested
    class FindSoftDeletedByEmail {

        @Test
        void shouldReturnNothing_whenDBIsEmpty() {
            assertThat(userRepository.findSoftDeletedByEmail("bob@mail.ru")).isEmpty();
        }

        @Test
        void shouldReturnNothing_whenUserIsActive() {
            persist(user("Bob", "bob@mail.ru", null));

            assertThat(userRepository.findSoftDeletedByEmail("bob@mail.ru")).isEmpty();
        }

        @Test
        void shouldReturnUser_whenUserIsSoftDeleted() {
            UserEntity user = persist(user("Bob", "bob@mail.ru", FIXED_NOW));

            Long userId = user.getId();

            assertThat(userRepository.findSoftDeletedByEmail("bob@mail.ru"))
                    .get()
                    .extracting(UserEntity::getId)
                    .isEqualTo(userId);
        }
    }

    @Nested
    class DeleteSoftDeletedBefore {

        Instant cutoff = FIXED_NOW.minus(Duration.ofDays(30));

        public Long countByEmail(String email) {
            return (Long) entityManager.getEntityManager()
                    .createNativeQuery("SELECT COUNT(*) FROM users WHERE email = :email")
                    .setParameter("email", email)
                    .getSingleResult();
        }

        @Test
        void shouldCleanup_whenRetentionPeriodExpired() {
            persist(user("Bob", "bob@mail.ru", FIXED_NOW.minus(Duration.ofDays(31))));

            userRepository.deleteSoftDeletedBefore(cutoff);

            assertThat(countByEmail("bob@mail.ru")).isEqualTo(0L);
        }

        @Test
        void shouldNotCleanup_whenDeletedAtExactlyAtCutoff() {
            persist(user("Bob", "bob@mail.ru", FIXED_NOW.minus(Duration.ofDays(30))));

            userRepository.deleteSoftDeletedBefore(cutoff);

            assertThat(countByEmail("bob@mail.ru")).isEqualTo(1L);
        }

        @Test
        void shouldNotCleanup_whenRetentionPeriodActive() {
            persist(user("Bob", "bob@mail.ru", FIXED_NOW.minus(Duration.ofDays(29))));

            userRepository.deleteSoftDeletedBefore(cutoff);

            assertThat(countByEmail("bob@mail.ru")).isEqualTo(1L);
        }
    }
}