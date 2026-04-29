package io.github.morningwn.client;

import io.github.morningwn.exception.ILinkException;
import io.github.morningwn.exception.SessionExpiredException;
import io.github.morningwn.handler.MessageHandler;
import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.BaseInfo;
import io.github.morningwn.protocol.CDNMedia;
import io.github.morningwn.protocol.FileItem;
import io.github.morningwn.protocol.GetUpdatesResponse;
import io.github.morningwn.protocol.GetUploadUrlRequest;
import io.github.morningwn.protocol.GetUploadUrlResponse;
import io.github.morningwn.protocol.ImageItem;
import io.github.morningwn.protocol.MessageItem;
import io.github.morningwn.protocol.ProtocolValues;
import io.github.morningwn.protocol.QrCodeResponse;
import io.github.morningwn.protocol.QrCodeStatusResponse;
import io.github.morningwn.protocol.SendMessageResponse;
import io.github.morningwn.protocol.VideoItem;
import io.github.morningwn.protocol.VoiceItem;
import io.github.morningwn.protocol.WeixinMessage;
import io.github.morningwn.util.ClientIdGenerator;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-level ready-to-use bot facade.
 *
 * <p>This class hides low-level details, including long polling loop management,
 * text chunking for long messages and media upload flow.</p>
 */
public final class ILinkBot implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ILinkBot.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int AES_ENCRYPT_TYPE = 1;
    private static final int RETRY_DELAY_MS = 1_000;
    private static final int RETRY_DELAY_MAX_MS = 15_000;
    private static final int QR_POLL_INTERVAL_MS = 1_000;
    private static final int MIN_LONG_POLL_TIMEOUT_MS = 3_000;
    private static final int MAX_LONG_POLL_TIMEOUT_MS = 60_000;
    private static final int LONG_POLL_TIMEOUT_MARGIN_MS = 1_000;
    private static final int SHUTDOWN_CLEANUP_TIMEOUT_MS = 1_000;
    private static final long SHUTDOWN_WAIT_MILLIS = 2_000L;
    private static final long FORCED_SHUTDOWN_WAIT_MILLIS = 1_000L;

    private final ILinkClient client;
    private final ILinkClientConfig config;
    private final SessionHandler sessionHandler;
    private final String clientIdPrefix;
    private final boolean ownsClient;
    private final Object sessionLock = new Object();

    private final AtomicBoolean autoPulling = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final ExecutorService pullExecutor;
    private volatile Future<?> pullTask;
    private volatile String getUpdatesBuf = "";

    private volatile ILinkAuthSession session;

    /**
     * Creates a bot facade with internal {@link ILinkClient}. Session will be restored by
     * {@link SessionHandler} or created via QR scan on demand.
     *
     * @param config client config
     * @param sessionHandler optional handler for session persistence and QR notification
     */
    public ILinkBot(ILinkClientConfig config, SessionHandler sessionHandler) {
        this(new ILinkClient(config), config, "weixin-ilink", true, sessionHandler, null);
    }

    /**
     * Creates a bot facade with internal {@link ILinkClient}.
     *
     * @param config         client config
     * @param clientIdPrefix sendmessage client_id prefix
     * @param sessionHandler optional handler for session persistence and QR notification
     */
    public ILinkBot(
            ILinkClientConfig config,
            String clientIdPrefix,
            SessionHandler sessionHandler
    ) {
        this(new ILinkClient(config), config, clientIdPrefix, true, sessionHandler, null);
    }

    /**
     * Creates a bot facade on top of an existing low-level client.
     *
     * @param client         low-level client
     * @param config         client config
     * @param sessionHandler optional handler for session persistence and QR notification
     */
    public ILinkBot(
            ILinkClient client,
            ILinkClientConfig config,
            SessionHandler sessionHandler
    ) {
        this(client, config, "weixin-ilink", false, sessionHandler, null);
    }

    /**
     * Creates a bot facade on top of an existing low-level client.
     *
     * @param client         low-level client
     * @param config         client config
     * @param clientIdPrefix sendmessage client_id prefix
     * @param sessionHandler optional handler for session persistence and QR notification
     */
    public ILinkBot(
            ILinkClient client,
            ILinkClientConfig config,
            String clientIdPrefix,
            SessionHandler sessionHandler
    ) {
        this(client, config, clientIdPrefix, false, sessionHandler, null);
    }

    private ILinkBot(
            ILinkClient client,
            ILinkClientConfig config,
            String clientIdPrefix,
            boolean ownsClient,
            SessionHandler sessionHandler,
            ExecutorService pullExecutor) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");

        this.sessionHandler = sessionHandler;
        this.clientIdPrefix = clientIdPrefix == null || clientIdPrefix.isBlank() ? "weixin-ilink" : clientIdPrefix;
        this.ownsClient = ownsClient;
        this.session = loadSessionFromHandler();
        this.pullExecutor = pullExecutor != null ? pullExecutor : Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ilink-auto-pull");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts background long polling and dispatches each inbound message to the handler.
     *
     * @param handler inbound message handler
     */
    public synchronized void startAutoPull(MessageHandler handler) {
        Objects.requireNonNull(handler, "handler cannot be null");
        if (closing.get()) {
            throw new ILinkException("Bot is already closing");
        }
        if (!autoPulling.compareAndSet(false, true)) {
            return;
        }
        pullTask = pullExecutor.submit(() -> runAutoPullLoop(handler));
    }

    /**
     * Stops background long polling.
     */
    public synchronized void stopAutoPull() {
        stopAutoPullInternal(true);
    }

    /**
     * @return whether background auto pull is running
     */
    public boolean isAutoPulling() {
        return autoPulling.get();
    }

    /**
     * Sets the getupdates cursor, useful for restoring cursor after restart.
     *
     * @param getUpdatesBuf getupdates cursor
     */
    public void setGetUpdatesBuf(String getUpdatesBuf) {
        this.getUpdatesBuf = getUpdatesBuf == null ? "" : getUpdatesBuf;
    }

    /**
     * @return current getupdates cursor
     */
    public String getGetUpdatesBuf() {
        return getUpdatesBuf;
    }

    /**
     * Sends text and automatically splits oversized content into multiple chunks.
     *
     * @param toUserId target user id
     * @param contextToken context token
     * @param text text content
     * @return send responses in sending order
     */
    public List<SendMessageResponse> sendText(String toUserId, String contextToken, String text) {
        return executeWithSessionRetry(
            currentSession -> client.sendText(currentSession, toUserId, contextToken, text, clientIdPrefix)
        );
    }

    /**
     * Replies to an inbound message with text.
     *
     * @param inbound inbound message
     * @param text reply content
     * @return send responses in sending order
     */
    public List<SendMessageResponse> replyText(WeixinMessage inbound, String text) {
        Objects.requireNonNull(inbound, "inbound cannot be null");
        requireNonBlank(inbound.fromUserId(), "inbound.fromUserId");
        requireNonBlank(inbound.contextToken(), "inbound.contextToken");
        return sendText(inbound.fromUserId(), inbound.contextToken(), text);
    }

    /**
     * Uploads and sends one image.
     *
     * @param toUserId target user id
     * @param contextToken context token
     * @param imageBytes image content bytes
     * @return send response
     */
    public SendMessageResponse sendImage(String toUserId, String contextToken, byte[] imageBytes) {
        return executeWithSessionRetry(currentSession -> {
            UploadedMedia uploadedMedia = uploadMedia(currentSession, toUserId, ProtocolValues.ITEM_TYPE_IMAGE, imageBytes);
            ImageItem imageItem = new ImageItem(
                uploadedMedia.media(),
                null,
                uploadedMedia.aesKeyHex(),
                null,
                uploadedMedia.encryptedSize(),
                null,
                null,
                null,
                null
            );
            MessageItem item = new MessageItem(
                ProtocolValues.ITEM_TYPE_IMAGE,
                null,
                null,
                null,
                null,
                null,
                null,
                imageItem,
                null,
                null,
                null
            );
            return client.sendMessage(currentSession, buildMessage(toUserId, contextToken, item));
        });
    }

    /**
     * Uploads and sends one image from file path.
     *
     * @param toUserId target user id
     * @param contextToken context token
     * @param imagePath image file path
     * @return send response
     */
    public SendMessageResponse sendImage(String toUserId, String contextToken, Path imagePath) {
        return sendImage(toUserId, contextToken, readAllBytes(imagePath));
    }

    /**
     * Uploads and sends one file.
     *
     * @param toUserId target user id
     * @param contextToken context token
     * @param fileName file name shown in message
     * @param fileBytes file bytes
     * @return send response
     */
    public SendMessageResponse sendFile(String toUserId, String contextToken, String fileName, byte[] fileBytes) {
        requireNonBlank(fileName, "fileName");
        return executeWithSessionRetry(currentSession -> {
            UploadedMedia uploadedMedia = uploadMedia(currentSession, toUserId, ProtocolValues.ITEM_TYPE_FILE, fileBytes);
            FileItem fileItem = new FileItem(
                uploadedMedia.media(),
                fileName,
                uploadedMedia.rawMd5(),
                Long.toString(uploadedMedia.rawSize())
            );
            MessageItem item = new MessageItem(
                ProtocolValues.ITEM_TYPE_FILE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                fileItem,
                null
            );
            return client.sendMessage(currentSession, buildMessage(toUserId, contextToken, item));
        });
    }

    /**
     * Uploads and sends one file from local path.
     *
     * @param toUserId target user id
     * @param contextToken context token
     * @param filePath local file path
     * @return send response
     */
    public SendMessageResponse sendFile(String toUserId, String contextToken, Path filePath) {
        String fileName = filePath.getFileName() == null ? "file.bin" : filePath.getFileName().toString();
        return sendFile(toUserId, contextToken, fileName, readAllBytes(filePath));
    }

    /**
     * Uploads and sends one voice message.
     *
     * @param toUserId target user id
     * @param contextToken context token
     * @param voiceBytes voice bytes
     * @param playtime playtime in milliseconds
     * @return send response
     */
    public SendMessageResponse sendVoice(String toUserId, String contextToken, byte[] voiceBytes, long playtime) {
        if (playtime < 0) {
            throw new ILinkException("playtime cannot be negative");
        }
        return executeWithSessionRetry(currentSession -> {
            UploadedMedia uploadedMedia = uploadMedia(currentSession, toUserId, ProtocolValues.ITEM_TYPE_VOICE, voiceBytes);
            VoiceItem voiceItem = new VoiceItem(
                uploadedMedia.media(),
                null,
                null,
                null,
                playtime,
                null
            );
            MessageItem item = new MessageItem(
                ProtocolValues.ITEM_TYPE_VOICE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                voiceItem,
                null,
                null
            );
            return client.sendMessage(currentSession, buildMessage(toUserId, contextToken, item));
        });
    }

    /**
     * Uploads and sends one video message.
     *
     * @param toUserId target user id
     * @param contextToken context token
     * @param videoBytes video bytes
     * @return send response
     */
    public SendMessageResponse sendVideo(String toUserId, String contextToken, byte[] videoBytes) {
        return executeWithSessionRetry(currentSession -> {
            UploadedMedia uploadedMedia = uploadMedia(currentSession, toUserId, ProtocolValues.ITEM_TYPE_VIDEO, videoBytes);
            VideoItem videoItem = new VideoItem(
                uploadedMedia.media(),
                uploadedMedia.encryptedSize(),
                null,
                uploadedMedia.rawMd5(),
                null,
                null,
                null,
                null
            );
            MessageItem item = new MessageItem(
                ProtocolValues.ITEM_TYPE_VIDEO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                videoItem
            );
            return client.sendMessage(currentSession, buildMessage(toUserId, contextToken, item));
        });
    }

    /**
     * Stops background tasks and closes internally owned HTTP client.
     */
    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        stopAutoPullInternal(false);
        pullExecutor.shutdown();
        awaitPullExecutorTermination();
        performShutdownCleanup();
        if (ownsClient) {
            try {
                client.close();
            } catch (RuntimeException e) {
                LOG.warn("Failed to close internal ILinkClient", e);
            }
        }
    }

    private void runAutoPullLoop(MessageHandler handler) {
        long retryDelayMs = RETRY_DELAY_MS;
        Duration longPollingTimeout = normalizeLongPollingTimeout(config.getLongPollingTimeout());
        while (autoPulling.get()) {
            try {
                String currentGetUpdatesBuf = getUpdatesBuf;
                Duration requestTimeout = longPollingTimeout;
                GetUpdatesResponse response = executeWithSessionRetry(
                        currentSession -> client.getUpdates(currentSession, getUpdatesBuf, requestTimeout)
                );
                retryDelayMs = RETRY_DELAY_MS;
                longPollingTimeout = deriveNextLongPollingTimeout(longPollingTimeout, response.longpollingTimeoutMs());
                String suggestedGetUpdatesBuf = normalizeGetUpdatesBuf(response.getUpdatesBuf());

                List<WeixinMessage> messages = response.msgs();
                boolean fullyProcessed = processMessageBatch(handler, messages);
                String confirmedGetUpdatesBuf = resolveConfirmedGetUpdatesBuf(
                        currentGetUpdatesBuf,
                        suggestedGetUpdatesBuf,
                        messages,
                        fullyProcessed
                );
                if (!Objects.equals(currentGetUpdatesBuf, confirmedGetUpdatesBuf)) {
                    getUpdatesBuf = confirmedGetUpdatesBuf;
                }

                if (!fullyProcessed) {
                    if (!autoPulling.get()) {
                        LOG.debug("Auto pull stopping, cursor commit deferred");
                        return;
                    }
                    LOG.warn("Message batch was not fully processed, cursor commit is deferred");
                    sleepBeforeRetry(retryDelayMs);
                    retryDelayMs = Math.min(retryDelayMs * 2L, RETRY_DELAY_MAX_MS);
                }
            } catch (SessionExpiredException e) {
                if (shouldSuppressDuringShutdown(e)) {
                    LOG.debug("Auto pull stopped during shutdown after session expiration");
                    return;
                }
                LOG.warn("Session expired during auto pull and retry also failed", e);
                sleepBeforeRetry(retryDelayMs);
                retryDelayMs = Math.min(retryDelayMs * 2L, RETRY_DELAY_MAX_MS);
            } catch (RuntimeException e) {
                if (shouldSuppressDuringShutdown(e)) {
                    LOG.debug("Auto pull stopped during shutdown: {}", e.getMessage());
                    return;
                }
                LOG.error("Auto pull failed, retrying in {} ms", retryDelayMs, e);
                sleepBeforeRetry(retryDelayMs);
                retryDelayMs = Math.min(retryDelayMs * 2L, RETRY_DELAY_MAX_MS);
            }
        }
    }

    private boolean processMessageBatch(MessageHandler handler, List<WeixinMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return true;
        }
        for (WeixinMessage message : messages) {
            if (!autoPulling.get()) {
                return false;
            }
            if (message == null) {
                continue;
            }
            try {
                handler.handle(message);
            } catch (Exception e) {
                LOG.error("Message handler failed, messageId={}", message.messageId(), e);
                return false;
            }
        }
        return true;
    }

    private String resolveConfirmedGetUpdatesBuf(
            String currentGetUpdatesBuf,
            String suggestedGetUpdatesBuf,
            List<WeixinMessage> receivedMessages,
            boolean fullyProcessed
    ) {
        String safeCurrentBuf = currentGetUpdatesBuf == null ? "" : currentGetUpdatesBuf;
        String safeSuggestedBuf = normalizeGetUpdatesBuf(suggestedGetUpdatesBuf);
        if (safeSuggestedBuf == null) {
            return safeCurrentBuf;
        }
        if (sessionHandler == null) {
            return fullyProcessed ? safeSuggestedBuf : safeCurrentBuf;
        }
        try {
            String confirmedGetUpdatesBuf = sessionHandler.confirmGetUpdatesBuf(
                    safeCurrentBuf,
                    safeSuggestedBuf,
                    receivedMessages == null ? List.of() : List.copyOf(receivedMessages),
                    fullyProcessed
            );
            String normalizedConfirmedBuf = normalizeGetUpdatesBuf(confirmedGetUpdatesBuf);
            return normalizedConfirmedBuf == null ? safeCurrentBuf : normalizedConfirmedBuf;
        } catch (Exception e) {
            LOG.warn("Session handler confirmGetUpdatesBuf failed, keep current cursor", e);
            return safeCurrentBuf;
        }
    }

    private UploadedMedia uploadMedia(ILinkAuthSession currentSession, String toUserId, int itemType, byte[] plaintext) {
        requireNonBlank(toUserId, "toUserId");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        int mediaType = toUploadMediaType(itemType);
        String fileKey = randomHex(16);
        String aesKeyHex = randomHex(16);
        String rawMd5 = DigestUtils.md5Hex(plaintext);

        byte[] encrypted = client.encryptMedia(plaintext, aesKeyHex);
        GetUploadUrlRequest request = new GetUploadUrlRequest(
                fileKey,
                mediaType,
                toUserId,
                (long) plaintext.length,
                rawMd5,
                (long) encrypted.length,
                null,
                null,
                null,
                Boolean.TRUE,
                aesKeyHex,
                BaseInfo.of(config.getChannelVersion())
        );
            GetUploadUrlResponse response = client.getUploadUrl(currentSession, request);

        CdnUploadResult uploadResult = client.uploadEncryptedMedia(
                response.uploadFullUrl(),
                response.uploadParam(),
                fileKey,
                encrypted
        );
        requireNonBlank(uploadResult.encryptedParam(), "x-encrypted-param");

        CDNMedia media = new CDNMedia(
                uploadResult.encryptedParam(),
                encodeAesKeyForMedia(aesKeyHex),
                AES_ENCRYPT_TYPE,
                null
        );

        return new UploadedMedia(media, aesKeyHex, rawMd5, plaintext.length, encrypted.length);
    }

    private ILinkAuthSession ensureSessionAvailable() {
        ILinkAuthSession currentSession = session;
        if (currentSession != null) {
            return currentSession;
        }

        synchronized (sessionLock) {
            currentSession = session;
            if (currentSession != null) {
                return currentSession;
            }

            currentSession = loginByQrCode();
            session = currentSession;
            persistSession(currentSession);
            return currentSession;
        }
    }

    private ILinkAuthSession loginByQrCode() {
        while (true) {
            QrCodeResponse qrCodeResponse = client.getBotQrcode();
            requireNonBlank(qrCodeResponse.qrcode(), "qrcode");

            LOG.info("Session missing, waiting for QR confirmation. qrcode_img_content={}",
                    qrCodeResponse.qrcodeImgContent());
            notifyQrcode(qrCodeResponse);

            String qrBaseUrl = config.getBaseUrl();
            while (true) {
                QrCodeStatusResponse statusResponse = client.getQrcodeStatus(qrCodeResponse.qrcode(), qrBaseUrl);
                String status = statusResponse.status();
                if (status == null || status.isBlank()
                        || ProtocolValues.QR_STATUS_WAIT.equals(status)
                        || ProtocolValues.QR_STATUS_SCANED.equals(status)) {
                    sleepQrPolling();
                    continue;
                }
                if (ProtocolValues.QR_STATUS_SCANED_BUT_REDIRECT.equals(status)) {
                    qrBaseUrl = normalizeQrBaseUrl(statusResponse.redirectHost(), qrBaseUrl);
                    continue;
                }
                if (ProtocolValues.QR_STATUS_CONFIRMED.equals(status)) {
                    return client.toAuthSession(statusResponse);
                }
                if (ProtocolValues.QR_STATUS_EXPIRED.equals(status)) {
                    LOG.info("QR code expired, requesting a new QR code");
                    break;
                }
                LOG.warn("Unexpected QR status={}, will continue polling", status);
                sleepQrPolling();
            }
        }
    }

    private static String normalizeQrBaseUrl(String redirectHost, String fallbackBaseUrl) {
        if (redirectHost == null || redirectHost.isBlank()) {
            return fallbackBaseUrl;
        }
        if (redirectHost.startsWith("http://") || redirectHost.startsWith("https://")) {
            return redirectHost;
        }
        return "https://" + redirectHost;
    }

    private void notifyQrcode(QrCodeResponse qrCodeResponse) {
        if (sessionHandler == null) {
            return;
        }
        try {
            sessionHandler.onQrcode(qrCodeResponse);
        } catch (Exception e) {
            LOG.warn("Session handler onQrcode failed", e);
        }
    }

    private ILinkAuthSession loadSessionFromHandler() {
        if (sessionHandler == null) {
            return null;
        }
        try {
            ILinkAuthSession loadedSession = sessionHandler.loadSession();
            if (loadedSession != null) {
                LOG.info("Loaded persisted session from handler");
            }
            return loadedSession;
        } catch (Exception e) {
            LOG.warn("Session handler loadSession failed", e);
            return null;
        }
    }

    private void persistSession(ILinkAuthSession currentSession) {
        if (sessionHandler == null || currentSession == null) {
            return;
        }
        try {
            sessionHandler.persistSession(currentSession);
        } catch (Exception e) {
            LOG.warn("Session handler persistSession failed", e);
        }
    }

    private void clearSession(ILinkAuthSession expiredSession) {
        if (sessionHandler == null || expiredSession == null) {
            return;
        }
        try {
            sessionHandler.clearSession(expiredSession);
        } catch (Exception e) {
            LOG.warn("Session handler clearSession failed", e);
        }
    }

    private void invalidateSession(ILinkAuthSession currentSession) {
        boolean cleared = false;
        synchronized (sessionLock) {
            if (session != null && session.equals(currentSession)) {
                session = null;
                cleared = true;
            }
        }
        if (cleared) {
            clearSession(currentSession);
        }
    }

    private static void sleepQrPolling() {
        try {
            Thread.sleep(QR_POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ILinkException("Interrupted while waiting for QR confirmation", e);
        }
    }

    private <T> T executeWithSessionRetry(SessionOperation<T> operation) {
        int retries = 0;
        while (true) {
            ILinkAuthSession currentSession = ensureSessionAvailable();
            try {
                return operation.execute(currentSession);
            } catch (SessionExpiredException e) {
                invalidateSession(currentSession);
                if (retries >= 1) {
                    throw e;
                }
                retries++;
                LOG.info("Session expired, attempting QR re-login");
            }
        }
    }

    private WeixinMessage buildMessage(String toUserId, String contextToken, MessageItem item) {
        requireNonBlank(toUserId, "toUserId");
        requireNonBlank(contextToken, "contextToken");
        return new WeixinMessage(
                null,
                null,
                "",
                toUserId,
                ClientIdGenerator.generate(clientIdPrefix),
                null,
                null,
                null,
                null,
                null,
                ProtocolValues.MESSAGE_TYPE_BOT,
                ProtocolValues.MESSAGE_STATE_FINISH,
                List.of(item),
                contextToken
        );
    }

    private static byte[] readAllBytes(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        try {
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new ILinkException("Failed to read file: " + path, e);
        }
    }

    private static String randomHex(int byteLength) {
        if (byteLength <= 0) {
            throw new ILinkException("byteLength must be positive");
        }
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Hex.encodeHexString(bytes);
    }

    private static String encodeAesKeyForMedia(String aesKeyHex) {
        return Base64.getEncoder().encodeToString(aesKeyHex.getBytes(StandardCharsets.US_ASCII));
    }

    private static int toUploadMediaType(int itemType) {
        return switch (itemType) {
            case ProtocolValues.ITEM_TYPE_IMAGE -> 1;
            case ProtocolValues.ITEM_TYPE_VIDEO -> 2;
            case ProtocolValues.ITEM_TYPE_FILE -> 3;
            case ProtocolValues.ITEM_TYPE_VOICE -> 4;
            default -> throw new ILinkException("Unsupported media item type: " + itemType);
        };
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ILinkException(field + " cannot be null or blank");
        }
    }

    private Duration deriveNextLongPollingTimeout(Duration currentTimeout, Integer responseTimeoutMs) {
        if (responseTimeoutMs == null || responseTimeoutMs <= 0) {
            return currentTimeout;
        }

        Duration nextTimeout = normalizeLongPollingTimeout(
                Duration.ofMillis((long) responseTimeoutMs + LONG_POLL_TIMEOUT_MARGIN_MS)
        );
        if (!nextTimeout.equals(currentTimeout)) {
            LOG.debug(
                    "Adjusted long polling timeout from {} ms to {} ms by server hint {} ms",
                    currentTimeout.toMillis(),
                    nextTimeout.toMillis(),
                    responseTimeoutMs
            );
        }
        return nextTimeout;
    }

    private static Duration normalizeLongPollingTimeout(Duration timeout) {
        long millis = timeout == null ? MIN_LONG_POLL_TIMEOUT_MS : timeout.toMillis();
        long clamped = Math.max(MIN_LONG_POLL_TIMEOUT_MS, Math.min(MAX_LONG_POLL_TIMEOUT_MS, millis));
        return Duration.ofMillis(clamped);
    }

    private void performShutdownCleanup() {
        ILinkAuthSession currentSession = session;
        if (currentSession == null) {
            return;
        }
        String currentGetUpdatesBuf = getUpdatesBuf;
        if (currentGetUpdatesBuf == null || currentGetUpdatesBuf.isBlank()) {
            return;
        }
        try {
            GetUpdatesResponse response = client.getUpdates(
                    currentSession,
                    currentGetUpdatesBuf,
                    Duration.ofMillis(SHUTDOWN_CLEANUP_TIMEOUT_MS)
            );
            List<WeixinMessage> messages = response.msgs();
            boolean hasPendingMessages = messages != null && !messages.isEmpty();
            String confirmedGetUpdatesBuf = resolveConfirmedGetUpdatesBuf(
                    currentGetUpdatesBuf,
                    response.getUpdatesBuf(),
                    messages,
                    !hasPendingMessages
            );
            if (!Objects.equals(currentGetUpdatesBuf, confirmedGetUpdatesBuf)) {
                getUpdatesBuf = confirmedGetUpdatesBuf;
            }
            if (hasPendingMessages) {
                LOG.info("Shutdown cleanup observed {} pending messages, cursor will not advance", messages.size());
            }
        } catch (SessionExpiredException e) {
            LOG.debug("Skip shutdown cleanup because session expired");
        } catch (RuntimeException e) {
            LOG.debug("Shutdown cleanup skipped: {}", e.getMessage());
        }
    }

    private static String normalizeGetUpdatesBuf(String getUpdatesBuf) {
        if (getUpdatesBuf == null || getUpdatesBuf.isBlank()) {
            return null;
        }
        return getUpdatesBuf;
    }

    private static void sleepBeforeRetry(long delayMillis) {
        long safeDelay = Math.max(delayMillis, 0L);
        try {
            Thread.sleep(safeDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void forceStopAutoPull() {
        stopAutoPullInternal(true);
    }

    private synchronized void stopAutoPullInternal(boolean interruptRunningTask) {
        autoPulling.set(false);
        if (pullTask == null) {
            return;
        }
        if (pullTask.isDone()) {
            pullTask = null;
            return;
        }
        if (interruptRunningTask) {
            pullTask.cancel(true);
            pullTask = null;
        }
    }

    private void awaitPullExecutorTermination() {
        boolean terminated = awaitTermination(SHUTDOWN_WAIT_MILLIS);
        if (terminated) {
            return;
        }
        LOG.info("Auto pull worker did not stop in {} ms, forcing shutdown", SHUTDOWN_WAIT_MILLIS);
        forceStopAutoPull();
        pullExecutor.shutdownNow();
        awaitTermination(FORCED_SHUTDOWN_WAIT_MILLIS);
    }

    private boolean awaitTermination(long timeoutMillis) {
        try {
            return pullExecutor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            forceStopAutoPull();
            pullExecutor.shutdownNow();
            return false;
        }
    }

    private boolean shouldSuppressDuringShutdown(Throwable throwable) {
        return closing.get() || !autoPulling.get() || Thread.currentThread().isInterrupted() || hasInterruptedCause(throwable);
    }

    private static boolean hasInterruptedCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    private interface SessionOperation<T> {

        T execute(ILinkAuthSession session);
    }

    private record UploadedMedia(
            CDNMedia media,
            String aesKeyHex,
            String rawMd5,
            long rawSize,
            long encryptedSize
    ) {
    }
}
