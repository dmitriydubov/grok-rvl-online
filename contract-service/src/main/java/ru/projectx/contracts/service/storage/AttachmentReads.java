package ru.projectx.contracts.service.storage;

import java.util.UUID;

public interface AttachmentReads {

    OpenedAttachment open(UUID attachmentId);
}
