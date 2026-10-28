package com.eugene.sharednotes.exceptions;

public class NoteNotFoundException extends RuntimeException {
    public NoteNotFoundException(Long noteId) {
        super("Note not found with id: " + noteId);
    }
}
