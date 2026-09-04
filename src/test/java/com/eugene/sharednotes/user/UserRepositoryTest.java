package com.eugene.sharednotes.user;

import com.eugene.sharednotes.RepositoryTestBase;
import com.eugene.sharednotes.common.Role;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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
    }

    @Test
    void shouldFindActiveUser() {
        persist(user("Bob", "bob@mail.ru", null));

        assertThat(userRepository.findByEmail("bob@mail.ru")).isPresent();
    }

    @Test
    void shouldRejectDuplicateEmail_whenBothUsersAreActive() {
        persist(user("Bob", "bob@mail.ru", null));

        assertThatThrownBy(() -> persist(user("Eugene", "bob@mail.ru", null)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("email");
    }

    @Test
    void shouldRejectDuplicateUsername_whenBothUsersAreActive() {
        persist(user("Bob", "bob@mail.ru", null));

        assertThatThrownBy(() -> persist(user("Bob", "eugene@mail.ru", null)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("username");
    }

    @Test
    void shouldAllowDuplicateEmail_whenOneUserIsSoftDeleted() {
        persist(user("Bob", "bob@mail.ru", FIXED_NOW));

        assertThatNoException()
                .isThrownBy(() -> persist(user("Eugene", "bob@mail.ru", null)));
    }

    @Test
    void shouldAllowDuplicateUsername_whenOneUserIsSoftDeleted() {
        persist(user("Bob", "bob@mail.ru", FIXED_NOW));

        assertThatNoException()
                .isThrownBy(() -> persist(user("Bob", "eugene@mail.ru", null)));
    }
}