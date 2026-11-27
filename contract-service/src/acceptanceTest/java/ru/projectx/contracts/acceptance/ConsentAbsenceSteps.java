package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.GetResponse;
import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;

public class ConsentAbsenceSteps {

    private final AttachmentCalls calls;
    private final CertificateRecords records;
    private final ReceivedStatusMessages statuses;
    private final ReceivedKmContractSubmissions submissions;
    private final ReceivedLmPolicyRequests policies;

    public ConsentAbsenceSteps(AttachmentCalls calls, CertificateRecords records, ReceivedStatusMessages statuses,
            ReceivedKmContractSubmissions submissions, ReceivedLmPolicyRequests policies) {
        this.calls = calls;
        this.records = records;
        this.statuses = statuses;
        this.submissions = submissions;
        this.policies = policies;
    }

    @Тогда("идентификаторы согласия по вложению {string} не попали в ответ о состоянии пакета, "
            + "в сообщение о состоянии выпуска №{int}, в передачу договора КМ №{int} "
            + "и в запрос на выпуск полиса ЛМ №{int}")
    public void absent(String attachment, int status, int submission, int policy) {
        String documentId = records.byAttachment(attachment).id().toString();
        absentFrom(calls.lastPackage().body(), documentId, attachment);
        absentFrom(text(statuses.number(status)), documentId, attachment);
        absentFrom(text(submissions.number(submission)), documentId, attachment);
        absentFrom(text(policies.number(policy)), documentId, attachment);
    }

    private static void absentFrom(String body, String documentId, String attachment) {
        assertThat(body).doesNotContain(documentId).doesNotContain(attachment);
    }

    private static String text(GetResponse message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
