package com.eugene.sharednotes.note;

import com.eugene.sharednotes.note.dto.CreateNoteRequest;
import com.eugene.sharednotes.note.dto.NoteResponse;
import com.eugene.sharednotes.note.dto.UpdateNoteRequest;
import com.eugene.sharednotes.security.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notes")
public class NoteController {
    private final NoteService noteService;

    @GetMapping("/{noteId}")
    public NoteResponse getNoteById(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable Long noteId) {
        return noteService.findById(principal.id(), noteId);
    }

    @GetMapping
    public Page<NoteResponse> getNotesByText(@AuthenticationPrincipal JwtPrincipal principal,
                                             @RequestParam(required = false) String text,
                                             @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return noteService.findByText(principal.id(), text, pageable);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public NoteResponse createNote(@AuthenticationPrincipal JwtPrincipal principal,
                                   @Valid @RequestBody CreateNoteRequest request) {
        return noteService.createNote(principal.id(), request);
    }

    @PatchMapping("/{noteId}")
    public NoteResponse updateNote(@AuthenticationPrincipal JwtPrincipal principal,
                                   @PathVariable Long noteId,
                                   @Valid @RequestBody UpdateNoteRequest request) {
        return noteService.updateNote(principal.id(), noteId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{noteId}")
    public void deleteNote(@AuthenticationPrincipal JwtPrincipal principal,
                           @PathVariable Long noteId) {
        noteService.deleteNote(principal.id(), noteId);
    }
}
