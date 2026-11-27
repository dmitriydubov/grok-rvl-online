package ru.projectx.contracts.controller;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.contracts.dto.ErrorResponse;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.AttachmentDefect;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.AttachmentRejectedException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.exception.RequestNotFoundException;
import ru.projectx.contracts.exception.StorageUnavailableException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DomainErrorAdvice {

    private static final Map<AttachmentDefect, String> DEFECT_REASONS = new EnumMap<>(Map.of(
            AttachmentDefect.CONTENT_TYPE, "тип файла не допускается",
            AttachmentDefect.SIZE, "размер файла вне допустимого"));

    private static final Map<Class<? extends RuntimeException>, Outcome> OUTCOMES = outcomes();

    @ExceptionHandler({
            RequestNotFoundException.class,
            RequestCancelledException.class,
            AttachmentConflictException.class,
            StorageUnavailableException.class,
            AttachmentNotFoundException.class,
            AttachmentLockedException.class,
            AttachmentBusyException.class
    })
    public ResponseEntity<ErrorResponse> failed(RuntimeException failure) {
        return rendered(OUTCOMES.get(failure.getClass()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> attachmentRejected(AttachmentRejectedException rejected) {
        return ApiErrors.invalidField(ApiErrors.FILE_FIELD, DEFECT_REASONS.get(rejected.defect()));
    }

    private static Map<Class<? extends RuntimeException>, Outcome> outcomes() {
        return Map.of(
                RequestNotFoundException.class, new Outcome(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND",
                        "задачи на выпуск по заявке нет"),
                RequestCancelledException.class, new Outcome(HttpStatus.CONFLICT, "INVALID_STATE",
                        "работа по заявке прекращена"),
                AttachmentConflictException.class, new Outcome(HttpStatus.CONFLICT, "ATTACHMENT_CONFLICT",
                        "вложение с таким идентификатором уже зарегистрировано с другими реквизитами или удалено"),
                StorageUnavailableException.class, new Outcome(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                        "хранилище файлов недоступно"),
                AttachmentNotFoundException.class, new Outcome(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND",
                        "вложение не найдено"),
                AttachmentLockedException.class, new Outcome(HttpStatus.CONFLICT, "ATTACHMENT_LOCKED",
                        "вложение нельзя удалить: договор уже передан в учётную систему"),
                AttachmentBusyException.class, new Outcome(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                        "договор КМ занят другой операцией, повторите запрос позже"));
    }

    private static ResponseEntity<ErrorResponse> rendered(Outcome outcome) {
        return ApiErrors.of(outcome.status(), outcome.code(), outcome.message());
    }

    private record Outcome(HttpStatus status, String code, String message) {
    }
}
