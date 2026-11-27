package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.contracts.config.storage.StorageProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class AttachmentRemovalSteps {

    private static final String REQUEST = "61a7982c-a0b4-4bbf-9527-60b40b752803";
    private static final String INSURED = "ae8312c3-350b-4d0b-806c-5e2460e74dbc";
    private static final String SPOUSE = "9275471c-d2fe-484f-97a6-d6d7b1267b5e";
    private static final String POLICY = "a23ba061-f742-4b20-8b95-36a0e525c087";
    private static final String RELATIVE = "c45dc283-1964-4d42-8d17-58c2a747e209";
    private static final String PROGRAM = "2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a";
    private static final String TASK = "b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11";
    private static final String CANCEL = "c3f80a67-9d12-4b58-8e44-0a7b1f25e903";
    private static final String PHRASE = "СТЕНД: вымышленная справка 070/у, участник 1";
    private static final String CONSENT_PHRASE = "СТЕНД: вымышленное согласие на обработку ПДн, удаление";
    private static final byte MARKER = (byte) 0xFF;
    private static final String NEW_UUID = "новый UUID";
    private static final String PRINTED_FORM_URL = "https://1c-usk.example/rvl/km/DKM-2026-000001.pdf";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AttachmentCalls calls;
    private final TaskPublishingSteps tasks;
    private final InboxSteps inbox;
    private final OutboxSteps outboxSteps;
    private final AcceptanceSteps acceptance;
    private final KmContractPaymentSteps payments;
    private final KmContractSubmissionSteps submission;
    private final OneCPublishingSteps oneC;
    private final CertificateStorageSteps storageSteps;
    private final CertificateRecords certificates;
    private final StandStorage storage;
    private final StorageProperties settings;
    private final OutboxRecords outbox;
    private final JdbcClient jdbc;
    private final Map<String, AttachmentRequest> uploads = new HashMap<>();
    private String firstDeletedAt;
    private String firstRequestId;

    public AttachmentRemovalSteps(AttachmentCalls calls, TaskPublishingSteps tasks, InboxSteps inbox,
            OutboxSteps outboxSteps, AcceptanceSteps acceptance, KmContractPaymentSteps payments,
            KmContractSubmissionSteps submission, OneCPublishingSteps oneC, CertificateStorageSteps storageSteps,
            CertificateRecords certificates, StandStorage storage, StorageProperties settings, OutboxRecords outbox,
            JdbcClient jdbc) {
        this.calls = calls;
        this.tasks = tasks;
        this.inbox = inbox;
        this.outboxSteps = outboxSteps;
        this.acceptance = acceptance;
        this.payments = payments;
        this.submission = submission;
        this.oneC = oneC;
        this.storageSteps = storageSteps;
        this.certificates = certificates;
        this.storage = storage;
        this.settings = settings;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    @Дано("стенд подготовил вложение, которое можно удалить: {string}")
    public void removable(String context) throws Exception {
        switch (context) {
            case "согласие" -> uploadConsent("b2000001-5a7b-4d21-9f6e-1b0a7c4d8e21", RELATIVE);
            case "согласие при переданном договоре" -> {
                submittedSpouse("b2000010-5a7b-4d21-9f6e-1b0a7c4d8e21");
                uploadConsent("b2000002-5a7b-4d21-9f6e-1b0a7c4d8e21", SPOUSE);
            }
            case "согласие при выпущенном полисе" -> {
                submittedSpouse("b2000010-5a7b-4d21-9f6e-1b0a7c4d8e21");
                register();
                uploadConsent("b2000003-5a7b-4d21-9f6e-1b0a7c4d8e21", SPOUSE);
            }
            case "справка до первой задачи" -> uploadCertificate("b2000004-5a7b-4d21-9f6e-1b0a7c4d8e21");
            case "справка при договоре DRAFT_READY" -> {
                issueTask();
                uploadCertificate("b2000005-5a7b-4d21-9f6e-1b0a7c4d8e21");
            }
            case "справка при договоре ACCEPTED" -> {
                issueTask();
                uploadCertificate("b2000006-5a7b-4d21-9f6e-1b0a7c4d8e21");
                accept();
            }
            case "справка при договоре INVOICE_ISSUED" -> {
                issueTask();
                uploadCertificate("b2000007-5a7b-4d21-9f6e-1b0a7c4d8e21");
                accept();
                markInvoice();
            }
            case "справка при договоре PAID" -> {
                issueTask();
                uploadCertificate("b2000008-5a7b-4d21-9f6e-1b0a7c4d8e21");
                accept();
                payments.contractPaid(SPOUSE, REQUEST, "2026-02-10T07:20:15Z");
            }
            case "справка после прекращения заявки" -> {
                issueTask();
                uploadCertificate("b2000009-5a7b-4d21-9f6e-1b0a7c4d8e21");
                cancel();
            }
            default -> throw new AssertionError("контекста «" + context + "» нет в перечне шага");
        }
    }

    @Дано("стенд подготовил справку, удаление которой запрещено: {string}")
    public void locked(String reason) throws Exception {
        switch (reason) {
            case "договор передан в 1С" -> {
                submittedSpouse("c4000001-5a7b-4d21-9f6e-1b0a7c4d8e21");
            }
            case "полис выпущен" -> {
                submittedSpouse("c4000002-5a7b-4d21-9f6e-1b0a7c4d8e21");
                register();
            }
            case "передача в журнале, договор в ошибке" -> failedSubmission("c4000003-5a7b-4d21-9f6e-1b0a7c4d8e21",
                    "ERROR");
            case "передача в журнале, договор прекращён" -> {
                submittedSpouse("c4000004-5a7b-4d21-9f6e-1b0a7c4d8e21");
                cancel();
            }
            case "дежурный вернул договор в оплаченные" -> returned("c4000005-5a7b-4d21-9f6e-1b0a7c4d8e21");
            case "справка загружена после передачи" -> {
                submittedSpouse("c4000007-5a7b-4d21-9f6e-1b0a7c4d8e21");
                uploadCertificate("c4000006-5a7b-4d21-9f6e-1b0a7c4d8e21");
            }
            default -> throw new AssertionError("запрета «" + reason + "» нет в перечне шага");
        }
    }

    @Дано("стенд подготовил повторное удаление: {string}, хранилище {string}")
    public void repeat(String situation, String storageState) throws Exception {
        switch (situation) {
            case "уже удалено" -> {
                uploadCertificate("a6000001-5a7b-4d21-9f6e-1b0a7c4d8e21");
                calls.read("DELETE", deletePath("a6000001-5a7b-4d21-9f6e-1b0a7c4d8e21"), Optional.of("k16b-first-01"));
                assertThat(calls.last().status()).isEqualTo(204);
                firstDeletedAt = stamp("a6000001-5a7b-4d21-9f6e-1b0a7c4d8e21");
                firstRequestId = requestStamp("a6000001-5a7b-4d21-9f6e-1b0a7c4d8e21");
            }
            case "неизвестное вложение" -> {
            }
            case "печатная форма" -> {
                submittedSpouse("b2000010-5a7b-4d21-9f6e-1b0a7c4d8e21");
                register();
            }
            default -> throw new AssertionError("случая «" + situation + "» нет в перечне шага");
        }
        if ("остановлено".equals(storageState)) {
            storageSteps.storageDown(storageState);
        } else if (!"доступно".equals(storageState)) {
            throw new AssertionError("хранилища «" + storageState + "» нет в перечне шага");
        }
    }

    @Дано("стенд подготовил активное вложение для удаления при сбое хранилища: вид {string}")
    public void failingStorage(String kind) throws Exception {
        if ("справка".equals(kind)) {
            issueTask();
            uploadCertificate("d5000001-5a7b-4d21-9f6e-1b0a7c4d8e21");
            accept();
            payments.contractPaid(SPOUSE, REQUEST, "2026-02-10T07:20:15Z");
            return;
        }
        if ("согласие".equals(kind)) {
            uploadConsent("d5000002-5a7b-4d21-9f6e-1b0a7c4d8e21", RELATIVE);
            return;
        }
        throw new AssertionError("вида «" + kind + "» нет в перечне шага");
    }

    @Когда("сервис Заявок удаляет вложение {string} с заголовком X-Request-Id {string}")
    public void deleteWithRequestId(String attachment, String header) throws IOException, InterruptedException {
        calls.read("DELETE", deletePath(attachment), requestId(header));
    }

    @Когда("сервис Заявок удаляет вложение {string}")
    public void delete(String attachment) throws IOException, InterruptedException {
        calls.read("DELETE", deletePath(attachment), Optional.empty());
    }

    @Когда("сервис Заявок удаляет вложение случая {string} с заголовком X-Request-Id {string}")
    public void deleteSituation(String situation, String header) throws IOException, InterruptedException {
        calls.read("DELETE", deletePath(situationId(situation)), requestId(header));
    }

    @Тогда("Договоры ответили на удаление вложения кодом {int} и пустым телом")
    public void deleted(int status) {
        assertThat(calls.last().status()).isEqualTo(status);
        assertThat(calls.lastBinary().body()).isEmpty();
    }

    @Тогда("вложение {string} в реестре документов: момент удаления {string}, идентификатор запроса удаления {string}")
    public void deletionStamp(String attachment, String moment, String request) {
        assertThat(stamp(attachment)).isEqualTo(OffsetDateTime.parse(moment).toInstant().toString());
        String stored = requestStamp(attachment);
        if (NEW_UUID.equals(request)) {
            String header = calls.last().requestId().orElseThrow();
            assertThat(stored).isEqualTo(header);
            assertThat(UUID.fromString(stored)).hasToString(stored);
            return;
        }
        assertThat(stored).isEqualTo(request);
    }

    @Тогда("объекта вложения {string} в хранилище справок нет")
    public void objectGone(String attachment) throws Exception {
        String key = certificates.byAttachment(attachment).key();
        assertThat(storage.keys(settings.bucket(), key)).doesNotContain(key);
    }

    @Тогда("повторная загрузка вложения {string} тем же файлом отклонена с кодом {int} {string}")
    public void reuploadRejected(String attachment, int status, String code) throws IOException, InterruptedException {
        calls.send(uploads.get(attachment));
        assertThat(calls.last().status()).as(calls.last().body()).isEqualTo(status);
        assertThat(calls.body(calls.last()).path("error").path("code").asString()).isEqualTo(code);
    }

    @Тогда("передача после удаления контекста {string} не содержит удалённую справку")
    public void submissionOmits(String context) throws Exception {
        if (context.startsWith("согласие")) {
            consentStays(context);
            return;
        }
        if ("справка после прекращения заявки".equals(context)) {
            submission.preparationPass();
            assertThat(outbox.ofRequest(REQUEST)).extracting(OutboxRecords.Entry::eventType)
                    .doesNotContain("KmContractSubmitted");
            return;
        }
        if ("справка до первой задачи".equals(context) || "справка при договоре DRAFT_READY".equals(context)) {
            if ("справка до первой задачи".equals(context)) {
                issueTask();
            }
            accept();
            payments.contractPaid(SPOUSE, REQUEST, "2026-02-10T07:20:15Z");
        }
        if ("справка при договоре ACCEPTED".equals(context) || "справка при договоре INVOICE_ISSUED".equals(context)) {
            payments.contractPaid(SPOUSE, REQUEST, "2026-02-10T07:20:15Z");
        }
        submission.preparationPass();
        String attachment = deletedAttachment(context);
        assertThat(outbox.ofRequest(REQUEST)).filteredOn(entry -> "KmContractSubmitted".equals(entry.eventType()))
                .singleElement()
                .satisfies(entry -> submittedWithout(entry, attachment));
    }

    @Тогда("повторное удаление случая {string} не изменило первичные пометки и не создало лишнего")
    public void repeatUntouched(String situation) {
        switch (situation) {
            case "уже удалено" -> {
                assertThat(stamp("a6000001-5a7b-4d21-9f6e-1b0a7c4d8e21")).isEqualTo(firstDeletedAt);
                assertThat(requestStamp("a6000001-5a7b-4d21-9f6e-1b0a7c4d8e21")).isEqualTo(firstRequestId);
            }
            case "неизвестное вложение" -> assertThat(count("a6000099-5a7b-4d21-9f6e-1b0a7c4d8e99")).isZero();
            case "печатная форма" -> {
                assertThat(jdbc.sql(
                        "select deleted_at from contract_documents where doc_type = 'CONTRACT' and attachment_id is null")
                        .query(OffsetDateTime.class).optional()).isEmpty();
                assertThat(printedFormUrl()).isEqualTo(PRINTED_FORM_URL);
            }
            default -> throw new AssertionError("случая «" + situation + "» нет в перечне шага");
        }
    }

    @Дано("хранилище справок для удаления недоступно: {string}")
    public void deleteStorageDown(String outage) throws Exception {
        if ("бакета нет".equals(outage)) {
            storageSteps.bucketRemoved();
            return;
        }
        storageSteps.storageDown(outage);
    }

    @Когда("хранилище справок для удаления снова доступно")
    public void deleteStorageBack() throws Exception {
        if (storage.disrupted()) {
            storage.recover();
        }
        if (!storage.bucketExists(settings.bucket())) {
            storage.recreateBucket(settings.bucket());
            restoreUploadedObjects();
        }
        storageSteps.storageBack();
    }

    private void restoreUploadedObjects() throws Exception {
        List<String[]> rows = jdbc.sql("""
                select attachment_id::text as attachment, file_key
                  from contract_documents
                 where file_key is not null
                """).query((row, index) -> new String[] {row.getString("attachment"), row.getString("file_key")}).list();
        for (String[] row : rows) {
            storage.putObject(settings.bucket(), row[1], calls.acceptedContent(row[0]));
        }
    }

    @Тогда("объект вложения {string} не удалён сервисом при недоступности {string}")
    public void objectKept(String attachment, String outage) throws Exception {
        if ("бакета нет".equals(outage)) {
            assertThat(storage.bucketExists(settings.bucket())).isFalse();
            return;
        }
        storage.recover();
        assertThat(storage.object(settings.bucket(), certificates.byAttachment(attachment).key()))
                .isEqualTo(calls.acceptedContent(attachment));
    }

    private void submittedSpouse(String attachment) throws Exception {
        issueTask();
        uploadCertificate(attachment);
        accept();
        payments.contractPaid(SPOUSE, REQUEST, "2026-02-10T07:20:15Z");
        submission.preparationPass();
        outboxSteps.publicationPass();
    }

    private void failedSubmission(String attachment, String status) throws Exception {
        submittedSpouse(attachment);
        jdbc.sql("""
                update contracts
                   set status = cast(:status as contract_status)
                 where insured_participant_id = :participant
                """)
                .param("status", status).param("participant", UUID.fromString(SPOUSE)).update();
    }

    private void returned(String attachment) throws Exception {
        failedSubmission(attachment, "ERROR");
        jdbc.sql("""
                update contracts
                   set status = 'PAID', error_code = null, error_message = null
                 where insured_participant_id = :participant
                """)
                .param("participant", UUID.fromString(SPOUSE)).update();
    }

    private void register() throws Exception {
        oneC.registrationPublished("0c6a9f31-58b2-4e77-91d0-3a7e5b1c22f9", SPOUSE, REQUEST, Map.of(
                "status", "REGISTERED",
                "contract_usk_id", "a71f3c98-4e20-4a55-93b1-5d0c7e2f8814",
                "contract_usk_number", "DKM-000004417",
                "policy.external_id", "1d9a6e77-4c02-4b88-a15e-30f7b2c9de41",
                "policy.number", "СТЕНД-0001",
                "policy.date_from", "2026-04-10",
                "policy.date_to", "2026-04-24",
                "documents[0].doc_type", "CONTRACT",
                "documents[0].file_url", "https://1c-usk.example/rvl/km/DKM-2026-000001.pdf",
                "documents[0].file_size", "205312"));
        inbox.everythingProcessed();
    }

    private void issueTask() throws Exception {
        tasks.taskPublished(TASK, REQUEST, 1, List.of(
                Map.of("участник", INSURED, "родственник", "нет", "доплата", "0.00"),
                Map.of("участник", SPOUSE, "родственник", "да", "доплата", "34000.00", "родство", "SPOUSE",
                        "программа", PROGRAM)));
        inbox.everythingProcessed();
        outboxSteps.publicationPass();
    }

    private void accept() throws Exception {
        acceptance.acceptsContractOf(SPOUSE, REQUEST, Map.of(
                "contract_version", "1",
                "accepted_by", INSURED,
                "email", "insured@example.test",
                "accepted_at", "2026-02-10T10:04:00+03:00",
                "channel", "WEB",
                "ip_address", "203.0.113.10",
                "user_agent", "stand"));
    }

    private void cancel() throws Exception {
        tasks.cancellationPublished(CANCEL, REQUEST, 2, Map.of(
                "аннулировано", "2026-02-10T10:05:00+03:00",
                "причина", "CKZ_REJECTED"));
        inbox.everythingProcessed();
    }

    private void markInvoice() {
        jdbc.sql("""
                update contracts
                   set status = 'INVOICE_ISSUED'
                 where insured_participant_id = :participant
                """)
                .param("participant", UUID.fromString(SPOUSE)).update();
    }

    private void uploadCertificate(String attachment) throws IOException, InterruptedException {
        remember(attachment, "/internal/v1/requests/" + REQUEST + "/participants/" + SPOUSE + "/attachments",
                "Тестова_Анна_070у.jpg", "image/jpeg", marked(PHRASE));
    }

    private void uploadConsent(String attachment, String member) throws IOException, InterruptedException {
        remember(attachment, "/internal/v1/policies/" + POLICY + "/family-members/" + member + "/consents",
                "Тестова_Ольга_согласие.png", "image/png", marked(CONSENT_PHRASE));
    }

    private void remember(String attachment, String path, String name, String type, byte[] body)
            throws IOException, InterruptedException {
        AttachmentRequest request = new AttachmentRequest(path,
                Optional.of(new AttachmentRequest.AttachmentPart(attachment, null)),
                Optional.of(new AttachmentRequest.FilePart(name, type, body)), Optional.empty(), true);
        uploads.put(attachment, request);
        calls.send(request);
        assertThat(calls.last().status()).as(calls.last().body()).isEqualTo(200);
    }

    private static byte[] marked(String phrase) {
        byte[] text = phrase.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[text.length + 1];
        System.arraycopy(text, 0, body, 0, text.length);
        body[text.length] = MARKER;
        return body;
    }

    private static Optional<String> requestId(String header) {
        if ("нет".equals(header)) {
            return Optional.empty();
        }
        if (AttachmentUploadSteps.INVALID_REQUEST_IDS.containsKey(header)) {
            return Optional.of(AttachmentUploadSteps.invalidRequestId(header));
        }
        return Optional.of(header);
    }

    private static String deletePath(String attachment) {
        return "/internal/v1/attachments/" + attachment;
    }

    private String situationId(String situation) {
        return switch (situation) {
            case "уже удалено" -> "a6000001-5a7b-4d21-9f6e-1b0a7c4d8e21";
            case "неизвестное вложение" -> "a6000099-5a7b-4d21-9f6e-1b0a7c4d8e99";
            case "печатная форма" -> jdbc.sql("""
                    select id::text from contract_documents
                     where doc_type = 'CONTRACT' and attachment_id is null
                    """).query(String.class).single();
            default -> throw new AssertionError("случая «" + situation + "» нет в перечне шага");
        };
    }

    private String stamp(String attachment) {
        return jdbc.sql("select deleted_at from contract_documents where attachment_id = :id")
                .param("id", UUID.fromString(attachment)).query(OffsetDateTime.class).single().toInstant().toString();
    }

    private String requestStamp(String attachment) {
        return jdbc.sql("select deleted_request_id from contract_documents where attachment_id = :id")
                .param("id", UUID.fromString(attachment)).query(String.class).single();
    }

    private int count(String attachment) {
        return jdbc.sql("select count(*) from contract_documents where attachment_id = :id")
                .param("id", UUID.fromString(attachment)).query(Integer.class).single();
    }

    private void consentStays(String context) {
        String attachment = switch (context) {
            case "согласие" -> "b2000001-5a7b-4d21-9f6e-1b0a7c4d8e21";
            case "согласие при переданном договоре" -> "b2000002-5a7b-4d21-9f6e-1b0a7c4d8e21";
            case "согласие при выпущенном полисе" -> "b2000003-5a7b-4d21-9f6e-1b0a7c4d8e21";
            default -> throw new AssertionError("контекста «" + context + "» нет в перечне шага");
        };
        assertThat(certificates.byAttachment(attachment).docType()).isEqualTo("PD_CONSENT");
        assertThat(stamp(attachment)).isNotBlank();
        assertThat(requestStamp(attachment)).isNotBlank();
        if ("согласие".equals(context)) {
            assertThat(contractCount()).isZero();
            return;
        }
        assertThat(spouseStatus()).isEqualTo("согласие при переданном договоре".equals(context) ? "SUBMITTED" : "ISSUED");
    }

    private void submittedWithout(OutboxRecords.Entry entry, String attachment) {
        assertThat(entry.payload()).as("тело передачи").isNotNull().doesNotContain(attachment);
        JsonNode documents = JSON.readTree(entry.payload()).path("data").path("documents");
        assertThat(documents.isArray()).as("documents передачи").isTrue();
        assertThat(documents.isEmpty()).isTrue();
    }

    private int contractCount() {
        return jdbc.sql("select count(*) from contracts where individual_request_id = :request")
                .param("request", UUID.fromString(REQUEST)).query(Integer.class).single();
    }

    private String spouseStatus() {
        return jdbc.sql("select status::text from contracts where insured_participant_id = :participant")
                .param("participant", UUID.fromString(SPOUSE)).query(String.class).single();
    }

    private String printedFormUrl() {
        return jdbc.sql("""
                select usk_file_url from contract_documents
                 where doc_type = 'CONTRACT' and attachment_id is null
                """).query(String.class).single();
    }

    private static String deletedAttachment(String context) {
        return switch (context) {
            case "справка до первой задачи" -> "b2000004-5a7b-4d21-9f6e-1b0a7c4d8e21";
            case "справка при договоре DRAFT_READY" -> "b2000005-5a7b-4d21-9f6e-1b0a7c4d8e21";
            case "справка при договоре ACCEPTED" -> "b2000006-5a7b-4d21-9f6e-1b0a7c4d8e21";
            case "справка при договоре INVOICE_ISSUED" -> "b2000007-5a7b-4d21-9f6e-1b0a7c4d8e21";
            case "справка при договоре PAID" -> "b2000008-5a7b-4d21-9f6e-1b0a7c4d8e21";
            default -> throw new AssertionError("контекста «" + context + "» нет в перечне шага");
        };
    }
}
