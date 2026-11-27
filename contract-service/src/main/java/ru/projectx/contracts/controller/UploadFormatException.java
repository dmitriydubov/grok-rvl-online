package ru.projectx.contracts.controller;

final class UploadFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final String reason;

    UploadFormatException(String field, String reason) {
        super(field);
        this.field = field;
        this.reason = reason;
    }

    UploadFormatException(String field, String reason, Throwable cause) {
        super(field, cause);
        this.field = field;
        this.reason = reason;
    }

    String field() {
        return field;
    }

    String reason() {
        return reason;
    }
}
