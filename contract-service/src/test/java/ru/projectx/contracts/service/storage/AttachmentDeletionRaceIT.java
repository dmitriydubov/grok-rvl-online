package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.entity.issuance.TripPeriod;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.RowRaces;

@IntegrationTest
@TestPropertySource(properties = "projectx.database.session-timeouts.lock-timeout=1s")
class AttachmentDeletionRaceIT {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    private static final UUID ATTACHMENT = UUID.fromString("b2000008-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final String KEY = "certificates/0b6c/" + ATTACHMENT + ".jpg";
    private static final String HOLD = "select id from contracts where id = ? for update";

    @Autowired
    private PrintedFormRepository documents;

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private AttachmentGuard guard;

    @Autowired
    private AttachmentMetrics metrics;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private Clock clock;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final CertificateStore store = mock(CertificateStore.class);
    private AttachmentDeletion deletion;
    private UUID contractId;

    @BeforeEach
    void certificateAndDraft() {
        jdbc.sql("truncate table contract_documents, contracts cascade").update();
        deletion = new AttachmentDeletion(documents, guard, store, metrics, transactions, clock);
        Contract draft = Contract.open(new ContractDraft("DKM-2026-000108", terms(), PARTICIPANT,
                new BigDecimal("34000.00")), NOW);
        draft.offerDraft();
        contractId = contracts.saveAndFlush(draft).id();
        documents.saveAndFlush(PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE,
                DocumentAttachments.certificate(REQUEST, PARTICIPANT, ATTACHMENT),
                new StoredFile(KEY, "ab", 4, "image/jpeg"), NOW));
    }

    @Test
    void heldContractRow_isBusyAndDoesNotRemoveTheObject() throws SQLException {
        new RowRaces(dataSource, jdbc).whileTheRowIsHeld(HOLD, contractId,
                () -> assertThatThrownBy(() -> deleteAs("k16b-busy")).isInstanceOf(AttachmentBusyException.class));

        verify(store, never()).remove(KEY);
        assertThat(deletedAt()).isNull();
    }

    @Test
    void twoDeletes_keepWhicheverRequestMarkedTheRow() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> one = pool.submit(() -> deleteAs("k16b-one"));
            Future<?> two = pool.submit(() -> deleteAs("k16b-two"));
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        }

        String markedBy = requestStamp();
        assertThat(markedBy).isIn("k16b-one", "k16b-two");
        assertThat(deletedAt()).isEqualTo(clock.instant());
        deleteAs("k16b-three");
        assertThat(requestStamp()).isEqualTo(markedBy);
        assertThat(deletedAt()).isEqualTo(clock.instant());
    }

    private void deleteAs(String requestId) {
        MDC.put(RequestIds.MDC_KEY, requestId);
        try {
            deletion.delete(ATTACHMENT);
        }
        finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    private Instant deletedAt() {
        return jdbc.sql("select deleted_at from contract_documents where attachment_id = :id")
                .param("id", ATTACHMENT).query(OffsetDateTime.class).optional().map(OffsetDateTime::toInstant)
                .orElse(null);
    }

    private String requestStamp() {
        return jdbc.sql("select deleted_request_id from contract_documents where attachment_id = :id")
                .param("id", ATTACHMENT).query(String.class).single();
    }

    private static IssuanceTerms terms() {
        return new IssuanceTerms(new RequestReference(REQUEST,
                UUID.fromString("e12aab1a-1582-4b3a-af36-d30e637953f1"),
                UUID.fromString("a23ba061-f742-4b20-8b95-36a0e525c087")),
                UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc"),
                UUID.fromString("2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a"),
                new TripPeriod(LocalDate.parse("2026-04-10"), LocalDate.parse("2026-04-24")));
    }
}
