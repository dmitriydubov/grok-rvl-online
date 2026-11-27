package ru.projectx.contracts.service.storage;

import java.util.UUID;

public interface AttachmentGuard {

    void ensureRemovable(UUID individualRequestId, UUID participantId);
}
