package com.vocawik.service.pv.client;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** PV external API configuration properties. */
@Component
@Getter
public class PvApiProperties {

    private final Http http;
    private final Youtube youtube;
    private final Vimeo vimeo;
    private final Niconico niconico;
    private final Bilibili bilibili;
    private final Piapro piapro;
    private final SoundCloud soundCloud;

    /** Creates PV API properties from configuration values. */
    public PvApiProperties(
            @Value("${pv.http.connect-timeout-ms:2000}") int httpConnectTimeoutMs,
            @Value("${pv.http.read-timeout-ms:5000}") int httpReadTimeoutMs,
            @Value("${pv.http.retry-attempts:2}") int httpRetryAttempts,
            @Value("${pv.http.retry-delay-ms:300}") int httpRetryDelayMs,
            @Value("${pv.youtube.api-key:}") String youtubeApiKey,
            @Value("${pv.youtube.base-url:https://www.googleapis.com/youtube/v3}")
                    String youtubeBaseUrl,
            @Value("${pv.youtube.connect-timeout-ms:${pv.http.connect-timeout-ms:2000}}")
                    int youtubeConnectTimeoutMs,
            @Value("${pv.youtube.read-timeout-ms:${pv.http.read-timeout-ms:5000}}")
                    int youtubeReadTimeoutMs,
            @Value("${pv.vimeo.access-token:}") String vimeoAccessToken,
            @Value("${pv.vimeo.base-url:https://api.vimeo.com}") String vimeoBaseUrl,
            @Value("${pv.vimeo.oembed-base-url:https://vimeo.com/api/oembed.json}")
                    String vimeoOembedBaseUrl,
            @Value("${pv.vimeo.connect-timeout-ms:${pv.http.connect-timeout-ms:2000}}")
                    int vimeoConnectTimeoutMs,
            @Value("${pv.vimeo.read-timeout-ms:${pv.http.read-timeout-ms:5000}}")
                    int vimeoReadTimeoutMs,
            @Value("${pv.niconico.base-url:https://ext.nicovideo.jp/api/getthumbinfo}")
                    String niconicoBaseUrl,
            @Value("${pv.bilibili.base-url:https://api.bilibili.com/x/web-interface/view}")
                    String bilibiliBaseUrl,
            @Value("${pv.piapro.base-url:https://piapro.jp/t}") String piaproBaseUrl,
            @Value("${pv.soundcloud.proxy-base-url:https://dream-traveler.fly.dev/soundcloud/api}")
                    String soundCloudProxyBaseUrl,
            @Value("${pv.soundcloud.oembed-base-url:https://soundcloud.com/oembed}")
                    String soundCloudOEmbedBaseUrl,
            @Value("${pv.soundcloud.connect-timeout-ms:${pv.http.connect-timeout-ms:2000}}")
                    int soundCloudConnectTimeoutMs,
            @Value("${pv.soundcloud.read-timeout-ms:${pv.http.read-timeout-ms:5000}}")
                    int soundCloudReadTimeoutMs) {
        this.http =
                new Http(
                        httpConnectTimeoutMs,
                        httpReadTimeoutMs,
                        httpRetryAttempts,
                        httpRetryDelayMs);
        this.youtube =
                new Youtube(
                        youtubeApiKey,
                        youtubeBaseUrl,
                        youtubeConnectTimeoutMs,
                        youtubeReadTimeoutMs);
        this.vimeo =
                new Vimeo(
                        vimeoAccessToken,
                        vimeoBaseUrl,
                        vimeoOembedBaseUrl,
                        vimeoConnectTimeoutMs,
                        vimeoReadTimeoutMs);
        this.niconico = new Niconico(niconicoBaseUrl);
        this.bilibili = new Bilibili(bilibiliBaseUrl);
        this.piapro = new Piapro(piaproBaseUrl);
        this.soundCloud =
                new SoundCloud(
                        soundCloudProxyBaseUrl,
                        soundCloudOEmbedBaseUrl,
                        soundCloudConnectTimeoutMs,
                        soundCloudReadTimeoutMs);
    }

    /** Shared HTTP policy for PV API clients. */
    @Getter
    public static class Http {
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final int retryAttempts;
        private final int retryDelayMs;

        public Http(int connectTimeoutMs, int readTimeoutMs, int retryAttempts, int retryDelayMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.retryAttempts = retryAttempts;
            this.retryDelayMs = retryDelayMs;
        }
    }

    /** YouTube API related properties. */
    @Getter
    public static class Youtube {
        private final String apiKey;
        private final String baseUrl;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;

        public Youtube(String apiKey, String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    /** Vimeo API related properties. */
    @Getter
    public static class Vimeo {
        private final String accessToken;
        private final String baseUrl;
        private final String oembedBaseUrl;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;

        public Vimeo(
                String accessToken,
                String baseUrl,
                String oembedBaseUrl,
                int connectTimeoutMs,
                int readTimeoutMs) {
            this.accessToken = accessToken;
            this.baseUrl = baseUrl;
            this.oembedBaseUrl = oembedBaseUrl;
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    /** NicoNico API related properties. */
    @Getter
    public static class Niconico {
        private final String baseUrl;

        public Niconico(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /** Bilibili API related properties. */
    @Getter
    public static class Bilibili {
        private final String baseUrl;

        public Bilibili(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /** Piapro page related properties. */
    @Getter
    public static class Piapro {
        private final String baseUrl;

        public Piapro(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /** SoundCloud proxy API related properties. */
    @Getter
    public static class SoundCloud {
        private final String proxyBaseUrl;
        private final String oembedBaseUrl;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;

        public SoundCloud(
                String proxyBaseUrl,
                String oembedBaseUrl,
                int connectTimeoutMs,
                int readTimeoutMs) {
            this.proxyBaseUrl = proxyBaseUrl;
            this.oembedBaseUrl = oembedBaseUrl;
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
