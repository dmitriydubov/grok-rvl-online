package ru.projectx.contracts.repository.journal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.outbox.OutboxMessage;
import ru.projectx.contracts.entity.outbox.OutboxStatus;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class OutboxMessageRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final String FIRST = "7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442";
    private static final String SECOND = "0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f";
    private static final String THIRD = "c3f80a67-9d12-4b58-8e44-0a7b1f25e903";
    private static final String FOURTH = "d4a91b78-0e23-4c69-9f55-1b8c2a36f014";
    private static final String FIFTH = "5b1e7d44-2c09-4a63-8f17-6e0b9a3c1d05";
    private static final String SIXTH = "6c2f8e55-3d10-4b74-9a28-7f1c0b4d2e16";
    private static final String STATUS_CHANGE = "ContractStatusChanged";
    private static final String POLICY_REQUEST = "LmPolicyRequested";
    private static final String DOCUMENT_FIX = "PaymentOrderFixed";

    @Autowired
    private OutboxMessageRepository messages;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyJournal() {
        messages.deleteAll();
    }

    @Test
    void message_isStoredWithNativeStatusAndJsonBody() {
        OutboxMessage stored = transaction.execute(status -> messages.save(message(FIRST, NOW)));

        assertThat(messages.findById(stored.id())).get().satisfies(found -> {
            assertThat(found.status()).isEqualTo(OutboxStatus.NEW);
            assertThat(found.payload()).contains("{\"data\": {\"n\": 1}}");
            assertThat(found.envelope().messageId()).isEqualTo(UUID.fromString(FIRST));
        });
        assertThat(jdbc.sql("select status::text from outbox_messages").query(String.class).single()).isEqualTo("NEW");
    }

    @Test
    void dueOfEventTypes_areNewMessagesOfThoseTypesWhoseNextAttemptHasComeInOrderOfCreationWithinTheLimit() {
        transaction.executeWithoutResult(status -> {
            messages.save(message(SECOND, NOW.plusSeconds(1)));
            messages.save(message(FIRST, NOW));
            messages.save(message(THIRD, NOW.minusSeconds(5))).markRetry("отказ", NOW.plusSeconds(60));
            messages.save(message(FOURTH, NOW.minusSeconds(9))).markRetry("отказ", NOW);
            messages.save(message(FIFTH, NOW.minusSeconds(10))).markPublished(NOW);
            messages.save(message(SIXTH, POLICY_REQUEST, NOW.minusSeconds(20)));
        });

        assertThat(dueOf(Set.of(STATUS_CHANGE), 10)).containsExactly(FOURTH, FIRST, SECOND);
        assertThat(dueOf(Set.of(STATUS_CHANGE), 1)).containsExactly(FOURTH);
        assertThat(dueOf(Set.of(POLICY_REQUEST, STATUS_CHANGE), 10)).containsExactly(SIXTH, FOURTH, FIRST, SECOND);
        assertThat(dueOf(Set.of(DOCUMENT_FIX), 10)).isEmpty();
    }

    @Test
    void dueExceptEventTypes_areNewMessagesOfOtherTypesWhoseNextAttemptHasComeInOrderOfCreationWithinTheLimit() {
        transaction.executeWithoutResult(status -> {
            messages.save(message(FIRST, NOW));
            messages.save(message(SECOND, DOCUMENT_FIX, NOW.plusSeconds(1)));
            messages.save(message(THIRD, DOCUMENT_FIX, NOW.minusSeconds(5))).markRetry("отказ", NOW.plusSeconds(60));
            messages.save(message(FOURTH, POLICY_REQUEST, NOW.minusSeconds(9))).markRetry("отказ", NOW);
            messages.save(message(FIFTH, POLICY_REQUEST, NOW.minusSeconds(10))).markPublished(NOW);
            messages.save(message(SIXTH, DOCUMENT_FIX, NOW.minusSeconds(20))).markFailed("отказ");
        });

        assertThat(dueExcept(Set.of(STATUS_CHANGE), 10)).containsExactly(FOURTH, SECOND);
        assertThat(dueExcept(Set.of(STATUS_CHANGE), 1)).containsExactly(FOURTH);
        assertThat(dueExcept(Set.of(STATUS_CHANGE, POLICY_REQUEST), 10)).containsExactly(SECOND);
        assertThat(dueExcept(Set.of(STATUS_CHANGE, POLICY_REQUEST, DOCUMENT_FIX), 10)).isEmpty();
    }

    @Test
    void dueExceptNoEventTypes_areAllNewMessagesWhoseNextAttemptHasCome() {
        transaction.executeWithoutResult(status -> {
            messages.save(message(FIRST, NOW));
            messages.save(message(SECOND, DOCUMENT_FIX, NOW.minusSeconds(1)));
        });

        assertThat(dueExcept(Set.of(), 10)).containsExactly(SECOND, FIRST);
    }

    @Test
    void erasure_clearsFinishedBodiesCreatedBeforeCutoffInBatchesAndKeepsPendingAndRecentOnes() {
        transaction.executeWithoutResult(status -> {
            messages.save(message(FIRST, NOW.minusSeconds(3))).markPublished(NOW);
            messages.save(message(SECOND, NOW.minusSeconds(2))).markFailed("отказ");
            messages.save(message(THIRD, NOW.minusSeconds(2)));
            messages.save(message(FOURTH, NOW)).markPublished(NOW);
        });

        int firstBatch = transaction.execute(status -> messages.eraseFinishedPayloadsCreatedBefore(NOW.minusSeconds(1), 1));
        int secondBatch = transaction.execute(status -> messages.eraseFinishedPayloadsCreatedBefore(NOW.minusSeconds(1), 1));
        int thirdBatch = transaction.execute(status -> messages.eraseFinishedPayloadsCreatedBefore(NOW.minusSeconds(1), 1));

        assertThat(List.of(firstBatch, secondBatch, thirdBatch)).containsExactly(1, 1, 0);
        assertThat(messages.findAll()).filteredOn(m -> m.payload().isPresent())
                .extracting(m -> m.envelope().messageId())
                .containsExactlyInAnyOrder(UUID.fromString(THIRD), UUID.fromString(FOURTH));
    }

    @Test
    void submissionRecord_isFoundInEveryStatusAndAfterThePayloadIsErased() {
        UUID contractId = UUID.fromString("0b6c1d2e-7f3a-4b5c-9d8e-1f2a3b4c5d6e");
        UUID otherContract = UUID.fromString("1c7d3e5f-8a4b-4d6e-9f50-2b3c4d5e6f70");
        transaction.executeWithoutResult(status -> {
            messages.save(submission(FIRST, contractId, NOW.minusSeconds(3))).markPublished(NOW);
            messages.save(submission(SECOND, contractId, NOW.minusSeconds(2))).markFailed("отказ");
            messages.save(submission(THIRD, otherContract, NOW));
            messages.save(message(FOURTH, NOW));
        });
        transaction.executeWithoutResult(status -> messages.eraseFinishedPayloadsCreatedBefore(NOW.minusSeconds(1), 10));

        assertThat(exists(REQUEST, contractId)).isTrue();
        assertThat(messages.findAll()).filteredOn(message -> contractId.equals(message.aggregateId()))
                .filteredOn(message -> "PUBLISHED".equals(message.status().name()))
                .singleElement()
                .satisfies(message -> assertThat(message.payload()).isEmpty());
        assertThat(exists(REQUEST, otherContract)).isTrue();
        assertThat(exists(UUID.randomUUID(), contractId)).isFalse();
    }

    private boolean exists(UUID correlationId, UUID contractId) {
        return messages.existsByCorrelationIdAndAggregateTypeAndEventTypeAndAggregateIdIn(correlationId, "CONTRACT",
                "KmContractSubmitted", List.of(contractId));
    }

    private static OutboxMessage submission(String messageId, UUID contractId, Instant createdAt) {
        return OutboxMessage.compose(new OutboxDraft("CONTRACT", contractId,
                new MessageEnvelope(UUID.fromString(messageId), REQUEST, "rvl.km-contract.submitted",
                        "KmContractSubmitted", 1), "{\"data\": {\"n\": 1}}"), createdAt);
    }

    private List<String> dueOf(Set<String> eventTypes, int limit) {
        return ids(messages.findDueOf(eventTypes, NOW, Limit.of(limit)));
    }

    private List<String> dueExcept(Set<String> eventTypes, int limit) {
        return ids(messages.findDueExcept(eventTypes, NOW, Limit.of(limit)));
    }

    private static List<String> ids(List<OutboxMessage> found) {
        return found.stream().map(message -> message.envelope().messageId().toString()).toList();
    }

    private static OutboxMessage message(String messageId, Instant createdAt) {
        return message(messageId, STATUS_CHANGE, createdAt);
    }

    private static OutboxMessage message(String messageId, String eventType, Instant createdAt) {
        return OutboxMessage.compose(new OutboxDraft("INDIVIDUAL_REQUEST", REQUEST,
                new MessageEnvelope(UUID.fromString(messageId), REQUEST, "rvl.contract-status.changed",
                        eventType, 1), "{\"data\": {\"n\": 1}}"), createdAt);
    }
}
