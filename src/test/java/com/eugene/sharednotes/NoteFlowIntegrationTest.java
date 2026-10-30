package com.eugene.sharednotes;

import com.eugene.sharednotes.note.dto.CreateNoteRequest;
import com.eugene.sharednotes.note.dto.NoteResponse;
import com.eugene.sharednotes.note.dto.UpdateNoteRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoteFlowIntegrationTest extends IntegrationTestBase {

    private String accessToken;

    private NoteResponse createNote(String title, String body, String token) throws Exception {
        ResultActions result = mockMvc
                .perform(post("/notes")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateNoteRequest(title, body))))
                .andExpect(status().isCreated());

        return bodyOf(result, NoteResponse.class);
    }

    private ResultActions getNoteById(Long noteId, String token) throws Exception {
        return mockMvc.perform(get("/notes/{id}", noteId)
                .with(bearer(token)));
    }

    private List<Integer> searchNoteIds(String text, String token) throws Exception {
        ResultActions result = mockMvc
                .perform(get("/notes")
                        .with(bearer(token))
                        .param("text", text))
                .andExpect(status().isOk());

        return JsonPath.read(bodyOf(result), "$.content[*].id");
    }

    private NoteResponse updateNote(Long noteId, String title, String body, String token) throws Exception {
        ResultActions result = mockMvc
                .perform(patch("/notes/{id}", noteId)
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UpdateNoteRequest(title, body))))
                .andExpect(status().isOk());

        return bodyOf(result, NoteResponse.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        accessToken = register("Bob", "bob@mail.ru").accessToken();
    }

    @Test
    void shouldRefreshUpdatedAtAndKeepCreatedAt_whenNoteIsUpdated() throws Exception {
        NoteResponse created = createNote("Title", "body", accessToken);
        NoteResponse updated = updateNote(created.id(), "NewTitle", "NewBody", accessToken);

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.updatedAt()).isAfter(created.updatedAt());
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }

    @Test
    void shouldReturnOnlyOwnNotes_whenSearching() throws Exception {
        String eugeneToken = register("Eugene", "eugene@mail.ru").accessToken();
        NoteResponse bobNote1 = createNote("Title", "BobNote1", accessToken);
        NoteResponse bobNote2 = createNote("Title", "BobNote2", accessToken);
        createNote("Title", "EugeneNote", eugeneToken);

        List<Integer> ids = searchNoteIds("Title", accessToken);

        assertThat(ids).containsExactlyInAnyOrder(bobNote1.id().intValue(), bobNote2.id().intValue());
    }

    @Test
    void shouldReturn404_whenAccessingOtherUsersNote() throws Exception {
        String eugeneToken = register("Eugene", "eugene@mail.ru").accessToken();
        NoteResponse eugeneNote = createNote("Title", "EugeneNote", eugeneToken);

        getNoteById(eugeneNote.id(), accessToken)
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/notes/{id}", eugeneNote.id())
                        .with(bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UpdateNoteRequest("NewTitle", "NewBody"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/notes/{id}", eugeneNote.id())
                        .with(bearer(accessToken)))
                .andExpect(status().isNotFound());

        ResultActions result = getNoteById(eugeneNote.id(), eugeneToken)
                .andExpect(status().isOk());

        NoteResponse response = bodyOf(result, NoteResponse.class);

        assertThat(response.id()).isEqualTo(eugeneNote.id());
        assertThat(response.title()).isEqualTo(eugeneNote.title());
        assertThat(response.body()).isEqualTo(eugeneNote.body());
    }

    @Test
    void shouldSortByUpdatedAtDesc_whenSortParamIsMissing() throws Exception {
        NoteResponse note1 = createNote("Title", "body1", accessToken);
        NoteResponse note2 = createNote("Title", "body2", accessToken);
        NoteResponse note3 = createNote("Title", "body3", accessToken);

        updateNote(note1.id(), null, "NewBody", accessToken);

        List<Integer> ids = searchNoteIds("Title", accessToken);

        assertThat(ids).containsExactly(
                note1.id().intValue(),
                note3.id().intValue(),
                note2.id().intValue());
    }

    @Test
    void shouldCreateFindUpdateAndDeleteNote() throws Exception {
        NoteResponse note = createNote("Title", "body", accessToken);

        // Get by id
        ResultActions getByIdResult = getNoteById(note.id(), accessToken)
                .andExpect(status().isOk());

        assertThat(bodyOf(getByIdResult, NoteResponse.class).id()).isEqualTo(note.id());

        // Search
        List<Integer> ids = searchNoteIds("Title", accessToken);

        assertThat(ids).containsExactlyInAnyOrder(note.id().intValue());

        // Update
        updateNote(note.id(), "NewTitle", "NewBody", accessToken);
        ResultActions afterUpdateResult = getNoteById(note.id(), accessToken)
                .andExpect(status().isOk());
        NoteResponse response = bodyOf(afterUpdateResult, NoteResponse.class);

        assertThat(response.title()).isEqualTo("NewTitle");
        assertThat(response.body()).isEqualTo("NewBody");

        // Delete
        mockMvc.perform(delete("/notes/{id}", note.id())
                        .with(bearer(accessToken)))
                .andExpect(status().isNoContent());

        getNoteById(note.id(), accessToken)
                .andExpect(status().isNotFound());
    }
}
