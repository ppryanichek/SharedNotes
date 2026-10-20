package com.eugene.sharednotes.exceptions;

public class EmptyNoteException extends RuntimeException {
    public EmptyNoteException() {
        super("Note must have a title or a body");
    }
}
