package com.eugene.sharednotes.note;

import com.eugene.sharednotes.exceptions.EmptyNoteException;
import com.eugene.sharednotes.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteEntityTest {

    UserEntity user = new UserEntity();

    private static NoteEntity note(String title, String body, UserEntity owner) {
        return NoteEntity.builder()
                .title(title)
                .body(body)
                .owner(owner)
                .build();
    }

    @Nested
    class CreateNote {

        @Test
        void shouldCreateNote_whenFieldsAreCorrect() {
            NoteEntity note = note("Title", "body", user);

            assertThat(note.getTitle()).isEqualTo("Title");
            assertThat(note.getBody()).isEqualTo("body");
        }

        @Test
        void shouldStripString_whenStringHasLeadingAndTrailingWhitespaces() {
            NoteEntity note = note("  Title  ", null, user);

            assertThat(note.getTitle()).isEqualTo("Title");
            assertThat(note.getBody()).isNull();
        }

        @Test
        void shouldSetTitleToNull_whenTitleIsEmpty() {
            NoteEntity note = note("", "body", user);

            assertThat(note.getTitle()).isNull();
            assertThat(note.getBody()).isEqualTo("body");
        }

        @Test
        void shouldThrowEmptyNoteException_whenFieldsAreNullOrBlank() {
            assertThatThrownBy(() -> note("  ", null, user))
                    .isInstanceOf(EmptyNoteException.class);
        }

        @Test
        void shouldThrowNullPointerException_whenOwnerIsNull() {
            assertThatThrownBy(() -> note("Title", "body", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class UpdateNote {

        NoteEntity note;

        @BeforeEach
        void setUp() {
            note = note("Title", "body", user);
        }

        @Test
        void shouldUpdate_whenFieldsAreCorrect() {
            note.update("NewTitle", "NewBody");

            assertThat(note.getTitle()).isEqualTo("NewTitle");
            assertThat(note.getBody()).isEqualTo("NewBody");
        }

        @Test
        void shouldNotUpdateField_whenFieldIsNull() {
            note.update("NewTitle", null);

            assertThat(note.getTitle()).isEqualTo("NewTitle");
            assertThat(note.getBody()).isEqualTo("body");
        }

        @Test
        void shouldStripString_whenStringHasLeadingAndTrailingWhitespaces() {
            note.update("  NewTitle  ", null);

            assertThat(note.getTitle()).isEqualTo("NewTitle");
        }

        @Test
        void shouldSetTitleToNull_whenTitleIsEmpty() {
            note.update("", null);

            assertThat(note.getTitle()).isNull();
            assertThat(note.getBody()).isEqualTo("body");
        }

        @Test
        void shouldThrowEmptyNoteException_whenAllFieldsAreEmpty() {
            assertThatThrownBy(() -> note.update("", ""))
                    .isInstanceOf(EmptyNoteException.class);

            assertThat(note.getTitle()).isEqualTo("Title");
            assertThat(note.getBody()).isEqualTo("body");
        }
    }
}