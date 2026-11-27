package ru.projectx.contracts.service.storage;

record StoredCopy(String key, String contentType, long size, String hash) {
}
