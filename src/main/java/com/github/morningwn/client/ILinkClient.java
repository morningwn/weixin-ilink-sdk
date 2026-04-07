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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
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
public class ILinkClient {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String AUTHORIZATION_TYPE = "ilink_bot_token";

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
                HttpClient.newBuilder().connectTimeout(config.getConnectTimeout()).build(),
                new JacksonJsonCodec());
    }

    /**
     * Creates a client with provided dependencies.
     *
     * @param config client config
     * @param httpClient java http client
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
        String path = "/ilink/bot/get_bot_qrcode?bot_type=" + config.getBotType();
        HttpRequest request = withLoginHeaders(HttpRequest.newBuilder(buildUri(config.getBaseUrl(), path))
                .GET()
                .timeout(config.getRequestTimeout()))
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
        HttpRequest request = withLoginHeaders(HttpRequest.newBuilder(buildUri(baseUrl, path))
                .GET()
                .timeout(config.getRequestTimeout()))
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
        List<SendMessageResponse> responses = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
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

        HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .header("Content-Type", CONTENT_TYPE_OCTET_STREAM)
                .POST(HttpRequest.BodyPublishers.ofByteArray(encryptedBytes))
                .timeout(config.getRequestTimeout())
                .build();

        HttpResponse<byte[]> response = sendBytes(request);
        assertHttpSuccess(response.statusCode(), "CDN upload failed");
        String encryptedParam = response.headers().firstValue("x-encrypted-param").orElse(null);
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
                    + "/download?encrypted_query_param=" + urlEncode(media.encryptQueryParam());
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .GET()
                .timeout(config.getRequestTimeout())
                .build();
        HttpResponse<byte[]> response = sendBytes(request);
        assertHttpSuccess(response.statusCode(), "CDN download failed");
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
        HttpRequest.Builder builder = HttpRequest.newBuilder(buildUri(baseUrl, path))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .header("AuthorizationType", AUTHORIZATION_TYPE)
                .header("Authorization", "Bearer " + token)
                .header("X-WECHAT-UIN", WechatUinGenerator.randomWechatUin())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(timeout == null ? config.getRequestTimeout() : timeout);

        withOptionalHeaders(builder);

        HttpResponse<String> response = sendString(builder.build());
        assertHttpSuccess(response.statusCode(), "Business request failed");
        return jsonCodec.fromJson(response.body(), responseType);
    }

    private HttpRequest.Builder withLoginHeaders(HttpRequest.Builder builder) {
        return withOptionalHeaders(builder);
    }

    private HttpRequest.Builder withOptionalHeaders(HttpRequest.Builder builder) {
        if (config.getAppId() != null && !config.getAppId().isBlank()) {
            builder.header("iLink-App-Id", config.getAppId());
        }
        if (config.getAppClientVersion() != null && !config.getAppClientVersion().isBlank()) {
            builder.header("iLink-App-ClientVersion", config.getAppClientVersion());
        }
        if (config.getRouteTag() != null && !config.getRouteTag().isBlank()) {
            builder.header("SKRouteTag", config.getRouteTag());
        }
        return builder;
    }

    private HttpResponse<String> sendString(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ILinkException("HTTP request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ILinkException("HTTP request interrupted", e);
        }
    }

    private HttpResponse<byte[]> sendBytes(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ILinkException("HTTP request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ILinkException("HTTP request interrupted", e);
        }
    }

    private String sendText(HttpRequest request) {
        HttpResponse<String> response = sendString(request);
        assertHttpSuccess(response.statusCode(), "HTTP request failed");
        return response.body();
    }

    private URI buildUri(String baseUrl, String path) {
        requireNonBlank(baseUrl, "baseUrl");
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
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
            throw new SessionExpiredException(message, effectiveRet, effectiveErr, statusCode);
        }
        throw new ILinkProtocolException(message, effectiveRet, effectiveErr, statusCode);
    }
}
