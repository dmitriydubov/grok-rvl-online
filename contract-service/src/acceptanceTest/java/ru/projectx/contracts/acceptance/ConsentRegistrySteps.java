package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Тогда;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class ConsentRegistrySteps {

    private static final String KEY_COLUMN = "ключ объекта";
    private static final String CREATED_COLUMN = "создана";
    private static final Map<String, Function<CertificateRecords.Certificate, String>> COLUMNS = Map.of(
            "вложение", certificate -> certificate.attachment().toString(),
            "вид", CertificateRecords.Certificate::docType,
            "content_type", CertificateRecords.Certificate::contentType,
            "размер", certificate -> Long.toString(certificate.size()),
            "file_hash", CertificateRecords.Certificate::hash);

    private final CertificateRecords records;

    public ConsentRegistrySteps(CertificateRecords records) {
        this.records = records;
    }

    @Тогда("согласия члена семьи {string} полиса {string} в реестре:")
    public void consents(String member, String policy, DataTable expected) {
        List<CertificateRecords.Certificate> stored = records.ofConsent(UUID.fromString(policy), UUID.fromString(member));
        List<Map<String, String>> rows = expected.asMaps();
        assertThat(stored).as("согласия члена семьи %s", member).hasSize(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            row(stored.get(index), rows.get(index));
        }
    }

    @Тогда("в реестре нет согласий члена семьи {string} полиса {string}")
    public void noConsents(String member, String policy) {
        assertThat(records.ofConsent(UUID.fromString(policy), UUID.fromString(member)))
                .as("согласия члена семьи %s", member).isEmpty();
    }

    private static void row(CertificateRecords.Certificate certificate, Map<String, String> expected) {
        for (Map.Entry<String, String> cell : expected.entrySet()) {
            cell(certificate, cell.getKey(), cell.getValue());
        }
    }

    private static void cell(CertificateRecords.Certificate certificate, String column, String expected) {
        if (KEY_COLUMN.equals(column)) {
            assertThat(certificate.key()).as("ключ объекта").matches(CertificateRegistrySteps.keyPattern(expected));
            return;
        }
        if (CREATED_COLUMN.equals(column)) {
            assertThat(certificate.createdAt()).isEqualTo(OffsetDateTime.parse(expected).toInstant());
            return;
        }
        Function<CertificateRecords.Certificate, String> value = Optional.ofNullable(COLUMNS.get(column))
                .orElseThrow(() -> new AssertionError("столбца «" + column + "» нет в словаре шага"));
        assertThat(value.apply(certificate)).as("столбец %s", column).isEqualTo(expected);
    }
}
