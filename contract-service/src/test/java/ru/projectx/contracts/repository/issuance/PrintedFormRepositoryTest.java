package ru.projectx.contracts.repository.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.ReceivedDocuments;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class PrintedFormRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final ParticipantAttachment OWNER = new ParticipantAttachment(REQUEST, PARTICIPANT, ATTACHMENT);
    private static final StoredFile FILE = new StoredFile("certificates/0b6c1d2e/" + ATTACHMENT + ".jpg",
            "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251", 76, "image/jpeg");

    @Autowired
    private PrintedFormRepository documents;

    @Autowired
    private IssuanceRequestRepository requests;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void knownRequest() {
        jdbc.sql("truncate table contract_documents, request_participants, issuance_requests cascade").update();
        transaction.executeWithoutResult(status -> {
            IssuanceRequest request = IssuanceRequest.start(REQUEST, NOW);
            request.takeComposition(List.of(new RequestParticipant(PARTICIPANT, DocumentKind.LM_POLICY)), NOW);
            requests.save(request);
        });
    }

    @Test
    void certificate_isFoundByAttachmentWithItsOwnerAndFile() {
        documents.saveAndFlush(PrintedForm.receivedCertificate(OWNER, FILE, NOW));

        assertThat(documents.findByAttachmentAttachmentId(ATTACHMENT)).get().satisfies(found -> {
            assertThat(found.attachment()).contains(OWNER);
            assertThat(found.storedFile()).contains(FILE);
            assertThat(found.docType()).isEqualTo(PrintedFormType.MEDICAL_CERTIFICATE);
            assertThat(found.lmPolicyId()).isEmpty();
            assertThat(found.createdAt()).isEqualTo(NOW);
        });
        assertThat(jdbc.sql("select doc_type::text from contract_documents").query(String.class).single())
                .isEqualTo("MEDICAL_CERTIFICATE");
    }

    @Test
    void unknownAttachment_isNotFound() {
        assertThat(documents.findByAttachmentAttachmentId(ATTACHMENT)).isEmpty();
    }

    @Test
    void secondRecordOfTheSameAttachment_isRefusedByTheDatabase() {
        documents.saveAndFlush(PrintedForm.receivedCertificate(OWNER, FILE, NOW));
        ParticipantAttachment again = new ParticipantAttachment(REQUEST, PARTICIPANT, ATTACHMENT);

        assertThatThrownBy(() -> documents.saveAndFlush(PrintedForm.receivedCertificate(again, FILE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void certificateOfAnUnknownRequest_isStored() {
        UUID request = UUID.randomUUID();
        UUID attachment = UUID.randomUUID();
        ParticipantAttachment stranger = new ParticipantAttachment(request, PARTICIPANT, attachment);

        documents.saveAndFlush(PrintedForm.receivedCertificate(stranger, new StoredFile(FILE.key(), FILE.hash(),
                FILE.size(), FILE.contentType()), NOW));

        assertThat(documents.findByAttachmentAttachmentId(attachment)).isPresent();
    }

    @Test
    void certificateWithoutFileOrParticipant_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> insert("individual_request_id, participant_id, attachment_id",
                ":request, :participant, :attachment"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_contract_documents_attachment_file");
        assertThatThrownBy(() -> insert("individual_request_id, attachment_id, file_key, file_hash, file_size, content_type",
                ":request, :attachment, 'k', 'h', 1, 'image/png'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_medical_certificate");
    }

    @Test
    void medicalCertificateTypeOutsideAParticipantAttachment_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, usk_file_url) values (gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, "
                        + "now(), :request, 'https://s3.example.test/x.pdf')").param("request", REQUEST).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, participant_id, attachment_id, file_key, file_hash, file_size, content_type) "
                        + "values (gen_random_uuid(), 'FINAL', 'POLICY', 1, now(), :request, :participant, :attachment, "
                        + "'k', 'h', 1, 'image/png')")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_printed_form");
    }

    @Test
    void documentWithoutAnyOwner_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, file_key) "
                        + "values (gen_random_uuid(), 'FINAL', 'POLICY', 1, now(), 'k')").update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_contract_documents_single_owner");
    }

    @Test
    void consent_isStoredByPolicyAndFamilyMember() {
        UUID policy = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID attachment = UUID.randomUUID();

        documents.saveAndFlush(ReceivedDocuments.consent(
                new ru.projectx.contracts.entity.document.ConsentBinding(policy, member), attachment, FILE, NOW));

        assertThat(jdbc.sql("select policy_id, family_member_id, doc_type::text from contract_documents "
                        + "where attachment_id = :attachment")
                .param("attachment", attachment)
                .query((row, index) -> row.getObject("policy_id", UUID.class) + " " + row.getObject("family_member_id",
                        UUID.class) + " " + row.getString("doc_type")).single())
                .isEqualTo(policy + " " + member + " PD_CONSENT");
    }

    @Test
    void twoOwnersOrNone_areRefused() {
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at) "
                        + "values (gen_random_uuid(), 'FINAL', 'POLICY', 1, now())").update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_single_owner");
        UUID contract = insertContract();
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "contract_id, individual_request_id, participant_id, attachment_id, file_key, file_hash, "
                        + "file_size, content_type) values (gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), "
                        + ":contract, :request, :participant, :attachment, 'k', 'h', 1, 'image/png')")
                .param("contract", contract).param("request", REQUEST).param("participant", PARTICIPANT)
                .param("attachment", ATTACHMENT).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_single_owner");
    }

    @Test
    void mixedCertificateAndConsentOwners_areRefused() {
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, participant_id, attachment_id, policy_id, file_key, file_hash, "
                        + "file_size, content_type) values (gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), "
                        + ":request, :participant, :attachment, gen_random_uuid(), 'k', 'h', 1, 'image/png')")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_medical_certificate");
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "policy_id, family_member_id, attachment_id, participant_id, file_key, file_hash, file_size, "
                        + "content_type) values (gen_random_uuid(), 'FINAL', 'PD_CONSENT', 1, now(), gen_random_uuid(), "
                        + "gen_random_uuid(), :attachment, :participant, 'k', 'h', 1, 'image/png')")
                .param("participant", PARTICIPANT).param("attachment", ATTACHMENT).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_pd_consent");
    }

    @Test
    void deletionMarkOnAPrintedFormOrHalfFilled_isRefused() {
        UUID contract = insertContract();
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, contract_id, kind, doc_type, version, "
                        + "created_at, deleted_at, deleted_request_id) values (gen_random_uuid(), :contract, 'FINAL', "
                        + "'CONTRACT', 1, now(), now(), 'req')").param("contract", contract).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_printed_form");
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, participant_id, attachment_id, file_key, file_hash, file_size, "
                        + "content_type, deleted_at) values (gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), "
                        + ":request, :participant, :attachment, 'k', 'h', 1, 'image/png', now())")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_deletion");
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, participant_id, attachment_id, file_key, file_hash, file_size, "
                        + "content_type, deleted_at, deleted_request_id) values (gen_random_uuid(), 'FINAL', "
                        + "'MEDICAL_CERTIFICATE', 1, now(), :request, :participant, :attachment, 'k', 'h', 1, "
                        + "'image/png', now(), '   ')")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_deletion");
    }

    @Test
    void rollbackRemovesEarlyCertificateAndConsentThenUpdateRestoresThem() throws Exception {
        UUID early = UUID.randomUUID();
        UUID consent = UUID.randomUUID();
        insertAttachment(early, ":request, :participant, :attachment", early, UUID.randomUUID(), early);
        insertConsent(consent);
        try (Connection connection = dataSource.getConnection()) {
            Liquibase liquibase = changelog(connection);
            liquibase.rollback(1, "");
            try {
                assertThat(count(early)).isZero();
                assertThat(count(consent)).isZero();
                assertThatThrownBy(() -> insertAttachment(early, ":request, :participant, :attachment", early,
                        UUID.randomUUID(), early)).isInstanceOf(DataIntegrityViolationException.class);
            } finally {
                liquibase.update("");
            }
        }
        insertAttachment(early, ":request, :participant, :attachment", early, UUID.randomUUID(), early);
        insertConsent(consent);
        assertThat(count(early)).isEqualTo(1);
        assertThat(count(consent)).isEqualTo(1);
    }

    private Liquibase changelog(Connection connection) throws Exception {
        return new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
    }

    private void insertAttachment(UUID id, String values, UUID request, UUID participant, UUID attachment) {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, individual_request_id, "
                        + "participant_id, attachment_id, file_key, file_hash, file_size, content_type) values "
                        + "(:id, 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), " + values + ", 'k', 'h', 1, 'image/jpeg')")
                .param("id", id).param("request", request).param("participant", participant)
                .param("attachment", attachment).update();
    }

    private void insertConsent(UUID attachment) {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, policy_id, "
                        + "family_member_id, attachment_id, file_key, file_hash, file_size, content_type) values "
                        + "(:id, 'FINAL', 'PD_CONSENT', 1, now(), :policy, :member, :attachment, 'k', 'h', 1, "
                        + "'image/jpeg')")
                .param("id", UUID.randomUUID()).param("policy", UUID.randomUUID()).param("member", UUID.randomUUID())
                .param("attachment", attachment).update();
    }

    private int count(UUID attachment) {
        return jdbc.sql("select count(*) from contract_documents where attachment_id = :attachment")
                .param("attachment", attachment).query(Integer.class).single();
    }

    private UUID insertContract() {
        UUID contract = UUID.randomUUID();
        UUID insured = UUID.randomUUID();
        jdbc.sql("insert into contracts (id, number, status, individual_request_id, insured_participant_id, "
                        + "policyholder_participant_id, insurance_contract_id, insurer_policy_id, date_from, date_to, "
                        + "premium_amount, entity_version, created_at, updated_at) values (:id, :number, 'DRAFT_READY', "
                        + ":request, :insured, :holder, gen_random_uuid(), gen_random_uuid(), '2026-04-10', "
                        + "'2026-04-24', 1, 1, now(), now())")
                .param("id", contract).param("number", "DKM-TEST-" + contract.toString().substring(0, 8))
                .param("request", UUID.randomUUID()).param("insured", insured).param("holder", UUID.randomUUID())
                .update();
        return contract;
    }

    private void insert(String columns, String values) {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, " + columns + ") values "
                        + "(gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), " + values + ")")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT)
                .update();
    }
}
