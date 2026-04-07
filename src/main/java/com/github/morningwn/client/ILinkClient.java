package com.github.morningwn.client;

import com.github.morningwn.codec.JacksonJsonCodec;
import com.github.morningwn.codec.JsonCodec;
import com.github.morningwn.exception.ILinkException;
import com.github.morningwn.exception.ILinkProtocolException;
import com.github.morningwn.exception.SessionExpiredException;
import com.github.morningwn.protocol.BaseInfo;
import com.github.morningwn.protocol.CDNMedia;
import com.github.morningwn.protocol.GetConfigRequest;
import com.github.morningwn.protocol.GetConfigResponse;
import com.github.morningwn.protocol.GetUpdatesRequest;
import com.github.morningwn.protocol.GetUpdatesResponse;
import com.github.morningwn.protocol.GetUploadUrlRequest;
import com.github.morningwn.protocol.GetUploadUrlResponse;
import com.github.morningwn.protocol.MessageItem;
import com.github.morningwn.protocol.ProtocolValues;
import com.github.morningwn.protocol.QrCodeResponse;
import com.github.morningwn.protocol.QrCodeStatusResponse;
import com.github.morningwn.protocol.SendMessageRequest;
import com.github.morningwn.protocol.SendMessageResponse;
import com.github.morningwn.protocol.SendTypingRequest;
import com.github.morningwn.protocol.SendTypingResponse;
import com.github.morningwn.protocol.TextItem;
import com.github.morningwn.protocol.WeixinMessage;
import com.github.morningwn.util.ClientIdGenerator;
import com.github.morningwn.util.CryptoUtils;
import com.github.morningwn.util.HexUtils;
import com.github.morningwn.util.TextChunker;
import com.github.morningwn.util.WechatUinGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
    private static final String AUTHORIZATION_TYPE = "ilink_bot_token";
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
    private static final String PARAM_QRCODE = "qrcode";
    private static final String PARAM_ENCRYPTED_QUERY_PARAM = "encrypted_query_param";
    private static final String PARAM_FILE_KEY = "filekey";

    private static final String PATH_GET_BOT_QRCODE = "/ilink/bot/get_bot_qrcode";
    private static final String PATH_GET_QRCODE_STATUS = "/ilink/bot/get_qrcode_status";
    private static final String PATH_GET_UPDATES = "/ilink/bot/getupdates";
    private static final String PATH_SEND_MESSAGE = "/ilink/bot/sendmessage";
    private static final String PATH_GET_CONFIG = "/ilink/bot/getconfig";
    private static final String PATH_SEND_TYPING = "/ilink/bot/sendtyping";
    private static final String PATH_GET_UPLOAD_URL = "/ilink/bot/getuploadurl";
    private static final String PATH_CDN_UPLOAD = "/upload";
    private static final String PATH_CDN_DOWNLOAD = "/download";

    private static final String QUERY_SEPARATOR = "?";
    private static final String QUERY_ASSIGN = "=";
    private static final String QUERY_AND = "&";

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
     * @param config client config
     * @param httpClient JDK http client
     * @param jsonCodec json codec
     */
    public ILinkClient(ILinkClientConfig config, HttpClient httpClient, JsonCodec jsonCodec) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec cannot be null");
    }

    /**
     * Calls get_bot_qrcode endpoint.
     *
     * @return qr code payload
     */
    public QrCodeResponse getBotQrcode() {
        String path = PATH_GET_BOT_QRCODE + QUERY_SEPARATOR + PARAM_BOT_TYPE + QUERY_ASSIGN + config.getBotType();
        LOG.debug("Requesting bot qrcode, botType={}", config.getBotType());
        HttpRequest request = withLoginHeaders(HttpRequest.newBuilder()
            .uri(URI.create(buildUrl(config.getBaseUrl(), path)))
            .timeout(config.getRequestTimeout())
            .GET())
                .build();
        String body = sendText(request);
        return jsonCodec.fromJson(body, QrCodeResponse.class);
    }

    /**
     * Calls get_qrcode_status endpoint using configured base URL.
     *
     * @param qrcode qr polling token
     * @return qr status response
     */
    public QrCodeStatusResponse getQrcodeStatus(String qrcode) {
        return getQrcodeStatus(qrcode, config.getBaseUrl());
    }

    /**
     * Calls get_qrcode_status endpoint using custom base URL.
     *
     * @param qrcode qr polling token
     * @param baseUrl target base URL, used for redirect host handling
     * @return qr status response
     */
    public QrCodeStatusResponse getQrcodeStatus(String qrcode, String baseUrl) {
        requireNonBlank(qrcode, "qrcode");
        String path = PATH_GET_QRCODE_STATUS + QUERY_SEPARATOR + PARAM_QRCODE + QUERY_ASSIGN + urlEncode(qrcode);
        LOG.debug("Polling qrcode status, baseUrl={}", baseUrl);
        HttpRequest request = withLoginHeaders(HttpRequest.newBuilder()
            .uri(URI.create(buildUrl(baseUrl, path)))
            .timeout(config.getRequestTimeout())
            .GET())
                .build();
        String body = sendText(request);
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
        if (!ProtocolValues.QR_STATUS_CONFIRMED.equals(statusResponse.status())) {
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
     * @param session auth session
     * @param getUpdatesBuf opaque cursor, empty string for first call
     * @return updates response
     */
    public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf) {
        Objects.requireNonNull(session, "session cannot be null");
        GetUpdatesRequest request = new GetUpdatesRequest(
                getUpdatesBuf == null ? "" : getUpdatesBuf,
                BaseInfo.of(config.getChannelVersion()),
                null
        );
        GetUpdatesResponse response = postBusiness(
                session.baseUrl(),
                PATH_GET_UPDATES,
                session.token(),
                request,
                config.getLongPollingTimeout(),
                GetUpdatesResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), HTTP_STATUS_OK);
        return response;
    }

    /**
     * Sends a prepared message body.
     *
     * @param session auth session
     * @param msg message payload
     * @return send response body
     */
    public SendMessageResponse sendMessage(ILinkAuthSession session, WeixinMessage msg) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(msg, "msg cannot be null");
        SendMessageRequest request = new SendMessageRequest(msg, BaseInfo.of(config.getChannelVersion()));
        SendMessageResponse response = postBusiness(
                session.baseUrl(),
                PATH_SEND_MESSAGE,
                session.token(),
                request,
                config.getRequestTimeout(),
                SendMessageResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), HTTP_STATUS_OK);
        return response;
    }

    /**
     * Sends long text by splitting to multiple FINISH text messages.
     *
     * @param session auth session
     * @param toUserId target user id
     * @param contextToken conversation context token
     * @param text text content
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
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            LOG.debug("Sending text chunk {}/{}, length={}", i + 1, chunks.size(), chunk.length());
            MessageItem item = new MessageItem(
                    ProtocolValues.ITEM_TYPE_TEXT,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new TextItem(chunk),
                    null,
                    null,
                    null,
                    null
            );
            WeixinMessage msg = new WeixinMessage(
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
            responses.add(sendMessage(session, msg));
        }
        return responses;
    }

    /**
     * Gets typing ticket for one target user.
     *
     * @param session auth session
     * @param ilinkUserId target user id
     * @param contextToken context token
     * @return getconfig response
     */
    public GetConfigResponse getConfig(ILinkAuthSession session, String ilinkUserId, String contextToken) {
        Objects.requireNonNull(session, "session cannot be null");
        requireNonBlank(ilinkUserId, "ilinkUserId");
        GetConfigRequest request = new GetConfigRequest(
                ilinkUserId,
                contextToken,
                BaseInfo.of(config.getChannelVersion())
        );
        GetConfigResponse response = postBusiness(
                session.baseUrl(),
                PATH_GET_CONFIG,
                session.token(),
                request,
                config.getRequestTimeout(),
                GetConfigResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), HTTP_STATUS_OK);
        return response;
    }

    /**
     * Sends typing status to one target user.
     *
     * @param session auth session
     * @param ilinkUserId target user id
     * @param typingTicket typing ticket
     * @param status 1 start, 2 stop
     * @return sendtyping response
     */
    public SendTypingResponse sendTyping(
            ILinkAuthSession session,
            String ilinkUserId,
            String typingTicket,
            int status
    ) {
        Objects.requireNonNull(session, "session cannot be null");
        requireNonBlank(ilinkUserId, "ilinkUserId");
        requireNonBlank(typingTicket, "typingTicket");
        if (status != ProtocolValues.TYPING_STATUS_START && status != ProtocolValues.TYPING_STATUS_STOP) {
            throw new ILinkException("typing status must be 1(start) or 2(stop)");
        }

        SendTypingRequest request = new SendTypingRequest(
                ilinkUserId,
                typingTicket,
                status,
                BaseInfo.of(config.getChannelVersion())
        );
        SendTypingResponse response = postBusiness(
                session.baseUrl(),
                PATH_SEND_TYPING,
                session.token(),
                request,
                config.getRequestTimeout(),
                SendTypingResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), HTTP_STATUS_OK);
        return response;
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
                session.baseUrl(),
                PATH_GET_UPLOAD_URL,
                session.token(),
                request,
                config.getRequestTimeout(),
                GetUploadUrlResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), HTTP_STATUS_OK);
        return response;
    }

    /**
     * Uploads encrypted media to CDN.
     *
     * @param uploadFullUrl full upload URL from getuploadurl, may be empty
     * @param uploadParam encrypted query parameter fallback
     * @param fileKey upload file key
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

        String target;
        if (uploadFullUrl != null && !uploadFullUrl.isBlank()) {
            target = uploadFullUrl;
        } else {
            requireNonBlank(uploadParam, "uploadParam");
            target = config.getCdnBaseUrl()
                    + PATH_CDN_UPLOAD + QUERY_SEPARATOR + PARAM_ENCRYPTED_QUERY_PARAM + QUERY_ASSIGN + urlEncode(uploadParam)
                    + QUERY_AND + PARAM_FILE_KEY + QUERY_ASSIGN + urlEncode(fileKey);
        }

        LOG.info("Uploading encrypted media to CDN, payloadSize={} bytes", encryptedBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(target))
            .timeout(config.getRequestTimeout())
            .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_OCTET_STREAM)
            .POST(HttpRequest.BodyPublishers.ofByteArray(encryptedBytes))
                .build();

        RawHttpResponse response = sendResponse(request);
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
        Objects.requireNonNull(media, "media cannot be null");

        String target;
        if (media.fullUrl() != null && !media.fullUrl().isBlank()) {
            target = media.fullUrl();
        } else {
            requireNonBlank(media.encryptQueryParam(), "media.encryptQueryParam");
            target = config.getCdnBaseUrl()
                    + PATH_CDN_DOWNLOAD + QUERY_SEPARATOR + PARAM_ENCRYPTED_QUERY_PARAM + QUERY_ASSIGN + urlEncode(media.encryptQueryParam());
        }

        LOG.debug("Downloading encrypted media from CDN");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(target))
            .timeout(config.getRequestTimeout())
            .GET()
                .build();
        RawHttpResponse response = sendResponse(request);
        assertHttpSuccess(response.statusCode(), MESSAGE_CDN_DOWNLOAD_FAILED);
        LOG.debug("CDN download succeeded, status={}, size={} bytes",
            response.statusCode(), response.body().length);
        return response.body();
    }

    /**
     * Downloads and decrypts media using key priority from spec.
     *
     * @param media CDN media reference
     * @param imageAesKeyHex optional image_item.aeskey in hex form
     * @return plaintext bytes
     */
    public byte[] downloadAndDecryptMedia(CDNMedia media, String imageAesKeyHex) {
        byte[] encrypted = downloadEncryptedMedia(media);
        byte[] key;
        if (imageAesKeyHex != null && !imageAesKeyHex.isBlank()) {
            key = HexUtils.fromHex(imageAesKeyHex);
        } else {
            requireNonBlank(media.aesKey(), "media.aesKey");
            key = CryptoUtils.decodeCompatibleAesKey(media.aesKey());
        }
        return CryptoUtils.decryptAesEcb(encrypted, key);
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

    private <T> T postBusiness(
            String baseUrl,
            String path,
            String token,
            Object payload,
            Duration timeout,
            Class<T> responseType
    ) {
        requireNonBlank(token, "token");
        String json = jsonCodec.toJson(payload);
        Duration effectiveTimeout = timeout == null ? config.getRequestTimeout() : timeout;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(baseUrl, path)))
                .timeout(effectiveTimeout)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION_TYPE, AUTHORIZATION_TYPE)
                .header(HEADER_AUTHORIZATION, AUTHORIZATION_BEARER_PREFIX + token)
                .header(HEADER_WECHAT_UIN, WechatUinGenerator.randomWechatUin())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

        withOptionalHeaders(builder);

        LOG.debug("Sending business request, path={}, timeoutMs={}", path, effectiveTimeout.toMillis());
        RawHttpResponse response = sendResponse(builder.build());
        assertHttpSuccess(response.statusCode(), MESSAGE_BUSINESS_REQUEST_FAILED);
        return jsonCodec.fromJson(response.bodyText(), responseType);
    }

    private HttpRequest.Builder withLoginHeaders(HttpRequest.Builder builder) {
        return withOptionalHeaders(builder);
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

    private RawHttpResponse sendResponse(HttpRequest request) {
        String path = request.uri().getPath();
        LOG.debug("Executing HTTP request: {} {}", request.method(), path);
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            LOG.debug("HTTP response received: {} {}, status={}",
                    request.method(), path, response.statusCode());
            return new RawHttpResponse(response.statusCode(), response.headers(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("HTTP request interrupted: {} {}", request.method(), path, e);
            throw new ILinkException(MESSAGE_HTTP_REQUEST_INTERRUPTED, e);
        } catch (IOException e) {
            LOG.error("HTTP request failed: {} {}", request.method(), path, e);
            throw new ILinkException(MESSAGE_HTTP_REQUEST_FAILED, e);
        }
    }

    private String sendText(HttpRequest request) {
        RawHttpResponse response = sendResponse(request);
        assertHttpSuccess(response.statusCode(), MESSAGE_HTTP_REQUEST_FAILED);
        return response.bodyText();
    }

    private String buildUrl(String baseUrl, String path) {
        requireNonBlank(baseUrl, "baseUrl");
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private static void assertBusinessSuccess(Integer ret, Integer errcode, String errmsg, int statusCode) {
        boolean retFail = ret != null && ret != ProtocolValues.RET_OK;
        boolean errFail = errcode != null && errcode != ProtocolValues.RET_OK;
        if (!retFail && !errFail) {
            return;
        }
        Integer effectiveRet = ret != null ? ret : errcode;
        Integer effectiveErr = errcode != null ? errcode : ret;
        String message = errmsg == null || errmsg.isBlank() ? MESSAGE_BUSINESS_REQUEST_FAILED : errmsg;

        boolean sessionExpired = ProtocolValues.RET_SESSION_EXPIRED == effectiveRet
                || ProtocolValues.RET_SESSION_EXPIRED == effectiveErr;
        if (sessionExpired) {
            LOG.warn("Business request session expired, ret={}, errcode={}, status={}",
                effectiveRet, effectiveErr, statusCode);
            throw new SessionExpiredException(message, effectiveRet, effectiveErr, statusCode);
        }
        LOG.warn("Business request failed, ret={}, errcode={}, status={}, errmsg={}",
            effectiveRet, effectiveErr, statusCode, message);
        throw new ILinkProtocolException(message, effectiveRet, effectiveErr, statusCode);
    }

    /**
     * JDK HTTP client does not require explicit close.
     */
    @Override
    public void close() {
        LOG.debug("ILinkClient closed");
    }

    private record RawHttpResponse(int statusCode, HttpHeaders headers, byte[] body) {

        private String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
