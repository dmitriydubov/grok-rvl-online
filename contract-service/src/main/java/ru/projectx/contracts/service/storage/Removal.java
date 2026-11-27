package ru.projectx.contracts.service.storage;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.document.PrintedFormType;

record Removal(UUID attachmentId, @Nullable UUID individualRequestId, @Nullable UUID participantId,
        PrintedFormType type, String key) {
}
