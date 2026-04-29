package io.github.morningwn.example;

import io.github.morningwn.client.ILinkAuthSession;
import io.github.morningwn.client.ILinkBot;
import io.github.morningwn.client.ILinkClient;
import io.github.morningwn.client.ILinkClientConfig;
import io.github.morningwn.exception.ILinkException;
import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.CDNMedia;
import io.github.morningwn.protocol.FileItem;
import io.github.morningwn.protocol.ImageItem;
import io.github.morningwn.protocol.MessageItem;
import io.github.morningwn.protocol.ProtocolValues;
import io.github.morningwn.protocol.QrCodeResponse;
import io.github.morningwn.protocol.VoiceItem;
import io.github.morningwn.protocol.WeixinMessage;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable web quick-start example.
 *
 * <p>Run this class directly to start the local web console:
 * <pre>
 * mvn -q -DskipTests exec:java -Dexec.mainClass=com.github.morningwn.example.WebQuickStartExampleTest -Dexec.classpathScope=test
 * </pre>
 * </p>
 */
public final class WebQuickStartExampleTest {

    private static final String INDEX_HTML_RESOURCE = "/io/github/morningwn/example/web/quick-start-web.html";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final Path SESSION_PATH = Path.of(".ilink-session.txt");
    private static final Path DOWNLOAD_DIR = Path.of(".ilink-web-downloads");
    private static final int DEFAULT_PORT = 8088;

    private WebQuickStartExampleTest() {
    }

    /**
     * Program entry.
     *
     * @param args optional first arg as web port
     * @throws Exception when startup fails
     */
    public static void main(String[] args) throws Exception {
        int port = parsePort(args);
        Files.createDirectories(DOWNLOAD_DIR);

        ILinkClientConfig config = ILinkClientConfig.builder()
                .baseUrl("https://ilinkai.weixin.qq.com")
                .channelVersion("1.0.0")
                .build();

        AtomicReference<ReplyTarget> latestReplyTarget = new AtomicReference<>();
        EventStore eventStore = new EventStore(500);
        WebSessionHandler sessionHandler = new WebSessionHandler(SESSION_PATH, eventStore);

        try (ILinkClient cdnClient = new ILinkClient(config);
             ILinkBot bot = new ILinkBot(config, sessionHandler)) {
            Runtime.getRuntime().addShutdownHook(new Thread(bot::close));

            bot.startAutoPull(message -> onInboundMessage(message, latestReplyTarget, eventStore, cdnClient));

            HttpServer server = createServer(port, bot, sessionHandler, latestReplyTarget, eventStore, DOWNLOAD_DIR);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            server.start();

            System.out.println("Web console started: http://127.0.0.1:" + port);
            System.out.println("Downloads directory: " + DOWNLOAD_DIR.toAbsolutePath());
            System.out.println("Press Ctrl+C to stop.");

            new CountDownLatch(1).await();
        }
    }

    private static HttpServer createServer(
            int port,
            ILinkBot bot,
            WebSessionHandler sessionHandler,
            AtomicReference<ReplyTarget> latestReplyTarget,
            EventStore eventStore,
            Path downloadDir
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        AppContext context = new AppContext(bot, sessionHandler, latestReplyTarget, eventStore, downloadDir);

        server.createContext("/", exchange -> withHandled(exchange, () -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            String body = loadIndexHtml();
            sendText(exchange, 200, body, "text/html; charset=utf-8");
        }));

        server.createContext("/api/state", exchange -> withHandled(exchange, () -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            sendJson(exchange, 200, context.snapshotState());
        }));

        server.createContext("/api/events", exchange -> withHandled(exchange, () -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            long afterId = parseAfterId(exchange.getRequestURI());
            Map<String, Object> payload = Map.of(
                    "items", context.eventStore.listAfter(afterId),
                    "latestId", context.eventStore.latestId()
            );
            sendJson(exchange, 200, payload);
        }));

        server.createContext("/api/send/text", exchange -> withHandled(exchange, () -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }
            SendTextPayload request = readJson(exchange, SendTextPayload.class);
            String text = request == null ? null : request.text();
            if (text == null || text.isBlank()) {
                sendJson(exchange, 400, Map.of("error", "text cannot be empty"));
                return;
            }
            ReplyTarget target = requireReplyTarget(context.latestReplyTarget.get());
            context.bot.sendText(target.toUserId(), target.contextToken(), text);
            context.eventStore.add("outbound", "text", target.toUserId(), text, null, null, null);
            sendJson(exchange, 200, Map.of("ok", true));
        }));

        server.createContext("/api/send/image", exchange -> withHandled(exchange, () -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }
            SendBinaryPayload request = readJson(exchange, SendBinaryPayload.class);
            byte[] bytes = decodeBase64(request == null ? null : request.contentBase64());
            if (bytes.length == 0) {
                sendJson(exchange, 400, Map.of("error", "image content cannot be empty"));
                return;
            }
            ReplyTarget target = requireReplyTarget(context.latestReplyTarget.get());
            context.bot.sendImage(target.toUserId(), target.contextToken(), bytes);
            context.eventStore.add("outbound", "image", target.toUserId(), safeName(request == null ? null : request.name()), null, null, null);
            sendJson(exchange, 200, Map.of("ok", true));
        }));

        server.createContext("/api/send/file", exchange -> withHandled(exchange, () -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "POST");
                return;
            }
            SendBinaryPayload request = readJson(exchange, SendBinaryPayload.class);
            byte[] bytes = decodeBase64(request == null ? null : request.contentBase64());
            if (bytes.length == 0) {
                sendJson(exchange, 400, Map.of("error", "file content cannot be empty"));
                return;
            }
            String fileName = safeName(request == null ? null : request.name());
            ReplyTarget target = requireReplyTarget(context.latestReplyTarget.get());
            context.bot.sendFile(target.toUserId(), target.contextToken(), fileName, bytes);
            context.eventStore.add("outbound", "file", target.toUserId(), fileName, null, null, null);
            sendJson(exchange, 200, Map.of("ok", true));
        }));

        server.createContext("/api/downloads", exchange -> withHandled(exchange, () -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            sendJson(exchange, 200, Map.of("items", listDownloads(context.downloadDir)));
        }));

        server.createContext("/downloads/", exchange -> withHandled(exchange, () -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            serveDownload(exchange, context.downloadDir);
        }));

        server.createContext("/media/", exchange -> withHandled(exchange, () -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }
            serveMedia(exchange, context.downloadDir);
        }));

        return server;
    }

    private static void onInboundMessage(
            WeixinMessage message,
            AtomicReference<ReplyTarget> latestReplyTarget,
            EventStore eventStore,
            ILinkClient cdnClient
    ) {
        if (message == null || message.itemList() == null || message.itemList().isEmpty()) {
            return;
        }

        if (hasText(message.fromUserId()) && hasText(message.contextToken())) {
            latestReplyTarget.set(new ReplyTarget(message.fromUserId(), message.contextToken()));
        }

        for (MessageItem item : message.itemList()) {
            if (item == null || item.type() == null) {
                continue;
            }
            switch (item.type()) {
                case ProtocolValues.ITEM_TYPE_TEXT -> handleInboundText(message, item, eventStore);
                case ProtocolValues.ITEM_TYPE_IMAGE -> handleInboundImage(message, item.imageItem(), eventStore, cdnClient);
                case ProtocolValues.ITEM_TYPE_VOICE -> handleInboundVoice(message, item.voiceItem(), eventStore, cdnClient);
                case ProtocolValues.ITEM_TYPE_FILE -> handleInboundFile(message, item.fileItem(), eventStore, cdnClient);
                default -> {
                    // ignore unsupported item types in this demo
                }
            }
        }
    }

    private static void handleInboundText(WeixinMessage message, MessageItem item, EventStore eventStore) {
        if (item.textItem() == null || item.textItem().text() == null) {
            return;
        }
        eventStore.add("inbound", "text", message.fromUserId(), item.textItem().text(), null, null, null);
        System.out.println("[inbound:text] from=" + message.fromUserId() + " text=" + item.textItem().text());
    }

    private static void handleInboundImage(
            WeixinMessage message,
            ImageItem imageItem,
            EventStore eventStore,
            ILinkClient cdnClient
    ) {
        if (imageItem == null || imageItem.media() == null) {
            return;
        }
        byte[] plain = downloadMediaBytes(cdnClient, imageItem.media(), imageItem.aeskey());
        String fileName = buildMediaNameWithExtension(message.messageId(), "image", detectImageExtension(plain));
        Path localPath = writeDownload(fileName, plain);
        String downloadUrl = "/downloads/" + urlEncodeSegment(localPath.getFileName().toString());
        String previewUrl = "/media/" + urlEncodeSegment(localPath.getFileName().toString());

        eventStore.add(
            "inbound",
            "image",
            message.fromUserId(),
            fileName,
            localPath.getFileName().toString(),
            downloadUrl,
            previewUrl
        );
        System.out.println("[inbound:image] from=" + message.fromUserId() + " saved=" + localPath.toAbsolutePath());
    }

    private static void handleInboundVoice(
            WeixinMessage message,
            VoiceItem voiceItem,
            EventStore eventStore,
            ILinkClient cdnClient
    ) {
        if (voiceItem == null || voiceItem.media() == null) {
            return;
        }
        byte[] plain = downloadMediaBytes(cdnClient, voiceItem.media(), null);
        String fileName = buildMediaNameWithExtension(message.messageId(), "voice", "silk");
        Path localPath = writeDownload(fileName, plain);
        String downloadUrl = "/downloads/" + urlEncodeSegment(localPath.getFileName().toString());
        String previewUrl = "/media/" + urlEncodeSegment(localPath.getFileName().toString());

        eventStore.add(
            "inbound",
            "voice",
            message.fromUserId(),
            fileName,
            localPath.getFileName().toString(),
            downloadUrl,
            previewUrl
        );
        System.out.println("[inbound:voice] from=" + message.fromUserId() + " saved=" + localPath.toAbsolutePath());
    }

    private static void handleInboundFile(
            WeixinMessage message,
            FileItem fileItem,
            EventStore eventStore,
            ILinkClient cdnClient
    ) {
        if (fileItem == null || fileItem.media() == null) {
            return;
        }
        byte[] plain = downloadMediaBytes(cdnClient, fileItem.media(), null);
        String sourceName = safeName(fileItem.fileName());
        String fileName = buildMediaNameWithSourceName(message.messageId(), "file", sourceName);
        Path localPath = writeDownload(fileName, plain);
        String downloadUrl = "/downloads/" + urlEncodeSegment(localPath.getFileName().toString());
        String previewUrl = "/media/" + urlEncodeSegment(localPath.getFileName().toString());

        eventStore.add(
            "inbound",
            "file",
            message.fromUserId(),
            sourceName,
            localPath.getFileName().toString(),
            downloadUrl,
            previewUrl
        );
        System.out.println("[inbound:file] from=" + message.fromUserId() + " saved=" + localPath.toAbsolutePath());
    }

    private static byte[] downloadMediaBytes(ILinkClient cdnClient, CDNMedia media, String imageAesKeyHex) {
        try {
            return cdnClient.downloadAndDecryptMedia(media, imageAesKeyHex);
        } catch (Exception e) {
            throw new ILinkException("Failed to download media", e);
        }
    }

    private static Path writeDownload(String fileName, byte[] bytes) {
        try {
            Files.createDirectories(DOWNLOAD_DIR);
            Path root = DOWNLOAD_DIR.toAbsolutePath().normalize();
            Path path = root.resolve(fileName).normalize();
            if (!path.startsWith(root)) {
                throw new ILinkException("Invalid download file path");
            }
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return path;
        } catch (IOException e) {
            throw new ILinkException("Failed to write downloaded media: " + fileName, e);
        }
    }

    private static List<Map<String, Object>> listDownloads(Path downloadDir) {
        try {
            if (!Files.exists(downloadDir)) {
                return List.of();
            }
            try (var stream = Files.list(downloadDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparingLong(WebQuickStartExampleTest::safeLastModified).reversed())
                        .map(path -> {
                            String name = path.getFileName().toString();
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("name", name);
                            row.put("size", safeSize(path));
                            row.put("updatedAt", safeLastModified(path));
                            row.put("downloadUrl", "/downloads/" + urlEncodeSegment(name));
                            return row;
                        })
                        .toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void serveDownload(HttpExchange exchange, Path downloadDir) throws IOException {
        serveStoredFile(exchange, downloadDir, "/downloads/", true);
    }

    private static void serveMedia(HttpExchange exchange, Path downloadDir) throws IOException {
        serveStoredFile(exchange, downloadDir, "/media/", false);
    }

    private static void serveStoredFile(HttpExchange exchange, Path downloadDir, String pathPrefix, boolean forceDownload)
            throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        String namePart = rawPath.substring(pathPrefix.length());
        if (namePart.isBlank()) {
            sendJson(exchange, 400, Map.of("error", "download file name missing"));
            return;
        }

        String fileName = URLDecoder.decode(namePart, StandardCharsets.UTF_8);
        Path root = downloadDir.toAbsolutePath().normalize();
        Path path = root.resolve(fileName).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            sendJson(exchange, 404, Map.of("error", "file not found"));
            return;
        }

        byte[] bytes = Files.readAllBytes(path);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", Optional.ofNullable(Files.probeContentType(path)).orElse("application/octet-stream"));
        String dispositionType = forceDownload ? "attachment" : "inline";
        headers.set("Content-Disposition", dispositionType + "; filename=\"" + path.getFileName() + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static long parseAfterId(URI uri) {
        Map<String, String> queryMap = parseQueryMap(uri.getRawQuery());
        String value = queryMap.get("afterId");
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Map<String, String> parseQueryMap(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        String[] parts = rawQuery.split("&");
        for (String part : parts) {
            int index = part.indexOf('=');
            String key = index >= 0 ? part.substring(0, index) : part;
            String value = index >= 0 ? part.substring(index + 1) : "";
            map.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }
        return map;
    }

    private static void withHandled(HttpExchange exchange, ExchangeAction action) {
        try {
            action.run();
        } catch (IllegalStateException e) {
            try {
                sendJson(exchange, 409, Map.of("error", e.getMessage()));
            } catch (IOException ignored) {
                exchange.close();
            }
        } catch (Exception e) {
            try {
                sendJson(exchange, 500, Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            } catch (IOException ignored) {
                exchange.close();
            }
        }
    }

    private static <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return JSON.readValue(bytes, type);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(payload);
        sendBytes(exchange, status, bytes, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        sendBytes(exchange, status, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private static void sendMethodNotAllowed(HttpExchange exchange, String expectedMethod) throws IOException {
        exchange.getResponseHeaders().set("Allow", expectedMethod);
        sendJson(exchange, 405, Map.of("error", "method not allowed"));
    }

    private static void sendBytes(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String loadIndexHtml() {
        try (InputStream inputStream = WebQuickStartExampleTest.class.getResourceAsStream(INDEX_HTML_RESOURCE)) {
            if (inputStream == null) {
                return "<!doctype html><html><body><h1>quick-start-web.html not found</h1></body></html>";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ILinkException("Failed to load web page resource", e);
        }
    }

    private static ReplyTarget requireReplyTarget(ReplyTarget target) {
        if (target == null) {
            throw new IllegalStateException("尚未收到可回复的用户消息");
        }
        return target;
    }

    private static byte[] decodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(value);
    }

    private static int parsePort(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid port: " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port argument: " + args[0], e);
        }
    }

    private static String buildMediaNameWithExtension(Long messageId, String kind, String extension) {
        String suffix = extension == null || extension.isBlank() ? "bin" : extension;
        String normalized = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        return "msg-" + safeLong(messageId) + "-" + kind + "." + normalized;
    }

    private static String detectImageExtension(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return "bin";
        }
        if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            return "png";
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) {
            return "jpg";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "gif";
        }
        if (bytes.length > 12 && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return "bin";
    }

    private static String buildMediaNameWithSourceName(Long messageId, String kind, String sourceFileName) {
        String cleaned = safeName(sourceFileName);
        return "msg-" + safeLong(messageId) + "-" + kind + "-" + cleaned;
    }

    private static String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file.bin";
        }
        String cleaned = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "file.bin";
        }
        return cleaned;
    }

    private static String urlEncodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static long safeLong(Long value) {
        return value == null ? System.currentTimeMillis() : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1L;
        }
    }

    private static long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private record AppContext(
            ILinkBot bot,
            WebSessionHandler sessionHandler,
            AtomicReference<ReplyTarget> latestReplyTarget,
            EventStore eventStore,
            Path downloadDir
    ) {
        private Map<String, Object> snapshotState() {
            ReplyTarget target = latestReplyTarget.get();
            ILinkAuthSession session = sessionHandler.currentSession();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loggedIn", session != null);
            payload.put("qrCodeImgContent", sessionHandler.qrCodeImgContent());
            payload.put("latestReplyUserId", target == null ? null : target.toUserId());
            payload.put("latestReplyContextToken", target == null ? null : target.contextToken());
            payload.put("accountId", session == null ? null : session.accountId());
            payload.put("userId", session == null ? null : session.userId());
            payload.put("downloadsDir", downloadDir.toAbsolutePath().toString());
            return payload;
        }
    }

    private static final class EventStore {

        private static final DateTimeFormatter TIME_FORMATTER =
                DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

        private final int maxEvents;
        private final AtomicLong idCounter = new AtomicLong();
        private final ConcurrentLinkedDeque<Map<String, Object>> events = new ConcurrentLinkedDeque<>();

        private EventStore(int maxEvents) {
            this.maxEvents = maxEvents;
        }

        private void add(
            String direction,
            String kind,
            String fromUserId,
            String content,
            String fileName,
            String downloadUrl,
            String previewUrl
        ) {
            long id = idCounter.incrementAndGet();
            long now = System.currentTimeMillis();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("timestamp", now);
            row.put("time", TIME_FORMATTER.format(Instant.ofEpochMilli(now)));
            row.put("direction", direction);
            row.put("kind", kind);
            row.put("fromUserId", fromUserId);
            row.put("content", content);
            row.put("fileName", fileName);
            row.put("downloadUrl", downloadUrl);
            row.put("previewUrl", previewUrl);

            events.addLast(row);
            while (events.size() > maxEvents) {
                events.pollFirst();
            }
        }

        private List<Map<String, Object>> listAfter(long afterId) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : events) {
                Object idValue = row.get("id");
                if (!(idValue instanceof Long id) || id <= afterId) {
                    continue;
                }
                result.add(row);
            }
            return result;
        }

        private long latestId() {
            return idCounter.get();
        }
    }

    private static final class WebSessionHandler implements SessionHandler {

        private final Path sessionPath;
        private final EventStore eventStore;
        private final AtomicReference<ILinkAuthSession> sessionRef = new AtomicReference<>();
        private final AtomicReference<String> qrCodeImgContentRef = new AtomicReference<>();

        private WebSessionHandler(Path sessionPath, EventStore eventStore) {
            this.sessionPath = Objects.requireNonNull(sessionPath, "sessionPath cannot be null");
            this.eventStore = Objects.requireNonNull(eventStore, "eventStore cannot be null");
        }

        @Override
        public ILinkAuthSession loadSession() {
            if (!Files.exists(sessionPath)) {
                return null;
            }
            try {
                List<String> lines = Files.readAllLines(sessionPath, StandardCharsets.UTF_8);
                if (lines.size() < 4) {
                    return null;
                }
                ILinkAuthSession session = new ILinkAuthSession(lines.get(0), lines.get(1), lines.get(2), lines.get(3));
                sessionRef.set(session);
                eventStore.add("system", "session", "local", "Loaded existing session", null, null, null);
                return session;
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public void persistSession(ILinkAuthSession session) {
            List<String> lines = List.of(
                    safe(session.token()),
                    safe(session.baseUrl()),
                    safe(session.accountId()),
                    safe(session.userId())
            );
            try {
                Files.write(
                        sessionPath,
                        lines,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
                sessionRef.set(session);
                qrCodeImgContentRef.set(null);
                eventStore.add("system", "session", "local", "Session persisted", null, null, null);
            } catch (IOException e) {
                throw new ILinkException("Failed to persist session", e);
            }
        }

        @Override
        public void clearSession(ILinkAuthSession expiredSession) {
            try {
                Files.deleteIfExists(sessionPath);
            } catch (IOException ignored) {
                // best effort
            }
            sessionRef.set(null);
            eventStore.add("system", "session", "local", "Session expired and cleared", null, null, null);
        }

        @Override
        public void onQrcode(QrCodeResponse qrCodeResponse) {
            String qr = Optional.ofNullable(qrCodeResponse)
                    .map(QrCodeResponse::qrcodeImgContent)
                    .orElse(null);
            qrCodeImgContentRef.set(qr);
            eventStore.add("system", "qrcode", "local", "QR code updated", null, null, null);
        }

        private ILinkAuthSession currentSession() {
            return sessionRef.get();
        }

        private String qrCodeImgContent() {
            return qrCodeImgContentRef.get();
        }
    }

    private record SendTextPayload(String text) {
    }

    private record SendBinaryPayload(String name, String contentBase64) {
    }

    private record ReplyTarget(String toUserId, String contextToken) {
    }

    @FunctionalInterface
    private interface ExchangeAction {

        void run() throws Exception;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
