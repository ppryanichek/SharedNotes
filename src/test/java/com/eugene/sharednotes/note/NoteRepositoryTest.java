package com.eugene.sharednotes.note;

import com.eugene.sharednotes.RepositoryTestBase;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.user.UserEntity;
import com.eugene.sharednotes.user.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NoteRepositoryTest extends RepositoryTestBase {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity user(String username, String email) {
        return UserEntity.builder()
                .username(username)
                .email(email)
                .password("hashed")
                .role(Role.USER)
                .build();
    }

    private NoteEntity note(String title, String body, UserEntity user) {
        return NoteEntity.builder()
                .title(title)
                .body(body)
                .owner(user)
                .build();
    }

    private UserEntity persistUser(UserEntity user) {
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();
        return user;
    }

    private NoteEntity persistNote(NoteEntity note) {
        entityManager.persist(note);
        entityManager.flush();
        entityManager.clear();
        return note;
    }

    @Nested
    class FindByUserAndText {

        @Test
        void shouldReturnNotes_whenTextMatchesPartOfBody() {
            UserEntity existingUser = persistUser(user("Bob", "bob@mail.ru"));
            NoteEntity note = persistNote(note("First note", "hello txt", existingUser));

            Page<NoteEntity> result = noteRepository
                    .findByUserWhereTitleOrBodyLikeText(existingUser.getId(), "txt", Pageable.unpaged());

            assertThat(result)
                    .extracting(NoteEntity::getId)
                    .containsExactly(note.getId());
        }

        @Test
        void shouldReturnNotes_whenTextMatchesPartOfTitle() {
            UserEntity existingUser = persistUser(user("Bob", "bob@mail.ru"));
            NoteEntity note = persistNote(note("First note", "hello txt", existingUser));

            Page<NoteEntity> result = noteRepository
                    .findByUserWhereTitleOrBodyLikeText(existingUser.getId(), "note", Pageable.unpaged());

            assertThat(result)
                    .extracting(NoteEntity::getId)
                    .containsExactly(note.getId());
        }

        @Test
        void shouldReturnNotes_whenTextMatchesPartOfBodyIgnoringCase() {
            UserEntity existingUser = persistUser(user("Bob", "bob@mail.ru"));
            NoteEntity note = persistNote(note("First note", "hello txt", existingUser));

            Page<NoteEntity> result = noteRepository
                    .findByUserWhereTitleOrBodyLikeText(existingUser.getId(), "TXT", Pageable.unpaged());

            assertThat(result)
                    .extracting(NoteEntity::getId)
                    .containsExactly(note.getId());
        }

        @Test
        void shouldReturnAllNotes_whenSeveralNotesMatch() {
            UserEntity existingUser = persistUser(user("Bob", "bob@mail.ru"));
            NoteEntity note1 = persistNote(note("First note", "txt1", existingUser));
            NoteEntity note2 = persistNote(note("Second note", "txt2", existingUser));

            Page<NoteEntity> result = noteRepository
                    .findByUserWhereTitleOrBodyLikeText(existingUser.getId(), "txt", Pageable.unpaged());

            assertThat(result)
                    .extracting(NoteEntity::getId)
                    .containsExactlyInAnyOrder(note1.getId(), note2.getId());
        }

        @Test
        void shouldNotReturnOtherUsersNotes_whenTextMatchesTheirNotes() {
            UserEntity bob = persistUser(user("Bob", "bob@mail.ru"));
            UserEntity eugene = persistUser(user("Eugene", "eugene@mail.ru"));
            NoteEntity bobNote = persistNote(note("Bob note", "txt", bob));
            persistNote(note("Eugene note", "txt", eugene));

            Page<NoteEntity> result = noteRepository
                    .findByUserWhereTitleOrBodyLikeText(bob.getId(), "txt", Pageable.unpaged());

            assertThat(result)
                    .extracting(NoteEntity::getId)
                    .containsExactly(bobNote.getId());
        }

        @Test
        void shouldReturnAllUserNotes_whenTextIsEmpty() {
            UserEntity existingUser = persistUser(user("Bob", "bob@mail.ru"));
            NoteEntity note1 = persistNote(note("Title", "body", existingUser));
            NoteEntity note2 = persistNote(note(null, "body", existingUser));

            Page<NoteEntity> result = noteRepository
                    .findByUserWhereTitleOrBodyLikeText(existingUser.getId(), "", Pageable.unpaged());

            assertThat(result)
                    .extracting(NoteEntity::getId)
                    .containsExactlyInAnyOrder(note1.getId(), note2.getId());
        }
    }

    @Test
    void shouldNotReturnNotes_whenUserHasBeenHardDeleted() {
        UserEntity existingUser = user("Bob", "bob@mail.ru");
        existingUser.setDeletedAt(FIXED_NOW.minus(Duration.ofDays(1)));
        persistUser(existingUser);
        persistNote(note("Title", "txt", existingUser));
        persistNote(note("Title", "txt", existingUser));

        userRepository.deleteSoftDeletedBefore(FIXED_NOW);
        long notesCount = noteRepository.count();

        assertThat(notesCount).isZero();
    }
}