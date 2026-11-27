package ru.projectx.contracts.service.storage;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CertificateUpload(
        UUID individualRequestId,
        UUID participantId,
        UUID attachmentId,
        @Nullable String contentType,
        long size,
        ContentSource content) implements IncomingFile {
}
