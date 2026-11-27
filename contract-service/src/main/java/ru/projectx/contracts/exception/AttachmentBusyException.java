package ru.projectx.contracts.exception;

public class AttachmentBusyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttachmentBusyException(Throwable cause) {
        super("строка договора занята другой операцией", cause);
    }
}
