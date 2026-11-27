package ru.projectx.contracts.service.storage;

import java.io.InputStream;

public record OpenedAttachment(String contentType, long size, String hash, InputStream body) {
}
