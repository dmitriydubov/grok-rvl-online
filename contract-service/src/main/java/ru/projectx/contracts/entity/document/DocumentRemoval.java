package ru.projectx.contracts.entity.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

@Embeddable
public record DocumentRemoval(
        @Column(name = "deleted_at", updatable = false) Instant deletedAt,
        @Column(name = "deleted_request_id", length = 64, updatable = false) String deletedRequestId) {
}
