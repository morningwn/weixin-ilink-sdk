package io.github.morningwn.client;

import io.github.morningwn.codec.JacksonJsonCodec;
import io.github.morningwn.codec.JsonCodec;
import io.github.morningwn.exception.ILinkException;
import io.github.morningwn.exception.ILinkProtocolException;
import io.github.morningwn.exception.PartialTextSendException;
import io.github.morningwn.exception.SessionExpiredException;
import io.github.morningwn.protocol.BaseInfo;
import io.github.morningwn.protocol.CDNMedia;
import io.github.morningwn.protocol.ILinkAuthSession;
import io.github.morningwn.protocol.enums.BusinessCode;
import io.github.morningwn.protocol.enums.QrCodeStatus;
import io.github.morningwn.protocol.enums.TypingStatus;
import io.github.morningwn.protocol.message.MessageItem;
import io.github.morningwn.protocol.message.OutboundMessage;
import io.github.morningwn.protocol.message.TextMessageItem;
import io.github.morningwn.protocol.message.TextItem;
import io.github.morningwn.protocol.request.GetBotQrcodeRequest;
import io.github.morningwn.protocol.request.GetConfigRequest;
import io.github.morningwn.protocol.request.GetUpdatesRequest;
import io.github.morningwn.protocol.request.GetUploadUrlRequest;
import io.github.morningwn.protocol.request.NotifyRequest;
import io.github.morningwn.protocol.request.SendMessageRequest;
import io.github.morningwn.protocol.request.SendTypingRequest;
import io.github.morningwn.protocol.response.CdnUploadResult;
import io.github.morningwn.protocol.response.DownloadedMedia;
import io.github.morningwn.protocol.response.GetConfigResponse;
import io.github.morningwn.protocol.response.GetUpdatesResponse;
import io.github.morningwn.protocol.response.GetUploadUrlResponse;
import io.github.morningwn.protocol.response.NotifyResponse;
import io.github.morningwn.protocol.response.QrCodeResponse;
import io.github.morningwn.protocol.response.QrCodeStatusResponse;
import io.github.morningwn.protocol.response.SendMessageResponse;
import io.github.morningwn.protocol.response.SendTypingResponse;
import io.github.morningwn.util.ClientIdGenerator;
import io.github.morningwn.util.CryptoUtils;
import io.github.morningwn.util.HexUtils;
import io.github.morningwn.util.TextChunker;
import io.github.morningwn.util.WechatUinGenerator;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Core HTTP client for WeChat iLink Bot API.
 */
public class ILinkClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ILinkClient.class);

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String AUTHORIZATION_SCHEME_ILINK_BOT_TOKEN = "ilink_bot_token";
    private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_AUTHORIZATION_TYPE = "AuthorizationType";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_WECHAT_UIN = "X-WECHAT-UIN";
    private static final String HEADER_APP_ID = "iLink-App-Id";
    private static final String HEADER_APP_CLIENT_VERSION = "iLink-App-ClientVersion";
    private static final String HEADER_ROUTE_TAG = "SKRouteTag";
    private static final String HEADER_ENCRYPTED_PARAM = "x-encrypted-param";

    private static final String PARAM_BOT_TYPE = "bot_type";
    private static final String PARAM_QR_CODE = "qrcode";
    private static final String PARAM_VERIFY_CODE = "verify_code";
    private static final String PARAM_ENCRYPTED_QUERY_PARAM = "encrypted_query_param";
    private static final String PARAM_FILE_KEY = "filekey";

    private static final String PATH_GET_BOT_QR_CODE = "/ilink/bot/get_bot_qrcode";
    private static final String PATH_GET_QR_CODE_STATUS = "/ilink/bot/get_qrcode_status";
    private static final String PATH_GET_UPDATES = "/ilink/bot/getupdates";
    private static final String PATH_SEND_MESSAGE = "/ilink/bot/sendmessage";
    private static final String PATH_GET_CONFIG = "/ilink/bot/getconfig";
    private static final String PATH_SEND_TYPING = "/ilink/bot/sendtyping";
    private static final String PATH_NOTIFY_START = "/ilink/bot/msg/notifystart";
    private static final String PATH_NOTIFY_STOP = "/ilink/bot/msg/notifystop";
    private static final String PATH_GET_UPLOAD_URL = "/ilink/bot/getuploadurl";
    private static final String PATH_CDN_UPLOAD = "/upload";
    private static final String PATH_CDN_DOWNLOAD = "/download";

    private static final int HTTP_STATUS_OK = 200;
    private static final int HTTP_STATUS_SUCCESS_MIN = 200;
    private static final int HTTP_STATUS_SUCCESS_MAX_EXCLUSIVE = 300;

    private static final String MESSAGE_HTTP_REQUEST_FAILED = "HTTP request failed";
    private static final String MESSAGE_HTTP_REQUEST_INTERRUPTED = "HTTP request interrupted";
    private static final String MESSAGE_BUSINESS_REQUEST_FAILED = "Business request failed";
    private static final String MESSAGE_CDN_UPLOAD_FAILED = "CDN upload failed";
    private static final String MESSAGE_CDN_DOWNLOAD_FAILED = "CDN download failed";

    private final ILinkClientConfig config;
    private final HttpClient httpClient;
    private final JsonCodec jsonCodec;

    /**
     * Creates a client with default JSON codec and HTTP client.
     *
     * @param config client config
     */
    public ILinkClient(ILinkClientConfig config) {
        this(config,
                HttpClient.newBuilder()
                        .connectTimeout(config.getConnectTimeout())
                        .build(),
                new JacksonJsonCodec());
    }

    /**
     * Creates a client with provided dependencies.
     *
     * @param config     client config
     * @param httpClient JDK http client
     * @param jsonCodec  json codec
     */
    public ILinkClient(ILinkClientConfig config, HttpClient httpClient, JsonCodec jsonCodec) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec cannot be null");
    }

    /**
     * Calls the get_bot_qrcode endpoint.
     *
     * @return QR code payload
     */
    public QrCodeResponse getBotQrCode() {
        return getBotQrCode(List.of());
    }

    /**
     * Calls the get_bot_qrcode endpoint with locally persisted bot tokens.
     *
     * <p>The server uses these tokens to recognize a bot already bound to this
     * client. Tokens must be ordered from most recent to oldest; no more than ten
     * are sent.</p>
     *
     * @param localTokenList locally persisted bot tokens, or {@code null} when none exist
     * @return QR code payload
     */
    public QrCodeResponse getBotQrCode(List<String> localTokenList) {
        List<String> localTokens = normalizeLocalTokenList(localTokenList);
        LOG.debug("Requesting bot QR code, botType={}, localTokenCount={}", config.getBotType(), localTokens.size());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(newEndpointUriBuilder(config.getBaseUrl(), PATH_GET_BOT_QR_CODE)
                        .addParameter(PARAM_BOT_TYPE, String.valueOf(config.getBotType()))
                        .toString()))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION_TYPE, AUTHORIZATION_SCHEME_ILINK_BOT_TOKEN)
                .header(HEADER_WECHAT_UIN, WechatUinGenerator.randomWechatUin())
                .timeout(config.getRequestTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonCodec.toJson(new GetBotQrcodeRequest(localTokens)),
                        StandardCharsets.UTF_8
                ));
        HttpRequest request = withOptionalHeaders(builder).build();
        String body = executeTextRequest(request);
        return jsonCodec.fromJson(body, QrCodeResponse.class);
    }

    /**
     * Calls the get_qrcode_status endpoint using the configured base URL.
     *
     * @param qrCode QR polling token
     * @return QR status response
     */
    public QrCodeStatusResponse getQrCodeStatus(String qrCode) {
        return getQrCodeStatus(qrCode, config.getBaseUrl(), null);
    }

    /**
     * Calls the get_qrcode_status endpoint using a custom base URL.
     *
     * @param qrCode  QR polling token
     * @param baseUrl target base URL, used for redirect host handling
     * @return qr status response
     */
    public QrCodeStatusResponse getQrCodeStatus(String qrCode, String baseUrl) {
        return getQrCodeStatus(qrCode, baseUrl, null);
    }

    /**
     * Calls the get_qrcode_status endpoint using a custom base URL and an optional verification code.
     *
     * @param qrCode QR polling token
     * @param baseUrl target base URL, used for redirect host handling
     * @param verifyCode one-time verification code, or {@code null} when not required
     * @return qr status response
     */
    public QrCodeStatusResponse getQrCodeStatus(String qrCode, String baseUrl, String verifyCode) {
        requireNonBlank(qrCode, "qrCode");
        URIBuilder uriBuilder = newEndpointUriBuilder(baseUrl, PATH_GET_QR_CODE_STATUS)
                .addParameter(PARAM_QR_CODE, qrCode);
        if (verifyCode != null && !verifyCode.isBlank()) {
            uriBuilder.addParameter(PARAM_VERIFY_CODE, verifyCode);
        }
        LOG.debug("Polling QR code status, baseUrl={}", baseUrl);
        HttpRequest request = withOptionalHeaders(HttpRequest.newBuilder()
                .uri(URI.create(uriBuilder.toString()))
                .timeout(config.getRequestTimeout())
                .GET())
                .build();
        String body = executeTextRequest(request);
        return jsonCodec.fromJson(body, QrCodeStatusResponse.class);
    }

    /**
     * Creates auth session from confirmed qr status response.
     *
     * @param statusResponse qr status response
     * @return auth session
     */
    public ILinkAuthSession toAuthSession(QrCodeStatusResponse statusResponse) {
        Objects.requireNonNull(statusResponse, "statusResponse cannot be null");
        if (statusResponse.status() != QrCodeStatus.CONFIRMED) {
            throw new ILinkException("QR status is not confirmed: " + statusResponse.status());
        }
        requireNonBlank(statusResponse.botToken(), "bot_token");
        requireNonBlank(statusResponse.ilinkBotId(), "ilink_bot_id");
        requireNonBlank(statusResponse.ilinkUserId(), "ilink_user_id");
        String baseUrl = statusResponse.baseurl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = config.getBaseUrl();
        }
        LOG.info("Auth session confirmed, botId={}, userId={}, baseUrl={}",
                statusResponse.ilinkBotId(), statusResponse.ilinkUserId(), baseUrl);
        return new ILinkAuthSession(
                statusResponse.botToken(),
                baseUrl,
                statusResponse.ilinkBotId(),
                statusResponse.ilinkUserId()
        );
    }

    /**
     * Calls getupdates with current cursor.
     *
     * @param session       auth session
     * @param getUpdatesBuf opaque cursor, empty string for first call
     * @return updates response
     */
    public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf) {
        return getUpdates(session, getUpdatesBuf, config.getLongPollingTimeout());
    }

    /**
     * Calls getupdates with current cursor and custom timeout for this request.
     *
     * @param session       auth session
     * @param getUpdatesBuf opaque cursor, empty string for first call
     * @param timeout       request timeout for this call
     * @return updates response
     */
    public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf, Duration timeout) {
        Objects.requireNonNull(session, "session cannot be null");
        GetUpdatesRequest request = new GetUpdatesRequest(
                getUpdatesBuf == null ? "" : getUpdatesBuf,
                BaseInfo.of(config.getChannelVersion(), config.getBotAgent()),
                null
        );
        GetUpdatesResponse response = postBusiness(
                session,
                PATH_GET_UPDATES,
                request,
                timeout,
                GetUpdatesResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg());
        return response;
    }

    /**
     * Sends long text by splitting to multiple FINISH text messages.
     *
     * @param session        auth session
     * @param toUserId       target user id
     * @param contextToken   conversation context token
     * @param text           text content
     * @param clientIdPrefix client id prefix
     * @return send responses in order
     */
    public List<SendMessageResponse> sendText(
            ILinkAuthSession session,
            String toUserId,
            String contextToken,
            String text,
            String clientIdPrefix
    ) {
        Objects.requireNonNull(session, "session cannot be null");
        requireNonBlank(toUserId, "toUserId");
        requireNonBlank(contextToken, "contextToken");
        requireNonBlank(text, "text");

        List<String> chunks = TextChunker.split(text);
        LOG.info("Sending text message in {} chunk(s), toUserId={}", chunks.size(), toUserId);
        List<SendMessageResponse> responses = new ArrayList<>(chunks.size());
        List<SentTextChunk> sentChunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            LOG.debug("Sending text chunk {}/{}, length={}", i + 1, chunks.size(), chunk.length());
            MessageItem item = new TextMessageItem(new TextItem(chunk));
            String clientId = ClientIdGenerator.generate(clientIdPrefix);
            OutboundMessage msg = OutboundMessage.botFinish(
                    toUserId,
                    clientId,
                    List.of(item),
                    contextToken
            );
            try {
                SendMessageResponse response = sendMessage(session, msg);
                responses.add(response);
                sentChunks.add(new SentTextChunk(i, clientId, response));
            } catch (ILinkException e) {
                if (sentChunks.isEmpty()) {
                    throw e;
                }
                throw new PartialTextSendException(
                        "Text message delivery failed after " + sentChunks.size() + " of " + chunks.size() + " chunk(s)",
                        sentChunks,
                        e
                );
            }
        }
        return responses;
    }

    /**
     * Sends a prepared message body.
     *
     * @param session auth session
     * @param msg     message payload
     * @return send response body
     */
    public SendMessageResponse sendMessage(ILinkAuthSession session, OutboundMessage msg) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(msg, "msg cannot be null");
        SendMessageRequest request = new SendMessageRequest(
                msg,
                BaseInfo.of(config.getChannelVersion(), config.getBotAgent())
        );
        SendMessageResponse response = postBusiness(
                session,
                PATH_SEND_MESSAGE,
                request,
                config.getRequestTimeout(),
                SendMessageResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg());
        return response;
    }

    /**
     * Gets the typing configuration for one target user.
     *
     * @param session      auth session
     * @param ilinkUserId  target user id
     * @param contextToken context token
     * @return getconfig response
     */
    public GetConfigResponse getTypingConfig(ILinkAuthSession session, String ilinkUserId, String contextToken) {
        Objects.requireNonNull(session, "session cannot be null");
        requireNonBlank(ilinkUserId, "ilinkUserId");
        GetConfigRequest request = new GetConfigRequest(
                ilinkUserId,
                contextToken,
                BaseInfo.of(config.getChannelVersion(), config.getBotAgent())
        );
        GetConfigResponse response = postBusiness(
                session,
                PATH_GET_CONFIG,
                request,
                config.getRequestTimeout(),
                GetConfigResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg());
        return response;
    }

    /**
     * Sends typing status to one target user.
     *
     * @param session      auth session
     * @param ilinkUserId  target user id
     * @param typingTicket typing ticket
     * @param status       typing status, see {@link TypingStatus}
     * @return sendtyping response
     */
    public SendTypingResponse sendTyping(
            ILinkAuthSession session,
            String ilinkUserId,
            String typingTicket,
            TypingStatus status
    ) {
        Objects.requireNonNull(session, "session cannot be null");
        requireNonBlank(ilinkUserId, "ilinkUserId");
        requireNonBlank(typingTicket, "typingTicket");
        Objects.requireNonNull(status, "status cannot be null");

        SendTypingRequest request = new SendTypingRequest(
                ilinkUserId,
                typingTicket,
                status,
                BaseInfo.of(config.getChannelVersion(), config.getBotAgent())
        );
        SendTypingResponse response = postBusiness(
                session,
                PATH_SEND_TYPING,
                request,
                config.getRequestTimeout(),
                SendTypingResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg());
        return response;
    }

    /**
     * Notifies the backend that this client has started.
     *
     * @param session auth session
     */
    void notifyStart(ILinkAuthSession session) {
        notify(session, PATH_NOTIFY_START);
    }

    /**
     * Notifies the backend that this client has stopped.
     *
     * @param session auth session
     */
    void notifyStop(ILinkAuthSession session) {
        notify(session, PATH_NOTIFY_STOP);
    }

    /**
     * Uploads encrypted media to CDN.
     *
     * @param uploadFullUrl  full upload URL from getuploadurl, may be empty
     * @param uploadParam    encrypted query parameter fallback
     * @param fileKey        upload file key
     * @param encryptedBytes encrypted payload bytes
     * @return upload result with x-encrypted-param header
     */
    public CdnUploadResult uploadEncryptedMedia(
            String uploadFullUrl,
            String uploadParam,
            String fileKey,
            byte[] encryptedBytes
    ) {
        requireNonBlank(fileKey, "fileKey");
        Objects.requireNonNull(encryptedBytes, "encryptedBytes cannot be null");

        URI target;
        if (uploadFullUrl != null && !uploadFullUrl.isBlank()) {
            target = requireTrustedCdnUri(uploadFullUrl);
        } else {
            requireNonBlank(uploadParam, "uploadParam");
            target = URI.create(newEndpointUriBuilder(config.getCdnBaseUrl(), PATH_CDN_UPLOAD)
                    .addParameter(PARAM_ENCRYPTED_QUERY_PARAM, uploadParam)
                    .addParameter(PARAM_FILE_KEY, fileKey)
                    .toString());
        }

        LOG.info("Uploading encrypted media to CDN, payloadSize={} bytes", encryptedBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(target)
                .timeout(config.getRequestTimeout())
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_OCTET_STREAM)
                .POST(HttpRequest.BodyPublishers.ofByteArray(encryptedBytes))
                .build();

        ResponseData response = executeByteArrayRequest(request);
        assertHttpSuccess(response.statusCode(), MESSAGE_CDN_UPLOAD_FAILED);
        String encryptedParam = response.headers().firstValue(HEADER_ENCRYPTED_PARAM).orElse(null);
        LOG.debug("CDN upload succeeded, status={}, hasEncryptedParam={}",
                response.statusCode(), encryptedParam != null && !encryptedParam.isBlank());
        return new CdnUploadResult(response.statusCode(), encryptedParam);
    }

    /**
     * Downloads encrypted media from CDN.
     *
     * @param media CDN media reference
     * @return encrypted bytes from CDN
     */
    public byte[] downloadEncryptedMedia(CDNMedia media) {
        return downloadEncryptedMediaResponse(media).body();
    }

    /**
     * Calls getuploadurl for media upload parameters.
     *
     * @param session auth session
     * @param request upload-url request body
     * @return getuploadurl response
     */
    public GetUploadUrlResponse getUploadUrl(ILinkAuthSession session, GetUploadUrlRequest request) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        GetUploadUrlResponse response = postBusiness(
                session,
                PATH_GET_UPLOAD_URL,
                request,
                config.getRequestTimeout(),
                GetUploadUrlResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg());
        return response;
    }

    private ResponseData downloadEncryptedMediaResponse(CDNMedia media) {
        Objects.requireNonNull(media, "media cannot be null");

        URI target;
        if (media.fullUrl() != null && !media.fullUrl().isBlank()) {
            target = requireTrustedCdnUri(media.fullUrl());
        } else {
            requireNonBlank(media.encryptQueryParam(), "media.encryptQueryParam");
            target = URI.create(newEndpointUriBuilder(config.getCdnBaseUrl(), PATH_CDN_DOWNLOAD)
                    .addParameter(PARAM_ENCRYPTED_QUERY_PARAM, media.encryptQueryParam())
                    .toString());
        }

        LOG.debug("Downloading encrypted media from CDN");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(target)
                .timeout(config.getRequestTimeout())
                .GET()
                .build();
        ResponseData response = executeByteArrayRequest(request, config.getMaxMediaDownloadBytes());
        assertHttpSuccess(response.statusCode(), MESSAGE_CDN_DOWNLOAD_FAILED);
        LOG.debug("CDN download succeeded, status={}, size={} bytes", response.statusCode(), response.body().length);
        return response;
    }

    private static byte[] resolveMediaKey(CDNMedia media, String imageAesKeyHex) {
        if (imageAesKeyHex != null && !imageAesKeyHex.isBlank()) {
            return HexUtils.fromHex(imageAesKeyHex);
        }
        requireNonBlank(media.aesKey(), "media.aesKey");
        return CryptoUtils.decodeCompatibleAesKey(media.aesKey());
    }

    /**
     * Encrypts plaintext media content for CDN upload.
     *
     * @param plaintext plaintext bytes
     * @param aesKeyHex 32-char hex key
     * @return encrypted bytes
     */
    public byte[] encryptMedia(byte[] plaintext, String aesKeyHex) {
        Objects.requireNonNull(plaintext, "plaintext cannot be null");
        requireNonBlank(aesKeyHex, "aesKeyHex");
        return CryptoUtils.encryptAesEcb(plaintext, HexUtils.fromHex(aesKeyHex));
    }

    /**
     * Downloads and decrypts media, preserving the response Content-Type.
     *
     * <p>For image messages, pass the hexadecimal value of image_item.aeskey to
     * decrypt with that key. For other media types, pass {@code null} to use
     * media.aes_key.</p>
     *
     * @param media          CDN media reference
     * @param imageAesKeyHex hexadecimal image_item.aeskey, or {@code null}
     * @return decrypted content and optional Content-Type
     */
    public DownloadedMedia downloadAndDecryptMedia(CDNMedia media, String imageAesKeyHex) {
        ResponseData response = downloadEncryptedMediaResponse(media);
        byte[] key = resolveMediaKey(media, imageAesKeyHex);
        byte[] plaintext = CryptoUtils.decryptAesEcb(response.body(), key);
        String contentType = response.headers().firstValue(HEADER_CONTENT_TYPE).orElse(null);
        return new DownloadedMedia(plaintext, contentType);
    }

    private HttpRequest.Builder withOptionalHeaders(HttpRequest.Builder builder) {
        if (config.getAppId() != null && !config.getAppId().isBlank()) {
            builder.header(HEADER_APP_ID, config.getAppId());
        }
        if (config.getAppClientVersion() != null && !config.getAppClientVersion().isBlank()) {
            builder.header(HEADER_APP_CLIENT_VERSION, config.getAppClientVersion());
        }
        if (config.getRouteTag() != null && !config.getRouteTag().isBlank()) {
            builder.header(HEADER_ROUTE_TAG, config.getRouteTag());
        }
        return builder;
    }

    private void notify(ILinkAuthSession session, String path) {
        Objects.requireNonNull(session, "session cannot be null");
        NotifyResponse response = postBusiness(
                session,
                path,
                new NotifyRequest(BaseInfo.of(config.getChannelVersion(), config.getBotAgent())),
                config.getRequestTimeout(),
                NotifyResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg());
    }

    private ResponseData executeByteArrayRequest(HttpRequest request) {
        return executeByteArrayRequest(request, Long.MAX_VALUE);
    }

    private ResponseData executeByteArrayRequest(HttpRequest request, long maxResponseBytes) {
        String path = request.uri().getPath();
        LOG.debug("Executing HTTP request: {} {}", request.method(), path);
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > maxResponseBytes) {
                try (InputStream body = response.body()) {
                    throw new ILinkException("HTTP response exceeds maximum size of " + maxResponseBytes + " bytes");
                }
            }
            byte[] responseBody;
            try (InputStream body = response.body()) {
                responseBody = readResponseBody(body, maxResponseBytes);
            }
            LOG.debug("HTTP response received: {} {}, status={}",
                    request.method(), path, response.statusCode());
            return new ResponseData(response.statusCode(), response.headers(), responseBody);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("HTTP request interrupted: {} {}", request.method(), path);
            throw new ILinkException(MESSAGE_HTTP_REQUEST_INTERRUPTED, e);
        } catch (IOException e) {
            LOG.error("HTTP request failed: {} {}", request.method(), path, e);
            throw new ILinkException(MESSAGE_HTTP_REQUEST_FAILED, e);
        }
    }

    private static byte[] readResponseBody(InputStream body, long maxResponseBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long totalBytes = 0;
        int bytesRead;
        while ((bytesRead = body.read(buffer)) != -1) {
            if (totalBytes > maxResponseBytes - bytesRead) {
                throw new ILinkException("HTTP response exceeds maximum size of " + maxResponseBytes + " bytes");
            }
            output.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }
        return output.toByteArray();
    }

    private URI requireTrustedCdnUri(String fullUrl) {
        URI target = URI.create(fullUrl);
        URI configuredCdn = URI.create(config.getCdnBaseUrl());
        if (!"https".equalsIgnoreCase(target.getScheme())
                || target.getUserInfo() != null
                || target.getHost() == null
                || configuredCdn.getHost() == null
                || !target.getHost().equalsIgnoreCase(configuredCdn.getHost())) {
            throw new ILinkException("full CDN URL must use HTTPS and match the configured CDN host");
        }
        return target;
    }

    private String executeTextRequest(HttpRequest request) {
        ResponseData response = executeByteArrayRequest(request);
        assertHttpSuccess(response.statusCode(), MESSAGE_HTTP_REQUEST_FAILED);
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static URI buildEndpointUri(String baseUrl, String path) {
        requireNonBlank(baseUrl, "baseUrl");
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    private static URIBuilder newEndpointUriBuilder(String baseUrl, String path) {
        requireNonBlank(baseUrl, "baseUrl");
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new URIBuilder(URI.create(normalizedBaseUrl)).appendPath(path);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ILinkException(field + " cannot be null or blank");
        }
    }

    private static void assertHttpSuccess(int statusCode, String message) {
        if (statusCode < HTTP_STATUS_SUCCESS_MIN || statusCode >= HTTP_STATUS_SUCCESS_MAX_EXCLUSIVE) {
            LOG.warn("HTTP status indicates failure, status={}, message={}", statusCode, message);
            throw new ILinkProtocolException(message + ", status=" + statusCode, null, null, statusCode);
        }
    }

    private static List<String> normalizeLocalTokenList(List<String> localTokenList) {
        if (localTokenList == null || localTokenList.isEmpty()) {
            return List.of();
        }
        List<String> localTokens = new ArrayList<>(Math.min(localTokenList.size(), 10));
        for (String token : localTokenList) {
            if (token == null || token.isBlank()) {
                continue;
            }
            localTokens.add(token);
            if (localTokens.size() == 10) {
                break;
            }
        }
        return List.copyOf(localTokens);
    }

    private <T> T postBusiness(
            ILinkAuthSession session,
            String path,
            Object payload,
            Duration timeout,
            Class<T> responseType
    ) {
        requireNonBlank(session.token(), "token");
        String json = jsonCodec.toJson(payload);
        Duration effectiveTimeout = timeout == null ? config.getRequestTimeout() : timeout;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(buildEndpointUri(session.baseUrl(), path))
                .timeout(effectiveTimeout)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION_TYPE, AUTHORIZATION_SCHEME_ILINK_BOT_TOKEN)
                .header(HEADER_AUTHORIZATION, AUTHORIZATION_BEARER_PREFIX + session.token())
                .header(HEADER_WECHAT_UIN, WechatUinGenerator.randomWechatUin())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

        withOptionalHeaders(builder);

        LOG.debug("Sending business request, path={}, timeoutMs={}", path, effectiveTimeout.toMillis());
        ResponseData response = executeByteArrayRequest(builder.build());
        assertHttpSuccess(response.statusCode(), MESSAGE_BUSINESS_REQUEST_FAILED);
        return jsonCodec.fromJson(new String(response.body(), StandardCharsets.UTF_8), responseType);
    }

    private static void assertBusinessSuccess(Integer ret, Integer errcode, String errmsg) {
        if (ret == null && errcode == null) {
            throw new ILinkProtocolException(
                    "Business response does not contain ret or errcode",
                    null,
                    null,
                    HTTP_STATUS_OK
            );
        }
        boolean retFail = ret != null && ret != BusinessCode.OK.code();
        boolean errFail = errcode != null && errcode != BusinessCode.OK.code();
        if (!retFail && !errFail) {
            return;
        }
        Integer effectiveRet = ret != null ? ret : errcode;
        Integer effectiveErr = errcode != null ? errcode : ret;
        String message = errmsg == null || errmsg.isBlank() ? MESSAGE_BUSINESS_REQUEST_FAILED : errmsg;

        boolean sessionExpired = BusinessCode.SESSION_EXPIRED.code() == effectiveRet
                || BusinessCode.SESSION_EXPIRED.code() == effectiveErr;
        if (sessionExpired) {
            LOG.warn("Business request session expired, ret={}, errcode={}", effectiveRet, effectiveErr);
            throw new SessionExpiredException(message, effectiveRet, effectiveErr, HTTP_STATUS_OK);
        }
        LOG.warn("Business request failed, ret={}, errcode={}, errmsg={}", effectiveRet, effectiveErr, message);
        throw new ILinkProtocolException(message, effectiveRet, effectiveErr, HTTP_STATUS_OK);
    }

    private record ResponseData(int statusCode, HttpHeaders headers, byte[] body) {
    }

    /**
     * JDK HTTP client does not require explicit close.
     */
    @Override
    public void close() {
        LOG.debug("ILinkClient closed");
    }

}
