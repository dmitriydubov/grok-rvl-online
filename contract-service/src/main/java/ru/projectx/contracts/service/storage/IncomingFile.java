package ru.projectx.contracts.service.storage;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface IncomingFile {

    UUID attachmentId();

    @Nullable String contentType();

    long size();

    ContentSource content();
}
