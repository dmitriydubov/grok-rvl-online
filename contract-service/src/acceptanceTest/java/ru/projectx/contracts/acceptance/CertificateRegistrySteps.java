package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

public class CertificateRegistrySteps {

    private static final String ANY_UUID = "{uuid}";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String KEY_COLUMN = "ключ объекта";
    private static final String CREATED_COLUMN = "создана";
    private static final Map<String, Function<CertificateRecords.Certificate, String>> COLUMNS = Map.of(
            "вложение", certificate -> certificate.attachment().toString(),
            "doc_type", CertificateRecords.Certificate::docType,
            "content_type", CertificateRecords.Certificate::contentType,
            "размер", certificate -> Long.toString(certificate.size()),
            "file_hash", CertificateRecords.Certificate::hash);

    private final CertificateRecords records;
    private final StandClock clock;

    public CertificateRegistrySteps(CertificateRecords records, StandClock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Дано("вложение {string} в реестре документов помечено удалённым")
    public void markDeleted(String attachment) {
        records.markDeleted(attachment, clock.instant(), "stand-delete-request");
    }

    @Тогда("справки участника {string} заявки {string} в реестре документов:")
    public void certificates(String participant, String request, DataTable expected) {
        assertRecords("справки участника " + participant,
                records.of(UUID.fromString(request), UUID.fromString(participant)), expected);
    }

    @Тогда("в реестре документов нет справок участника {string} заявки {string}")
    public void noCertificates(String participant, String request) {
        assertThat(records.of(UUID.fromString(request), UUID.fromString(participant)))
                .as("справки участника %s", participant).isEmpty();
    }

    @Тогда("согласия родственника {string} по полису {string} в реестре документов:")
    public void consents(String familyMember, String policy, DataTable expected) {
        assertRecords("согласия родственника " + familyMember,
                records.ofConsent(UUID.fromString(policy), UUID.fromString(familyMember)), expected);
    }

    @Тогда("в реестре документов нет согласий родственника {string} по полису {string}")
    public void noConsents(String familyMember, String policy) {
        assertThat(records.ofConsent(UUID.fromString(policy), UUID.fromString(familyMember)))
                .as("согласия родственника %s", familyMember).isEmpty();
    }

    private static void assertRecords(String description, List<CertificateRecords.Certificate> stored,
            DataTable expected) {
        List<Map<String, String>> rows = expected.asMaps();
        assertThat(stored).as(description).hasSize(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            matches(stored.get(index), rows.get(index));
        }
    }

    static String keyPattern(String expected) {
        return Pattern.quote(expected).replace(ANY_UUID, "\\E" + UUID_PATTERN + "\\Q");
    }

    private static void matches(CertificateRecords.Certificate certificate, Map<String, String> row) {
        for (Map.Entry<String, String> cell : row.entrySet()) {
            matches(certificate, cell.getKey(), cell.getValue());
        }
    }

    private static void matches(CertificateRecords.Certificate certificate, String column, String expected) {
        if (KEY_COLUMN.equals(column)) {
            assertThat(certificate.key()).as("ключ объекта").matches(keyPattern(expected));
            return;
        }
        if (CREATED_COLUMN.equals(column)) {
            assertThat(certificate.createdAt()).as("момент записи").isEqualTo(OffsetDateTime.parse(expected).toInstant());
            return;
        }
        Function<CertificateRecords.Certificate, String> value = Optional.ofNullable(COLUMNS.get(column))
                .orElseThrow(() -> new AssertionError("столбца «" + column + "» нет в словаре шага"));
        assertThat(value.apply(certificate)).as("столбец %s", column).isEqualTo(expected);
    }
}
