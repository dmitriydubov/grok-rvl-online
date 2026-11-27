package ru.projectx.contracts.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Когда;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class ConsentUploadSteps {

    private static final String ATTACHMENT = "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21";
    private static final String FILE_NAME = "Тестова_Анна_согласие.jpg";
    private static final String CONTENT = "СТЕНД: вымышленное согласие на обработку, член 1";
    private static final String JPEG = "image/jpeg";

    private final AttachmentCalls calls;

    public ConsentUploadSteps(AttachmentCalls calls) {
        this.calls = calls;
    }

    @Когда("сервис Заявок загружает согласие члена семьи {string} полиса {string}:")
    public void upload(String member, String policy, DataTable table) throws IOException, InterruptedException {
        calls.send(AttachmentUploadSteps.request(policy, member, table.asMap()).asConsent());
    }

    @Когда("сервис Заявок загружает согласие члена семьи {string} полиса {string} с дефектом {string}")
    public void uploadWithDefect(String member, String policy, String defect) throws IOException, InterruptedException {
        UnaryOperator<AttachmentRequest> spoil = Optional.ofNullable(AttachmentUploadSteps.DEFECTS.get(defect))
                .orElseThrow(() -> new AssertionError("дефекта «" + defect + "» нет в перечне шага"));
        calls.send(spoil.apply(standard(policy, member)));
    }

    private static AttachmentRequest standard(String policy, String member) {
        return AttachmentRequest.certificate(policy, member, Optional.of(ATTACHMENT),
                Optional.of(new AttachmentRequest.FilePart(FILE_NAME, JPEG, CONTENT.getBytes(StandardCharsets.UTF_8))),
                Optional.empty(), true).asConsent();
    }
}
