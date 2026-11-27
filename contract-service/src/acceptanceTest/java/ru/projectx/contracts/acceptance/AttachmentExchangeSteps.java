package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import io.minio.errors.ErrorResponseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.contracts.config.storage.StorageProperties;

public class AttachmentExchangeSteps {

    private static final String REQUEST = "61a7982c-a0b4-4bbf-9527-60b40b752803";
    private static final String INSURED = "ae8312c3-350b-4d0b-806c-5e2460e74dbc";
    private static final String SPOUSE = "9275471c-d2fe-484f-97a6-d6d7b1267b5e";
    private static final String POLICY = "a23ba061-f742-4b20-8b95-36a0e525c087";
    private static final String RELATIVE = "c45dc283-1964-4d42-8d17-58c2a747e209";
    private static final String PROGRAM = "2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a";
    private static final String TASK = "b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11";
    private static final String CANCEL = "c3f80a67-9d12-4b58-8e44-0a7b1f25e903";
    private static final String REGISTRATION = "0c6a9f31-58b2-4e77-91d0-3a7e5b1c22f9";
    private static final String ABSENT = "нет";
    private static final String NEW_UUID = "новый UUID";
    private static final byte MARKER = (byte) 0xFF;

    private final AttachmentCalls calls;
    private final TaskPublishingSteps tasks;
    private final InboxSteps inbox;
    private final OutboxSteps outboxSteps;
    private final AcceptanceSteps acceptance;
    private final KmContractPaymentSteps payments;
    private final KmContractSubmissionSteps submission;
    private final OneCPublishingSteps oneC;
    private final CertificateStorageSteps storageSteps;
    private final CertificateContentSteps contents;
    private final CertificateRegistrySteps registry;
    private final StandStorage storage;
    private final StorageProperties settings;
    private final CertificateRecords certificates;
    private final ServiceJournal journal;
    private final OutboxRecords outbox;
    private final JdbcClient jdbc;
    private final URI metrics;
    private String printedFormId;

    public AttachmentExchangeSteps(AttachmentCalls calls, TaskPublishingSteps tasks, InboxSteps inbox,
            OutboxSteps outboxSteps, AcceptanceSteps acceptance, KmContractPaymentSteps payments,
            KmContractSubmissionSteps submission, OneCPublishingSteps oneC, CertificateStorageSteps storageSteps,
            CertificateContentSteps contents, CertificateRegistrySteps registry, StandStorage storage,
            StorageProperties settings, CertificateRecords certificates, ServiceJournal journal, OutboxRecords outbox,
            JdbcClient jdbc, @LocalManagementPort int managementPort) {
        this.calls = calls;
        this.tasks = tasks;
        this.inbox = inbox;
        this.outboxSteps = outboxSteps;
        this.acceptance = acceptance;
        this.payments = payments;
        this.submission = submission;
        this.oneC = oneC;
        this.storageSteps = storageSteps;
        this.contents = contents;
        this.registry = registry;
        this.storage = storage;
        this.settings = settings;
        this.certificates = certificates;
        this.journal = journal;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.metrics = URI.create("http://localhost:" + managementPort + "/actuator/prometheus");
    }

    @Дано("стенд подготовил вложение для выдачи содержимого: контекст {string}, тип {string}")
    public void contentReady(String context, String type) throws Exception {
        switch (context) {
            case "справка до первой задачи" -> uploadCertificate("a1000001-5a7b-4d21-9f6e-1b0a7c4d8e21", type,
                    "СТЕНД: вымышленная справка 070/у, до задачи", SPOUSE);
            case "справка после прекращения заявки" -> {
                issueTask();
                uploadCertificate("a1000002-5a7b-4d21-9f6e-1b0a7c4d8e21", type,
                        "СТЕНД: вымышленная справка 070/у, после прекращения", SPOUSE);
                cancelRequest();
            }
            case "справка при переданном договоре" -> {
                issueTask();
                uploadCertificate("a1000003-5a7b-4d21-9f6e-1b0a7c4d8e21", type,
                        "СТЕНД: вымышленная справка 070/у, передан в 1С", SPOUSE);
                submitContract();
            }
            case "справка при выпущенном полисе" -> {
                issueTask();
                uploadCertificate("a1000004-5a7b-4d21-9f6e-1b0a7c4d8e21", type,
                        "СТЕНД: вымышленная справка 070/у, полис выпущен", SPOUSE);
                submitContract();
                registerContract();
            }
            case "согласие" -> uploadConsent("a1000005-5a7b-4d21-9f6e-1b0a7c4d8e21", type,
                    "СТЕНД: вымышленное согласие на обработку ПДн, родственник", RELATIVE);
            default -> throw new AssertionError("контекста «" + context + "» нет в перечне шага");
        }
        assertThat(calls.last().status()).as("подготовка вложения, тело %s", calls.last().body()).isEqualTo(200);
    }

    @Когда("сервис Заявок запрашивает содержимое вложения {string} с заголовком X-Request-Id {string}")
    public void readWithRequestId(String attachment, String header) throws IOException, InterruptedException {
        calls.read("GET", contentPath(attachment), requestId(header));
    }

    @Когда("сервис Заявок запрашивает содержимое вложения {string}")
    public void read(String attachment) throws IOException, InterruptedException {
        calls.read("GET", contentPath(attachment), Optional.empty());
    }

    @Когда("сервис Заявок запрашивает содержимое отсутствующего вложения случая {string} с заголовком X-Request-Id {string}")
    public void readMissing(String situation, String header) throws IOException, InterruptedException {
        calls.read("GET", contentPath(missingId(situation)), requestId(header));
    }

    private String missingId(String situation) {
        return switch (situation) {
            case "неизвестное вложение" -> "f6000099-5a7b-4d21-9f6e-1b0a7c4d8e99";
            case "печатная форма" -> printedFormId;
            case "мягко удалённое вложение" -> "f6000001-5a7b-4d21-9f6e-1b0a7c4d8e21";
            default -> throw new AssertionError("случая «" + situation + "» нет в перечне шага");
        };
    }

    @Тогда("^Договоры отдали содержимое вложения \"([^\"]+)\" байтами загруженного файла: код (\\d+), Content-Type \"([^\"]+)\", Content-Length (\\d+), ETag (?:\"\"([0-9a-f]+)\"\"|\"\\\\\"([0-9a-f]+)\\\\\"\"), хеш \"([0-9a-f]+)\"$")
    public void streamed(String attachment, int status, String type, int size, String doubled, String escaped, String hash) {
        String etag = "\"" + (doubled == null ? escaped : doubled) + "\"";
        AttachmentCalls.BinaryAnswer answer = calls.lastBinary();
        assertThat(answer.status()).isEqualTo(status);
        assertThat(answer.body()).isEqualTo(calls.acceptedContent(attachment));
        assertThat(answer.contentType().orElseThrow()).isEqualTo(type);
        assertThat(answer.contentLength()).contains((long) size);
        assertThat(answer.etag()).contains(etag);
        assertThat(etag).isEqualTo("\"" + hash + "\"");
        CertificateRecords.Certificate record = certificates.byAttachment(attachment);
        assertThat(record.hash()).isEqualTo(hash);
        assertThat(record.size()).isEqualTo(size);
    }

    @Тогда("в реестре документов вложение {string}: doc_type {string}, content_type {string}, размер {int}, file_hash {string}")
    public void registryRow(String attachment, String docType, String contentType, int size, String hash) {
        CertificateRecords.Certificate record = certificates.byAttachment(attachment);
        assertThat(record.docType()).isEqualTo(docType);
        assertThat(record.contentType()).isEqualTo(contentType);
        assertThat(record.size()).isEqualTo(size);
        assertThat(record.hash()).isEqualTo(hash);
    }

    @Тогда("у вложения {string} в реестре документов нет момента удаления")
    public void notDeleted(String attachment) {
        assertThat(deletedAt(attachment)).isNull();
    }

    @Тогда("ожидаемый заголовок X-Request-Id ответа — {string}")
    public void expectedRequestId(String expected) {
        if (NEW_UUID.equals(expected)) {
            String issued = calls.last().requestId().orElseThrow();
            assertThat(UUID.fromString(issued)).hasToString(issued);
            assertThat(calls.last().sentRequestId()).isNotEqualTo(Optional.of(issued));
            return;
        }
        assertThat(calls.last().requestId()).contains(expected);
    }

    @Тогда("имя файла {string} и текстовая фраза вложения {string} не попали ни в журнал Договоров, ни в метрики")
    public void phraseStaysOut(String fileName, String attachment) throws IOException, InterruptedException {
        assertThat(calls.sentFileName(attachment)).isEqualTo(fileName);
        byte[] sent = calls.sentContent(attachment);
        assertThat(sent[sent.length - 1]).isEqualTo(MARKER);
        String phrase = new String(sent, 0, sent.length - 1, StandardCharsets.UTF_8);
        String exposition = metrics();
        for (String value : List.of(fileName, phrase)) {
            assertThat(journal.mentioning(value)).as("журнал содержит «%s»", value).isEmpty();
            assertThat(exposition).as("метрики содержат «%s»", value).doesNotContain(value);
        }
    }

    @Дано("стенд подготовил обращение за содержимым, которого нет: {string}, хранилище {string}")
    public void missingReady(String situation, String storageState) throws Exception {
        switch (situation) {
            case "неизвестное вложение" -> {
            }
            case "печатная форма" -> printedForm();
            case "мягко удалённое вложение" -> {
                uploadCertificate("f6000001-5a7b-4d21-9f6e-1b0a7c4d8e21", "image/jpeg",
                        "СТЕНД: вымышленная справка 070/у, участник 1", SPOUSE);
                registry.markDeleted("f6000001-5a7b-4d21-9f6e-1b0a7c4d8e21");
            }
            default -> throw new AssertionError("случая «" + situation + "» нет в перечне шага");
        }
        if ("остановлено".equals(storageState)) {
            storageSteps.storageDown(storageState);
        } else if (!"доступно".equals(storageState)) {
            throw new AssertionError("хранилища «" + storageState + "» нет в перечне шага");
        }
    }

    @Тогда("след случая {string} не испорчен")
    public void missingUntouched(String situation) {
        switch (situation) {
            case "неизвестное вложение" -> assertThat(countAttachment("f6000099-5a7b-4d21-9f6e-1b0a7c4d8e99")).isZero();
            case "печатная форма" -> {
                assertThat(deletedAtOfDocument()).isNull();
                assertThat(printedFormUrl()).isEqualTo("https://1c-usk.example/rvl/km/DKM-2026-000001.pdf");
            }
            case "мягко удалённое вложение" -> assertThat(deletedAt("f6000001-5a7b-4d21-9f6e-1b0a7c4d8e21")).isNotNull();
            default -> throw new AssertionError("случая «" + situation + "» нет в перечне шага");
        }
    }

    @Тогда("в журнале Договоров есть запись уровня WARN об отсутствующем объекте вложения {string} без ключа объекта, без имени файла {string} и без фразы {string}")
    public void missingObjectWarned(String attachment, String fileName, String phrase) {
        assertThat(calls.sentFileName(attachment)).isEqualTo(fileName);
        assertThat(new String(calls.sentContent(attachment), StandardCharsets.UTF_8)).isEqualTo(phrase);
        String expected = "объект вложения " + attachment + " в хранилище справок не найден";
        String key = certificates.byAttachment(attachment).key();
        assertThat(journal.events()).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(event -> event.getFormattedMessage())
                .filteredOn(expected::equals)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message).doesNotContain(key).doesNotContain(fileName).doesNotContain(phrase);
                });
    }

    @Дано("хранилище справок для чтения недоступно: {string}")
    public void readStorageDown(String outage) throws Exception {
        disturb(outage);
    }

    @Когда("хранилище справок для чтения снова доступно")
    public void readStorageBack() throws Exception {
        restoreUploadedObjects();
        storageSteps.storageBack();
    }

    private void disturb(String outage) throws Exception {
        if ("бакета нет".equals(outage)) {
            storageSteps.bucketRemoved();
            return;
        }
        storageSteps.storageDown(outage);
    }

    private void restoreUploadedObjects() throws Exception {
        if (storage.disrupted()) {
            storage.recover();
        }
        if (storage.bucketExists(settings.bucket())) {
            return;
        }
        storage.recreateBucket(settings.bucket());
        List<String[]> rows = jdbc.sql(
                "select attachment_id::text as attachment, file_key from contract_documents where file_key is not null")
                .query((row, index) -> new String[] {row.getString("attachment"), row.getString("file_key")}).list();
        for (String[] row : rows) {
            storage.putObject(settings.bucket(), row[1], calls.acceptedContent(row[0]));
        }
    }

    private void issueTask() throws Exception {
        tasks.taskPublished(TASK, REQUEST, 1, List.of(
                Map.of("участник", INSURED, "родственник", "нет", "доплата", "0.00"),
                Map.of("участник", SPOUSE, "родственник", "да", "доплата", "34000.00", "родство", "SPOUSE",
                        "программа", PROGRAM)));
        inbox.everythingProcessed();
        outboxSteps.publicationPass();
    }

    private void cancelRequest() throws Exception {
        tasks.cancellationPublished(CANCEL, REQUEST, 2, Map.of(
                "аннулировано", "2026-02-10T10:05:00+03:00",
                "причина", "CKZ_REJECTED"));
        inbox.everythingProcessed();
    }

    private void submitContract() throws Exception {
        acceptance.acceptsContractOf(SPOUSE, REQUEST, Map.of(
                "contract_version", "1",
                "accepted_by", INSURED,
                "email", "insured@example.test",
                "accepted_at", "2026-02-10T10:04:00+03:00",
                "channel", "WEB",
                "ip_address", "203.0.113.10",
                "user_agent", "stand"));
        payments.contractPaid(SPOUSE, REQUEST, "2026-02-10T07:20:15Z");
        submission.preparationPass();
        outboxSteps.publicationPass();
    }

    private void registerContract() throws Exception {
        oneC.registrationPublished(REGISTRATION, SPOUSE, REQUEST, Map.of(
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
        printedFormId = jdbc.sql("""
                select id::text from contract_documents
                 where doc_type = 'CONTRACT' and attachment_id is null
                """).query(String.class).single();
    }

    private void printedForm() throws Exception {
        issueTask();
        uploadCertificate("b2000010-5a7b-4d21-9f6e-1b0a7c4d8e21", "image/jpeg",
                "СТЕНД: вымышленная справка 070/у, участник 1", SPOUSE);
        submitContract();
        registerContract();
    }

    private void uploadCertificate(String attachment, String type, String phrase, String participant)
            throws IOException, InterruptedException {
        calls.send(request("/internal/v1/requests/" + REQUEST + "/participants/" + participant + "/attachments",
                attachment, fileName(type, false), type, marked(phrase)));
    }

    private void uploadConsent(String attachment, String type, String phrase, String member)
            throws IOException, InterruptedException {
        calls.send(request("/internal/v1/policies/" + POLICY + "/family-members/" + member + "/consents",
                attachment, fileName(type, true), type, marked(phrase)));
    }

    private static AttachmentRequest request(String path, String attachment, String name, String type, byte[] body) {
        return new AttachmentRequest(path, Optional.of(new AttachmentRequest.AttachmentPart(attachment, null)),
                Optional.of(new AttachmentRequest.FilePart(name, type, body)), Optional.empty(), true);
    }

    private static byte[] marked(String phrase) {
        byte[] text = phrase.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[text.length + 1];
        System.arraycopy(text, 0, body, 0, text.length);
        body[text.length] = MARKER;
        return body;
    }

    private static String fileName(String type, boolean consent) {
        String stem = consent ? "Тестова_Ольга_согласие" : "Тестова_Анна_070у";
        return switch (type) {
            case "image/jpeg" -> stem + ".jpg";
            case "image/png" -> stem + ".png";
            case "application/pdf" -> stem + ".pdf";
            default -> throw new AssertionError("типа «" + type + "» нет в перечне шага");
        };
    }

    private static Optional<String> requestId(String header) {
        if (ABSENT.equals(header)) {
            return Optional.empty();
        }
        if (AttachmentUploadSteps.INVALID_REQUEST_IDS.containsKey(header)) {
            return Optional.of(AttachmentUploadSteps.invalidRequestId(header));
        }
        return Optional.of(header);
    }

    private static String contentPath(String attachment) {
        return "/internal/v1/attachments/" + attachment + "/content";
    }

    private String metrics() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(metrics).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private Instant deletedAt(String attachment) {
        return jdbc.sql("select deleted_at from contract_documents where attachment_id = :id")
                .param("id", UUID.fromString(attachment)).query(OffsetDateTime.class).optional()
                .map(OffsetDateTime::toInstant).orElse(null);
    }

    private int countAttachment(String attachment) {
        return jdbc.sql("select count(*) from contract_documents where attachment_id = :id")
                .param("id", UUID.fromString(attachment)).query(Integer.class).single();
    }

    private Instant deletedAtOfDocument() {
        return jdbc.sql("select deleted_at from contract_documents where id = :id")
                .param("id", UUID.fromString(printedFormId)).query(OffsetDateTime.class).optional()
                .map(OffsetDateTime::toInstant).orElse(null);
    }

    private String printedFormUrl() {
        return jdbc.sql("select usk_file_url from contract_documents where id = :id")
                .param("id", UUID.fromString(printedFormId)).query(String.class).single();
    }
}
