package dev.suprim.zava.internal.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-side settings received during login.
 *
 * <p>Contains file upload limits, socket configuration, and keepalive timings.
 * Populated from the {@code settings} field in the login/server info response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {

    @JsonProperty("features")
    private Features features;

    @JsonProperty("keepalive")
    private Keepalive keepalive;

    public Settings() {}

    public Features getFeatures() { return features; }
    public Keepalive getKeepalive() { return keepalive; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Features {

        @JsonProperty("sharefile")
        private ShareFile shareFile;

        @JsonProperty("socket")
        private Socket socket;

        public ShareFile getShareFile() { return shareFile; }
        public Socket getSocket() { return socket; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShareFile {

        @JsonProperty("max_size_share_file")
        private long maxSizeShareFile;

        @JsonProperty("max_size_share_file_v2")
        private long maxSizeShareFileV2;

        @JsonProperty("max_size_share_file_v3")
        private long maxSizeShareFileV3;

        @JsonProperty("max_size_photo")
        private long maxSizePhoto;

        @JsonProperty("max_size_original_photo")
        private long maxSizeOriginalPhoto;

        @JsonProperty("max_size_gif")
        private long maxSizeGif;

        @JsonProperty("max_size_resize_photo")
        private long maxSizeResizePhoto;

        @JsonProperty("chunk_size_file")
        private int chunkSizeFile;

        @JsonProperty("max_file")
        private int maxFile;

        @JsonProperty("next_file_time")
        private int nextFileTime;

        @JsonProperty("restricted_ext")
        private String restrictedExt;

        public long getMaxSizeShareFile() { return maxSizeShareFile; }
        public long getMaxSizeShareFileV2() { return maxSizeShareFileV2; }
        public long getMaxSizeShareFileV3() { return maxSizeShareFileV3; }
        public long getMaxSizePhoto() { return maxSizePhoto; }
        public long getMaxSizeOriginalPhoto() { return maxSizeOriginalPhoto; }
        public long getMaxSizeGif() { return maxSizeGif; }
        public long getMaxSizeResizePhoto() { return maxSizeResizePhoto; }
        public int getChunkSizeFile() { return chunkSizeFile; }
        public int getMaxFile() { return maxFile; }
        public int getNextFileTime() { return nextFileTime; }
        public String getRestrictedExt() { return restrictedExt; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Socket {

        @JsonProperty("ping_interval")
        private int pingInterval;

        @JsonProperty("max_msg_size")
        private int maxMsgSize;

        @JsonProperty("enable_chat_socket")
        private boolean enableChatSocket;

        @JsonProperty("enable_ctrl_socket")
        private boolean enableCtrlSocket;

        public int getPingInterval() { return pingInterval; }
        public int getMaxMsgSize() { return maxMsgSize; }
        public boolean isEnableChatSocket() { return enableChatSocket; }
        public boolean isEnableCtrlSocket() { return enableCtrlSocket; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Keepalive {

        @JsonProperty("alway_keepalive")
        private int alwaysKeepalive;

        @JsonProperty("keepalive_duration")
        private int keepaliveDuration;

        @JsonProperty("time_deactive")
        private int timeDeactive;

        public int getAlwaysKeepalive() { return alwaysKeepalive; }
        public int getKeepaliveDuration() { return keepaliveDuration; }
        public int getTimeDeactive() { return timeDeactive; }
    }
}
