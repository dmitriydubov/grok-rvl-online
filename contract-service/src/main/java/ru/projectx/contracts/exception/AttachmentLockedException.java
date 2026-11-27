package ru.projectx.contracts.exception;

public class AttachmentLockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttachmentLockedException() {
        super("вложение нельзя удалить: договор уже передан в учётную систему");
    }
}
