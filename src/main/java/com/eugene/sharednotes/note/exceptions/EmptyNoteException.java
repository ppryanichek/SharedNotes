package com.eugene.sharednotes.note.exceptions;

public class EmptyNoteException extends RuntimeException {
    public EmptyNoteException() {
        super("Note must have a title or a body");
    }
}
