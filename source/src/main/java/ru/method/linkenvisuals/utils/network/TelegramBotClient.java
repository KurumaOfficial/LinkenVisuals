package ru.method.linkenvisuals.utils.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class TelegramBotClient {

    public static final long MAX_BOT_UPLOAD_BYTES = 50L * 1024L * 1024L;

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final URI DEFAULT_API_BASE = URI.create("https://api.telegram.org/");

    private final HttpClient client;
    private final URI apiBase;

    public TelegramBotClient(HttpClient client) {
        this(client, DEFAULT_API_BASE);
    }

    public TelegramBotClient(HttpClient client, URI apiBase) {
        this.client = Objects.requireNonNull(client, "client");
        this.apiBase = Objects.requireNonNull(apiBase, "apiBase");
    }

    /** Немедленно гасит внутренний HttpClient (его selector-поток — не-daemon, держит JVM живой). */
    public void close() {
        try {
            client.shutdownNow();
        } catch (Throwable ignored) {
        }
    }

    public SendResult sendDocument(String botToken, String chatId, Path document, String caption, Duration timeout) {
        String token = safeTrim(botToken);
        String chat = safeTrim(chatId);
        if (token.isEmpty()) return SendResult.error("empty_bot_token");
        if (chat.isEmpty()) return SendResult.error("empty_chat_id");
        if (document == null || !Files.isRegularFile(document)) return SendResult.error("document_not_found");

        try {
            long size = Files.size(document);
            if (size > MAX_BOT_UPLOAD_BYTES) {
                return SendResult.error("file_too_large");
            }

            String boundary = "----LVBoundary" + UUID.randomUUID().toString().replace("-", "");
            String filename = sanitizeFileName(document.getFileName().toString());
            String contentType = Files.probeContentType(document);
            if (contentType == null || contentType.isBlank()) {
                contentType = "text/plain";
            }

            byte[] body = buildMultipartBody(
                    boundary,
                    chat,
                    safeTrim(caption),
                    filename,
                    contentType,
                    Files.readAllBytes(document)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(resolveBotMethod(token, "sendDocument"))
                    .timeout(timeout == null ? Duration.ofSeconds(10) : timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return fromTelegramResponse(response);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return SendResult.error("interrupted");
        } catch (Exception exception) {
            return SendResult.error(exception.getClass().getSimpleName() + ":" + exception.getMessage());
        }
    }

    private SendResult fromTelegramResponse(HttpResponse<String> response) {
        if (response == null) return SendResult.error("no_response");
        String body = response.body();
        int status = response.statusCode();
        if (status != 200) {
            return new SendResult(false, status, body, "http_" + status);
        }
        if (!isOkResponse(body)) {
            return new SendResult(false, status, body, "telegram_not_ok");
        }
        return new SendResult(true, status, body, "");
    }

    private boolean isOkResponse(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return root.has("ok") && root.get("ok").getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private URI resolveBotMethod(String token, String method) {
        String cleanMethod = method == null ? "" : method.trim();
        return apiBase.resolve("bot" + token + "/" + cleanMethod);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sanitizeFileName(String filename) {
        if (filename == null || filename.isBlank()) return "crash-report.txt";
        return filename.replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    static byte[] buildMultipartBody(
            String boundary,
            String chatId,
            String caption,
            String filename,
            String contentType,
            byte[] payload
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeTextPart(out, boundary, "chat_id", chatId);
        if (!caption.isBlank()) {
            writeTextPart(out, boundary, "caption", caption);
        }

        out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
        out.write(("Content-Disposition: form-data; name=\"document\"; filename=\"" + filename + "\"").getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
        out.write(("Content-Type: " + contentType).getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
        out.write(CRLF);
        out.write(payload);
        out.write(CRLF);

        out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
        return out.toByteArray();
    }

    private static void writeTextPart(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"").getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
        out.write(CRLF);
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
    }

    public record SendResult(boolean success, int statusCode, String body, String error) {
        public static SendResult error(String reason) {
            return new SendResult(false, -1, "", reason == null ? "unknown" : reason);
        }
    }
}
