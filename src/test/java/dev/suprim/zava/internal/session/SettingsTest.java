package dev.suprim.zava.internal.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test @DisplayName("deserializes settings from JSON")
    void deserialize() throws Exception {
        String json = "{\"features\":{\"sharefile\":{\"max_size_share_file\":25,\"chunk_size_file\":524288,\"max_file\":20,\"next_file_time\":3000,\"restricted_ext\":\"exe,bat\",\"max_size_share_file_v2\":100,\"max_size_share_file_v3\":200,\"max_size_photo\":5,\"max_size_original_photo\":10,\"max_size_gif\":5,\"max_size_resize_photo\":2},\"socket\":{\"ping_interval\":30000,\"max_msg_size\":65536,\"enable_chat_socket\":true,\"enable_ctrl_socket\":true}},\"keepalive\":{\"alway_keepalive\":1,\"keepalive_duration\":300,\"time_deactive\":60}}";

        Settings s = MAPPER.readValue(json, Settings.class);
        assertNotNull(s.getFeatures());
        assertNotNull(s.getFeatures().getShareFile());
        assertNotNull(s.getFeatures().getSocket());
        assertNotNull(s.getKeepalive());

        assertEquals(524288, s.getFeatures().getShareFile().getChunkSizeFile());
        assertEquals(25, s.getFeatures().getShareFile().getMaxSizeShareFile());
        assertEquals(100, s.getFeatures().getShareFile().getMaxSizeShareFileV2());
        assertEquals(200, s.getFeatures().getShareFile().getMaxSizeShareFileV3());
        assertEquals(5, s.getFeatures().getShareFile().getMaxSizePhoto());
        assertEquals(10, s.getFeatures().getShareFile().getMaxSizeOriginalPhoto());
        assertEquals(5, s.getFeatures().getShareFile().getMaxSizeGif());
        assertEquals(2, s.getFeatures().getShareFile().getMaxSizeResizePhoto());
        assertEquals(20, s.getFeatures().getShareFile().getMaxFile());
        assertEquals(3000, s.getFeatures().getShareFile().getNextFileTime());
        assertEquals("exe,bat", s.getFeatures().getShareFile().getRestrictedExt());

        assertEquals(30000, s.getFeatures().getSocket().getPingInterval());
        assertEquals(65536, s.getFeatures().getSocket().getMaxMsgSize());
        assertTrue(s.getFeatures().getSocket().isEnableChatSocket());
        assertTrue(s.getFeatures().getSocket().isEnableCtrlSocket());

        assertEquals(1, s.getKeepalive().getAlwaysKeepalive());
        assertEquals(300, s.getKeepalive().getKeepaliveDuration());
        assertEquals(60, s.getKeepalive().getTimeDeactive());
    }
}
