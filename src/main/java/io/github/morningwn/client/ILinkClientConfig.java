package io.github.morningwn.client;

import io.github.morningwn.util.VersionEncoder;

import java.time.Duration;

/**
 * Configuration for {@link ILinkClient}.
 */
public final class ILinkClientConfig {

    private final String baseUrl;
    private final String cdnBaseUrl;
    private final String channelVersion;
    private final String appId;
    private final String appClientVersion;
    private final String routeTag;
    private final int botType;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final Duration longPollingTimeout;

    private ILinkClientConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.cdnBaseUrl = builder.cdnBaseUrl;
        this.channelVersion = builder.channelVersion;
        this.appId = builder.appId;
        this.appClientVersion = builder.appClientVersion;
        this.routeTag = builder.routeTag;
        this.botType = builder.botType;
        this.connectTimeout = builder.connectTimeout;
        this.requestTimeout = builder.requestTimeout;
        this.longPollingTimeout = builder.longPollingTimeout;
    }

    /**
     * @return default config builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return business base url */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** @return cdn base url */
    public String getCdnBaseUrl() {
        return cdnBaseUrl;
    }

    /** @return channel version for base_info */
    public String getChannelVersion() {
        return channelVersion;
    }

    /** @return iLink-App-Id header value */
    public String getAppId() {
        return appId;
    }

    /** @return iLink-App-ClientVersion header value */
    public String getAppClientVersion() {
        return appClientVersion;
    }

    /** @return optional route tag */
    public String getRouteTag() {
        return routeTag;
    }

    /** @return get_bot_qrcode bot_type */
    public int getBotType() {
        return botType;
    }

    /** @return HTTP connect timeout */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** @return default request timeout */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /** @return long polling timeout for getupdates */
    public Duration getLongPollingTimeout() {
        return longPollingTimeout;
    }

    /**
     * Builder for {@link ILinkClientConfig}.
     */
    public static final class Builder {

        private String baseUrl = "https://ilinkai.weixin.qq.com";
        private String cdnBaseUrl = "https://novac2c.cdn.weixin.qq.com/c2c";
        private String channelVersion = "1.0.0";
        private String appId = "bot";
        private String appClientVersion = VersionEncoder.encode("2.1.6");
        private String routeTag;
        private int botType = 3;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(40);
        private Duration longPollingTimeout = Duration.ofSeconds(40);

        private Builder() {
        }

        /**
         * @param baseUrl business base url
         * @return builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param cdnBaseUrl cdn base url
         * @return builder
         */
        public Builder cdnBaseUrl(String cdnBaseUrl) {
            this.cdnBaseUrl = cdnBaseUrl;
            return this;
        }

        /**
         * @param channelVersion channel version in base_info
         * @return builder
         */
        public Builder channelVersion(String channelVersion) {
            this.channelVersion = channelVersion;
            return this;
        }

        /**
         * @param appId iLink-App-Id header
         * @return builder
         */
        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        /**
         * @param appClientVersion encoded iLink-App-ClientVersion header
         * @return builder
         */
        public Builder appClientVersion(String appClientVersion) {
            this.appClientVersion = appClientVersion;
            return this;
        }

        /**
         * @param semanticVersion semantic version, encoded to iLink client version
         * @return builder
         */
        public Builder semanticVersion(String semanticVersion) {
            this.appClientVersion = VersionEncoder.encode(semanticVersion);
            return this;
        }

        /**
         * @param routeTag optional SKRouteTag header
         * @return builder
         */
        public Builder routeTag(String routeTag) {
            this.routeTag = routeTag;
            return this;
        }

        /**
         * @param botType bot type for qr request
         * @return builder
         */
        public Builder botType(int botType) {
            this.botType = botType;
            return this;
        }

        /**
         * @param connectTimeout HTTP connect timeout
         * @return builder
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * @param requestTimeout default request timeout
         * @return builder
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * @param longPollingTimeout timeout for getupdates
         * @return builder
         */
        public Builder longPollingTimeout(Duration longPollingTimeout) {
            this.longPollingTimeout = longPollingTimeout;
            return this;
        }

        /**
         * @return immutable client config
         */
        public ILinkClientConfig build() {
            return new ILinkClientConfig(this);
        }
    }
}
