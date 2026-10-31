package com.eugene.sharednotes.note;

import com.eugene.sharednotes.ControllerTestBase;
import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.error.dto.ErrorCode;
import com.eugene.sharednotes.note.dto.CreateNoteRequest;
import com.eugene.sharednotes.note.dto.NoteResponse;
import com.eugene.sharednotes.note.dto.UpdateNoteRequest;
import com.eugene.sharednotes.note.exceptions.EmptyNoteException;
import com.eugene.sharednotes.note.exceptions.NoteNotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoteController.class)
class NoteControllerTest extends ControllerTestBase {

    private static final Long USER_ID = 1L;
    private static final Long NOTE_ID = 2L;
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final String TOO_LONG_BODY = "a".repeat(NoteLimits.BODY_MAX_LENGTH + 1);
    private static final String TOO_LONG_TITLE = "a".repeat(NoteLimits.TITLE_MAX_LENGTH + 1);
    private static final NoteResponse RESPONSE = new NoteResponse(NOTE_ID, "Title", "body", FIXED_NOW, FIXED_NOW);

    private static final RequestPostProcessor AUTH_USER = authAs(USER_ID, Role.USER);

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private NoteService noteService;

    @Nested
    class SearchNotes {
        private MockHttpServletRequestBuilder findByTextRequest(String text) {
            return get("/notes").with(AUTH_USER)
                    .param("text", text);
        }

        @Test
        void shouldApplyDefaultPagination_whenPageParamsAreMissing() throws Exception {
            when(noteService
                    .findByText(eq(USER_ID), eq("Title"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(new NoteResponse(
                            NOTE_ID, "Title", "body", FIXED_NOW, FIXED_NOW))));

            mockMvc.perform(findByTextRequest("Title"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.content[0].title").value("Title"))
                    .andExpect(jsonPath("$.content[0].body").value("body"));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(noteService).findByText(eq(USER_ID), eq("Title"), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(20);
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "updatedAt"));
        }

        @Test
        void shouldReturnNotes_whenTextParamIsMissing() throws Exception {
            when(noteService
                    .findByText(eq(USER_ID), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(new NoteResponse(
                            NOTE_ID, "Title", "body", FIXED_NOW, FIXED_NOW))));

            mockMvc.perform(get("/notes").with(AUTH_USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.content[0].title").value("Title"))
                    .andExpect(jsonPath("$.content[0].body").value("body"));
        }

        @Test
        void shouldReturnNotesWithPagination_whenPageParamsSetByClient() throws Exception {
            when(noteService
                    .findByText(eq(USER_ID), eq("Title"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(
                            List.of(new NoteResponse(NOTE_ID, "Title", "body", FIXED_NOW, FIXED_NOW))));

            mockMvc.perform(findByTextRequest("Title")
                            .param("page", "1")
                            .param("size", "10")
                            .param("sort", "createdAt,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.content[0].title").value("Title"))
                    .andExpect(jsonPath("$.content[0].body").value("body"));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(noteService).findByText(eq(USER_ID), eq("Title"), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(10);
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
    }

    @Nested
    class GetNoteById {
        private static MockHttpServletRequestBuilder findByIdRequest() {
            return get("/notes/{id}", NOTE_ID).with(AUTH_USER);
        }

        @Test
        void shouldReturn200_whenNoteExists() throws Exception {
            when(noteService.findById(USER_ID, NOTE_ID))
                    .thenReturn(RESPONSE);

            mockMvc.perform(findByIdRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.title").value("Title"))
                    .andExpect(jsonPath("$.body").value("body"))
                    .andExpect(jsonPath("$.createdAt").value(FIXED_NOW.toString()))
                    .andExpect(jsonPath("$.updatedAt").value(FIXED_NOW.toString()));
        }

        @Test
        void shouldReturn400_whenIdIsNotValid() throws Exception {
            mockMvc.perform(get("/notes/abc").with(AUTH_USER))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(noteService);
        }

        @Test
        void shouldReturn401_whenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/notes/{id}", NOTE_ID))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(noteService);
        }

        @Test
        void shouldReturn404_whenNoteDoesNotExist() throws Exception {
            when(noteService.findById(USER_ID, NOTE_ID))
                    .thenThrow(new NoteNotFoundException(NOTE_ID));

            mockMvc.perform(findByIdRequest())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.NOTE_NOT_FOUND.name()))
                    .andExpect(jsonPath("$.message").value("Note not found with id: " + NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    @Nested
    class CreateNote {
        private MockHttpServletRequestBuilder createRequest(String title, String body) {
            return post("/notes").with(AUTH_USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new CreateNoteRequest(title, body)));
        }

        @Test
        void shouldReturn201_whenRequestIsValid() throws Exception {
            when(noteService.createNote(USER_ID, new CreateNoteRequest("Title", "body")))
                    .thenReturn(RESPONSE);

            mockMvc.perform(createRequest("Title", "body"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.title").value("Title"))
                    .andExpect(jsonPath("$.body").value("body"))
                    .andExpect(jsonPath("$.createdAt").value(FIXED_NOW.toString()))
                    .andExpect(jsonPath("$.updatedAt").value(FIXED_NOW.toString()));
        }

        @Test
        void shouldReturn400_whenAllFieldsAreBlank() throws Exception {
            when(noteService.createNote(USER_ID, new CreateNoteRequest("", "")))
                    .thenThrow(new EmptyNoteException());

            mockMvc.perform(createRequest("", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.EMPTY_NOTE.name()))
                    .andExpect(jsonPath("$.message").value("Note must have a title or a body"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldReturn400_whenTitleIsInvalid() throws Exception {
            mockMvc.perform(createRequest(TOO_LONG_TITLE, "body"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].message").exists());

            verifyNoInteractions(noteService);
        }

        @Test
        void shouldReturn400_whenBodyIsInvalid() throws Exception {
            mockMvc.perform(createRequest("Title", TOO_LONG_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("body"))
                    .andExpect(jsonPath("$.errors[0].message").exists());

            verifyNoInteractions(noteService);
        }
    }

    @Nested
    class UpdateNote {
        private static final NoteResponse UPDATE_RESPONSE = new NoteResponse(NOTE_ID, "NewTitle", "NewBody", FIXED_NOW, FIXED_NOW);

        private MockHttpServletRequestBuilder updateRequest(String title, String body) {
            return patch("/notes/{id}", NOTE_ID).with(AUTH_USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new UpdateNoteRequest(title, body)));
        }

        @Test
        void shouldReturn200_whenRequestIsValid() throws Exception {
            when(noteService.updateNote(USER_ID, NOTE_ID, new UpdateNoteRequest("NewTitle", "NewBody")))
                    .thenReturn(UPDATE_RESPONSE);

            mockMvc.perform(updateRequest("NewTitle", "NewBody"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.title").value("NewTitle"))
                    .andExpect(jsonPath("$.body").value("NewBody"));
        }

        @Test
        void shouldReturn200_whenBodyIsNull() throws Exception {
            when(noteService.updateNote(USER_ID, NOTE_ID, new UpdateNoteRequest("NewTitle", null)))
                    .thenReturn(new NoteResponse(NOTE_ID, "NewTitle", "body", FIXED_NOW, FIXED_NOW));

            mockMvc.perform(updateRequest("NewTitle", null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.title").value("NewTitle"))
                    .andExpect(jsonPath("$.body").value("body"));
        }

        @Test
        void shouldReturn400_whenNoteBecomesEmpty() throws Exception {
            when(noteService.updateNote(USER_ID, NOTE_ID, new UpdateNoteRequest("", "")))
                    .thenThrow(new EmptyNoteException());

            mockMvc.perform(updateRequest("", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.EMPTY_NOTE.name()))
                    .andExpect(jsonPath("$.message").value("Note must have a title or a body"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldReturn404_whenNoteDoesNotExist() throws Exception {
            when(noteService.updateNote(USER_ID, NOTE_ID, new UpdateNoteRequest("NewTitle", "NewBody")))
                    .thenThrow(new NoteNotFoundException(NOTE_ID));

            mockMvc.perform(updateRequest("NewTitle", "NewBody"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.NOTE_NOT_FOUND.name()))
                    .andExpect(jsonPath("$.message").value("Note not found with id: " + NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void shouldReturn400_whenTitleIsInvalid() throws Exception {
            mockMvc.perform(updateRequest(TOO_LONG_TITLE, "body"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("title"))
                    .andExpect(jsonPath("$.errors[0].message").exists());

            verifyNoInteractions(noteService);
        }

        @Test
        void shouldReturn400_whenBodyIsInvalid() throws Exception {
            mockMvc.perform(updateRequest("Title", TOO_LONG_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("body"))
                    .andExpect(jsonPath("$.errors[0].message").exists());

            verifyNoInteractions(noteService);
        }
    }

    @Nested
    class DeleteNote {

        private static MockHttpServletRequestBuilder deleteRequest() {
            return delete("/notes/{id}", NOTE_ID).with(AUTH_USER);
        }

        @Test
        void shouldReturn204_whenNoteExists() throws Exception {
            mockMvc.perform(deleteRequest())
                    .andExpect(status().isNoContent());

            verify(noteService).deleteNote(USER_ID, NOTE_ID);
        }

        @Test
        void shouldReturn404_whenNoteDoesNotExist() throws Exception {
            doThrow(new NoteNotFoundException(NOTE_ID))
                    .when(noteService).deleteNote(USER_ID, NOTE_ID);

            mockMvc.perform(deleteRequest())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.NOTE_NOT_FOUND.name()))
                    .andExpect(jsonPath("$.message").value("Note not found with id: " + NOTE_ID.intValue()))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }
}