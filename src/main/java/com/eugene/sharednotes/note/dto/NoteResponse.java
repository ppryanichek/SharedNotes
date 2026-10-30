package com.eugene.sharednotes.note.dto;

import com.eugene.sharednotes.note.NoteEntity;

import java.time.Instant;

public record NoteResponse(
        Long id,
        String title,
        String body,
        Instant updatedAt,
        Instant createdAt
) {
    public static NoteResponse from(NoteEntity note) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getBody(),
                note.getUpdatedAt(),
                note.getCreatedAt()
        );
    }
}
