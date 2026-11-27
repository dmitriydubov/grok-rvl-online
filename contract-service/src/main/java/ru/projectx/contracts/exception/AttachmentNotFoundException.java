package ru.projectx.contracts.exception;

public class AttachmentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttachmentNotFoundException() {
        super("вложение не найдено");
    }

    public AttachmentNotFoundException(Throwable cause) {
        super("вложение не найдено", cause);
    }
}
