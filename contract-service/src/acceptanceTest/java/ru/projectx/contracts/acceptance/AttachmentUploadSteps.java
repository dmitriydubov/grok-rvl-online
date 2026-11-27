package ru.projectx.contracts.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Когда;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

public class AttachmentUploadSteps {

    private static final String ATTACHMENT = "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21";
    private static final String FILE_NAME = "Тестова_Анна_070у.jpg";
    private static final String CONTENT = "СТЕНД: вымышленная справка 070/у, участник 1";
    private static final String JPEG = "image/jpeg";
    private static final String NOT_A_GUID = "not-a-guid";
    private static final byte[] FILLER = "070u".getBytes(StandardCharsets.US_ASCII);
    private static final int OVER_LIMIT = 5_242_881;

    static final Map<String, UnaryOperator<AttachmentRequest>> DEFECTS = Map.ofEntries(
            Map.entry("тип файла image/tiff", request -> withFile(request, "image/tiff", content(CONTENT))),
            Map.entry("тип файла image/gif", request -> withFile(request, "image/gif", content(CONTENT))),
            Map.entry("тип файла не указан", request -> withFile(request, null, content(CONTENT))),
            Map.entry("пустой файл", request -> withFile(request, JPEG, new byte[0])),
            Map.entry("файл на байт больше предела", request -> withFile(request, JPEG, filler(OVER_LIMIT))),
            Map.entry("идентификатор заявки в пути — не GUID", request -> identified(request, NOT_A_GUID,
                    request.participant())),
            Map.entry("идентификатор участника в пути — не GUID", request -> identified(request, request.request(),
                    NOT_A_GUID)),
            Map.entry("идентификатор полиса в пути — не GUID", request -> identified(request, NOT_A_GUID,
                    request.participant())),
            Map.entry("идентификатор члена семьи в пути — не GUID", request -> identified(request, request.request(),
                    NOT_A_GUID)),
            Map.entry("нет части attachment_id", request -> parts(request, Optional.empty(), request.filePart(), true)),
            Map.entry("attachment_id — не GUID", request -> parts(request, Optional.of(NOT_A_GUID), request.filePart(),
                    true)),
            Map.entry("нет части file", request -> parts(request, request.attachmentPart(), Optional.empty(), true)),
            Map.entry("тело не multipart", request -> parts(request, request.attachmentPart(), Optional.empty(),
                    false)),
            Map.entry("у части attachment_id есть filename", request -> request.withAttachmentFileName("id.txt")),
            Map.entry("у части attachment_id пустой filename", request -> request.withAttachmentFileName("")),
            Map.entry("у части file нет параметра filename", AttachmentRequest::withoutFileName),
            Map.entry("у части file пустой filename", request -> request.withFileName("")),
            Map.entry("у части file filename из пробелов", request -> request.withFileName("   ")));

    private static final Map<String, String> INVALID_REQUEST_IDS = Map.of(
            "длиной 65 символов", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
            "с символом /", "req/01",
            "пустой", "");

    private final AttachmentCalls calls;

    public AttachmentUploadSteps(AttachmentCalls calls) {
        this.calls = calls;
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string}:")
    public void upload(String participant, String request, DataTable table) throws IOException, InterruptedException {
        calls.send(request(request, participant, table.asMap()));
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string} с дефектом {string}")
    public void uploadWithDefect(String participant, String request, String defect)
            throws IOException, InterruptedException {
        UnaryOperator<AttachmentRequest> spoil = Optional.ofNullable(DEFECTS.get(defect))
                .orElseThrow(() -> new AssertionError("дефекта «" + defect + "» нет в перечне шага"));
        calls.send(spoil.apply(standard(request, participant)));
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string} с заголовком X-Request-Id {string}:")
    public void uploadWithRequestId(String participant, String request, String requestId, DataTable table)
            throws IOException, InterruptedException {
        calls.send(request(request, participant, table.asMap()).withRequestId(requestId));
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string} с недопустимым заголовком X-Request-Id {string}:")
    public void uploadWithInvalidRequestId(String participant, String request, String kind, DataTable table)
            throws IOException, InterruptedException {
        String requestId = Optional.ofNullable(INVALID_REQUEST_IDS.get(kind))
                .orElseThrow(() -> new AssertionError("недопустимого заголовка «" + kind + "» нет в перечне шага"));
        calls.send(request(request, participant, table.asMap()).withRequestId(requestId));
    }

    static AttachmentRequest request(String request, String participant, Map<String, String> fields) {
        byte[] content = Optional.ofNullable(fields.get("содержимое")).map(AttachmentUploadSteps::content)
                .orElseGet(() -> filler(Integer.parseInt(required(fields, "размер"))));
        return AttachmentRequest.certificate(request, participant, Optional.of(required(fields, "вложение")),
                Optional.of(new AttachmentRequest.FilePart(required(fields, "имя файла"), required(fields, "тип"),
                        content)), Optional.empty(), true);
    }

    private static AttachmentRequest standard(String request, String participant) {
        return AttachmentRequest.certificate(request, participant, Optional.of(ATTACHMENT),
                Optional.of(new AttachmentRequest.FilePart(FILE_NAME, JPEG, content(CONTENT))), Optional.empty(), true);
    }

    private static AttachmentRequest withFile(AttachmentRequest request, @Nullable String contentType,
            byte[] content) {
        String name = request.filePart().map(AttachmentRequest.FilePart::fileName).orElse(FILE_NAME);
        return new AttachmentRequest(request.request(), request.participant(), request.attachmentPart(),
                Optional.of(new AttachmentRequest.FilePart(name, contentType, content)), Optional.empty(), true,
                Optional.empty(), false, request.consent());
    }

    private static AttachmentRequest identified(AttachmentRequest request, String owner, String subject) {
        return new AttachmentRequest(owner, subject, request.attachmentPart(), request.filePart(), Optional.empty(),
                true, request.attachmentFileName(), request.omitFileName(), request.consent());
    }

    private static AttachmentRequest parts(AttachmentRequest request, Optional<String> attachment,
            Optional<AttachmentRequest.FilePart> file, boolean multipart) {
        return new AttachmentRequest(request.request(), request.participant(), attachment, file, Optional.empty(),
                multipart, Optional.empty(), false, request.consent());
    }

    private static String required(Map<String, String> fields, String name) {
        return Optional.ofNullable(fields.get(name))
                .orElseThrow(() -> new AssertionError("в таблице загрузки нет строки «" + name + "»"));
    }

    private static byte[] content(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] filler(int size) {
        byte[] content = new byte[size];
        for (int index = 0; index < size; index++) {
            content[index] = FILLER[index % FILLER.length];
        }
        return content;
    }
}
