package com.eugene.sharednotes.note.dto;

import com.eugene.sharednotes.note.NoteLimits;
import jakarta.validation.constraints.Size;

public record UpdateNoteRequest(
        @Size(max = NoteLimits.TITLE_MAX_LENGTH)
        String title,

        @Size(max = NoteLimits.BODY_MAX_LENGTH)
        String body
) {}
