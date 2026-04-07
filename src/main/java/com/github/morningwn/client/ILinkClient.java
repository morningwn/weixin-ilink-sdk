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
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Core HTTP client for WeChat iLink Bot API.
 */
public class ILinkClient {

    private static final Logger LOG = LoggerFactory.getLogger(ILinkClient.class);

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String AUTHORIZATION_TYPE = "ilink_bot_token";

    private final ILinkClientConfig config;
    private final AsyncHttpClient httpClient;
    private final JsonCodec jsonCodec;

    /**
     * Creates a client with default JSON codec and HTTP client.
     *
     * @param config client config
     */
    public ILinkClient(ILinkClientConfig config) {
        this(config,
            Dsl.asyncHttpClient(Dsl.config()
                .setConnectTimeout(config.getConnectTimeout())
                .setReadTimeout(config.getRequestTimeout())
                .setRequestTimeout(config.getRequestTimeout())),
                new JacksonJsonCodec());
    }

    /**
     * Creates a client with provided dependencies.
     *
     * @param config client config
     * @param httpClient async http client
     * @param jsonCodec json codec
     */
    public ILinkClient(ILinkClientConfig config, AsyncHttpClient httpClient, JsonCodec jsonCodec) {
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
        String path = "/ilink/bot/get_bot_qrcode?bot_type=" + config.getBotType();
        LOG.debug("Requesting bot qrcode, botType={}", config.getBotType());
        Request request = withLoginHeaders(new RequestBuilder("GET")
            .setUrl(buildUrl(config.getBaseUrl(), path))
            .setRequestTimeout(config.getRequestTimeout()))
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
        String path = "/ilink/bot/get_qrcode_status?qrcode=" + urlEncode(qrcode);
        LOG.debug("Polling qrcode status, baseUrl={}", baseUrl);
        Request request = withLoginHeaders(new RequestBuilder("GET")
            .setUrl(buildUrl(baseUrl, path))
            .setRequestTimeout(config.getRequestTimeout()))
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
                "/ilink/bot/getupdates",
                session.token(),
                request,
                config.getLongPollingTimeout(),
                GetUpdatesResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), 200);
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
                "/ilink/bot/sendmessage",
                session.token(),
                request,
                config.getRequestTimeout(),
                SendMessageResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), 200);
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
                "/ilink/bot/getconfig",
                session.token(),
                request,
                config.getRequestTimeout(),
                GetConfigResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), 200);
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
                "/ilink/bot/sendtyping",
                session.token(),
                request,
                config.getRequestTimeout(),
                SendTypingResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), 200);
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
                "/ilink/bot/getuploadurl",
                session.token(),
                request,
                config.getRequestTimeout(),
                GetUploadUrlResponse.class
        );
        assertBusinessSuccess(response.ret(), response.errcode(), response.errmsg(), 200);
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
                    + "/upload?encrypted_query_param=" + urlEncode(uploadParam)
                    + "&filekey=" + urlEncode(fileKey);
        }

        LOG.info("Uploading encrypted media to CDN, payloadSize={} bytes", encryptedBytes.length);

        Request request = new RequestBuilder("POST")
            .setUrl(target)
            .setHeader("Content-Type", CONTENT_TYPE_OCTET_STREAM)
            .setBody(encryptedBytes)
            .setRequestTimeout(config.getRequestTimeout())
                .build();

        Response response = sendResponse(request);
        assertHttpSuccess(response.getStatusCode(), "CDN upload failed");
        String encryptedParam = response.getHeader("x-encrypted-param");
        LOG.debug("CDN upload succeeded, status={}, hasEncryptedParam={}",
            response.getStatusCode(), encryptedParam != null && !encryptedParam.isBlank());
        return new CdnUploadResult(response.getStatusCode(), encryptedParam);
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
                    + "/download?encrypted_query_param=" + urlEncode(media.encryptQueryParam());
        }

        LOG.debug("Downloading encrypted media from CDN");

        Request request = new RequestBuilder("GET")
            .setUrl(target)
            .setRequestTimeout(config.getRequestTimeout())
                .build();
        Response response = sendResponse(request);
        assertHttpSuccess(response.getStatusCode(), "CDN download failed");
        LOG.debug("CDN download succeeded, status={}, size={} bytes",
            response.getStatusCode(), response.getResponseBodyAsBytes().length);
        return response.getResponseBodyAsBytes();
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
        RequestBuilder builder = new RequestBuilder("POST")
                .setUrl(buildUrl(baseUrl, path))
                .setHeader("Content-Type", CONTENT_TYPE_JSON)
                .setHeader("AuthorizationType", AUTHORIZATION_TYPE)
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("X-WECHAT-UIN", WechatUinGenerator.randomWechatUin())
                .setBody(json)
                .setCharset(StandardCharsets.UTF_8)
                .setRequestTimeout(timeout == null ? config.getRequestTimeout() : timeout);

        withOptionalHeaders(builder);

    LOG.debug("Sending business request, path={}, timeoutMs={}",
        path,
        (timeout == null ? config.getRequestTimeout() : timeout).toMillis());
        Response response = sendResponse(builder.build());
        assertHttpSuccess(response.getStatusCode(), "Business request failed");
        return jsonCodec.fromJson(response.getResponseBody(StandardCharsets.UTF_8), responseType);
    }

    private RequestBuilder withLoginHeaders(RequestBuilder builder) {
        return withOptionalHeaders(builder);
    }

    private RequestBuilder withOptionalHeaders(RequestBuilder builder) {
        if (config.getAppId() != null && !config.getAppId().isBlank()) {
            builder.setHeader("iLink-App-Id", config.getAppId());
        }
        if (config.getAppClientVersion() != null && !config.getAppClientVersion().isBlank()) {
            builder.setHeader("iLink-App-ClientVersion", config.getAppClientVersion());
        }
        if (config.getRouteTag() != null && !config.getRouteTag().isBlank()) {
            builder.setHeader("SKRouteTag", config.getRouteTag());
        }
        return builder;
    }

    private Response sendResponse(Request request) {
        LOG.debug("Executing HTTP request: {} {}", request.getMethod(), request.getUri().getPath());
        try {
            Response response = httpClient.executeRequest(request).get();
            LOG.debug("HTTP response received: {} {}, status={}",
                    request.getMethod(), request.getUri().getPath(), response.getStatusCode());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("HTTP request interrupted: {} {}", request.getMethod(), request.getUri().getPath(), e);
            throw new ILinkException("HTTP request interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            LOG.error("HTTP request failed: {} {}", request.getMethod(), request.getUri().getPath(), cause);
            throw new ILinkException("HTTP request failed", cause);
        }
    }

    private String sendText(Request request) {
        Response response = sendResponse(request);
        assertHttpSuccess(response.getStatusCode(), "HTTP request failed");
        return response.getResponseBody(StandardCharsets.UTF_8);
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
        if (statusCode < 200 || statusCode >= 300) {
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
        String message = errmsg == null || errmsg.isBlank() ? "Business request failed" : errmsg;

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
}
