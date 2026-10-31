package com.eugene.sharednotes.note;

import com.eugene.sharednotes.note.dto.CreateNoteRequest;
import com.eugene.sharednotes.note.dto.NoteResponse;
import com.eugene.sharednotes.note.dto.UpdateNoteRequest;
import com.eugene.sharednotes.note.exceptions.NoteNotFoundException;
import com.eugene.sharednotes.user.UserEntity;
import com.eugene.sharednotes.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long NOTE_ID = 2L;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NoteService noteService;

    UserEntity user;
    NoteEntity note;


    private static NoteEntity note(String title, String body, UserEntity owner) {
        return NoteEntity.builder()
                .title(title)
                .body(body)
                .owner(owner)
                .build();
    }

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        note = note("Title", "body", user);
    }

    @Nested
    class FindNoteByText {
        @Test
        void shouldStripText_whenTextHasLeadingAndTrailingWhitespaces() {
            String text = "  docker ";
            String strippedText = "docker";
            Page<NoteEntity> page = new PageImpl<>(List.of(note));

            when(noteRepository.findByUserWhereTitleOrBodyLikeText(USER_ID, strippedText, Pageable.unpaged()))
                    .thenReturn(page);

            Page<NoteResponse> result = noteService.findByText(USER_ID, text, Pageable.unpaged());

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().title()).isEqualTo("Title");
            assertThat(result.getContent().getFirst().body()).isEqualTo("body");
        }

        @Test
        void shouldConvertNullTextToEmptyString_whenTextIsNull() {
            NoteEntity note2 = note("Title2", "body2", user);
            Page<NoteEntity> page = new PageImpl<>(List.of(note, note2));

            when(noteRepository.findByUserWhereTitleOrBodyLikeText(USER_ID, "", Pageable.unpaged()))
                    .thenReturn(page);

            Page<NoteResponse> result = noteService.findByText(USER_ID, null, Pageable.unpaged());

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent())
                    .extracting(NoteResponse::title)
                    .containsExactlyInAnyOrder("Title", "Title2");
            assertThat(result.getContent())
                    .extracting(NoteResponse::body)
                    .containsExactlyInAnyOrder("body", "body2");
        }
    }

    @Nested
    class FindNoteById {
        @Test
        void shouldReturnNote_whenNoteExists() {
            when(noteRepository.findByOwnerIdAndId(USER_ID, NOTE_ID))
                    .thenReturn(Optional.of(note));

            NoteResponse response = noteService.findById(USER_ID, NOTE_ID);

            assertThat(response.title()).isEqualTo("Title");
            assertThat(response.body()).isEqualTo("body");
        }

        @Test
        void shouldThrowNoteNotFoundException_whenNoteDoesNotExist() {
            when(noteRepository.findByOwnerIdAndId(USER_ID, NOTE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> noteService.findById(USER_ID, NOTE_ID))
                    .isInstanceOf(NoteNotFoundException.class);
        }
    }

    @Nested
    class CreateNote {
        @Test
        void shouldSaveNote_whenFieldsAreCorrect() {
            when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

            noteService.createNote(USER_ID, new CreateNoteRequest("Title", "body"));
            ArgumentCaptor<NoteEntity> captor = ArgumentCaptor.captor();
            verify(noteRepository).save(captor.capture());

            assertThat(captor.getValue().getOwner()).isEqualTo(user);
            assertThat(captor.getValue().getTitle()).isEqualTo("Title");
            assertThat(captor.getValue().getBody()).isEqualTo("body");
        }
    }

    @Nested
    class UpdateNote {
        private static final UpdateNoteRequest UPDATE_NOTE_REQUEST =
                new UpdateNoteRequest("NewTitle", "NewBody");

        @Test
        void shouldUpdateNote_whenDataIsValid() {
            when(noteRepository.findByOwnerIdAndId(USER_ID, NOTE_ID))
                    .thenReturn(Optional.of(note));

            NoteResponse response = noteService
                    .updateNote(USER_ID, NOTE_ID, UPDATE_NOTE_REQUEST);

            assertThat(response.title()).isEqualTo("NewTitle");
            assertThat(response.body()).isEqualTo("NewBody");
            verify(noteRepository).flush();
        }

        @Test
        void shouldThrowNoteNotFoundException_whenNoteDoesNotExist() {
            when(noteRepository.findByOwnerIdAndId(USER_ID, NOTE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> noteService
                    .updateNote(USER_ID, NOTE_ID, UPDATE_NOTE_REQUEST))
                    .isInstanceOf(NoteNotFoundException.class);
        }
    }

    @Nested
    class DeleteNote {
        @Test
        void shouldDelete_whenNoteExists() {
            when(noteRepository.findByOwnerIdAndId(USER_ID, NOTE_ID))
                    .thenReturn(Optional.of(note));

            noteService.deleteNote(USER_ID, NOTE_ID);

            verify(noteRepository).delete(note);
        }

        @Test
        void shouldThrowNoteNotFoundException_whenNoteDoesNotExist() {
            when(noteRepository.findByOwnerIdAndId(USER_ID, NOTE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> noteService
                    .deleteNote(USER_ID, NOTE_ID))
                    .isInstanceOf(NoteNotFoundException.class);

            verify(noteRepository, never()).delete(any());
        }
    }
}