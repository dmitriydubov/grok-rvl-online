package ru.projectx.contracts.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class AttachmentCalls {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String REQUEST_ID = "X-Request-Id";
    private static final int OK = 200;

    record Answer(int status, Optional<String> requestId, Optional<String> sentRequestId, String body) {
    }

    record BinaryAnswer(int status, byte[] body, Optional<String> contentType, Optional<Long> contentLength,
            Optional<String> etag) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Answer> answers = new ArrayList<>();
    private final List<BinaryAnswer> binaries = new ArrayList<>();
    private final Map<String, byte[]> accepted = new HashMap<>();
    private final Map<String, byte[]> sent = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final String base;

    public AttachmentCalls(@LocalServerPort int port) {
        this.base = "http://localhost:" + port;
    }

    void send(AttachmentRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder call = HttpRequest.newBuilder(URI.create(base + request.path())).timeout(TIMEOUT)
                .header("Content-Type", request.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.body()));
        request.requestId().ifPresent(value -> call.header(REQUEST_ID, value));
        HttpResponse<String> response = http.send(call.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        answers.add(new Answer(response.statusCode(), response.headers().firstValue(REQUEST_ID), request.requestId(),
                response.body()));
        remember(request, response.statusCode());
    }

    Answer last() {
        if (answers.isEmpty()) {
            throw new AssertionError("в сценарии ещё не было вызовов загрузки справки");
        }
        return answers.getLast();
    }

    Answer number(int ordinal) {
        if (ordinal < 1 || ordinal > answers.size()) {
            throw new AssertionError("в сценарии нет ответа №" + ordinal + ", всего ответов " + answers.size());
        }
        return answers.get(ordinal - 1);
    }

    JsonNode body(Answer answer) {
        return json.readTree(answer.body());
    }

    byte[] acceptedContent(String attachment) {
        return Optional.ofNullable(accepted.get(attachment))
                .orElseThrow(() -> new AssertionError("вложение " + attachment + " не принималось в этом сценарии"));
    }

    List<byte[]> allSentContents() {
        return List.copyOf(sent.values());
    }

    byte[] sentContent(String attachment) {
        return Optional.ofNullable(sent.get(attachment))
                .orElseThrow(() -> new AssertionError("вложение " + attachment + " не отправлялось в этом сценарии"));
    }

    String sentFileName(String attachment) {
        return Optional.ofNullable(names.get(attachment))
                .orElseThrow(() -> new AssertionError("вложение " + attachment + " не отправлялось в этом сценарии"));
    }

    private void remember(AttachmentRequest request, int status) {
        request.attachmentPart().ifPresent(attachment -> request.filePart().ifPresent(file -> {
            sent.put(attachment.value(), file.content());
            if (file.fileName() != null) {
                names.put(attachment.value(), file.fileName());
            }
            if (status == OK) {
                accepted.put(attachment.value(), file.content());
            }
        }));
    }

    void exchange(String method, String path, Optional<String> requestId, Optional<String> json)
            throws IOException, InterruptedException {
        HttpRequest.Builder call = HttpRequest.newBuilder(URI.create(base + path)).timeout(TIMEOUT)
                .method(method, json.map(body -> HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .orElseGet(HttpRequest.BodyPublishers::noBody));
        json.ifPresent(body -> call.header("Content-Type", "application/json"));
        requestId.ifPresent(value -> call.header(REQUEST_ID, value));
        HttpResponse<String> response = http.send(call.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        answers.add(new Answer(response.statusCode(), response.headers().firstValue(REQUEST_ID), requestId,
                response.body()));
    }

    void read(String method, String path, Optional<String> requestId) throws IOException, InterruptedException {
        HttpRequest.Builder call = HttpRequest.newBuilder(URI.create(base + path)).timeout(TIMEOUT)
                .method(method, HttpRequest.BodyPublishers.noBody());
        requestId.ifPresent(value -> call.header(REQUEST_ID, value));
        HttpResponse<byte[]> response = http.send(call.build(), HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        answers.add(new Answer(response.statusCode(), response.headers().firstValue(REQUEST_ID), requestId,
                new String(body, StandardCharsets.UTF_8)));
        binaries.add(new BinaryAnswer(response.statusCode(), body, response.headers().firstValue("Content-Type"),
                response.headers().firstValue("Content-Length").map(Long::valueOf),
                response.headers().firstValue("ETag")));
    }

    BinaryAnswer lastBinary() {
        if (binaries.isEmpty()) {
            throw new AssertionError("в сценарии ещё не было чтения или удаления вложения");
        }
        return binaries.getLast();
    }
}
