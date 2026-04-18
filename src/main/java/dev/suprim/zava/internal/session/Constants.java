package dev.suprim.zava.internal.session;

/**
 * Hardcoded protocol constants.
 */
public final class Constants {

    private Constants() {}

    public static final String LOGIN_ENCRYPT_KEY = "3FC4F0D2AB50057BCE0D90D9187A22B1";
    public static final String SIGN_KEY_PREFIX = "zsecure";

    public static final int DEFAULT_API_TYPE = 30;
    public static final int DEFAULT_API_VERSION = 671;

    public static final String ORIGIN = "https://chat.zalo.me";
    public static final String REFERER = "https://chat.zalo.me/";

    public static final String LOGIN_BASE_URL = "https://wpa.chat.zalo.me/api/login";
    public static final String QR_LOGIN_BASE_URL = "https://id.zalo.me/account";
    public static final String USER_INFO_URL = "https://jr.chat.zalo.me/jr/userinfo";

    /** uidFrom == "0" means message is from the logged-in user. */
    public static final String SELF_UID = "0";

    /** Max messages per send/seen/delivered batch. */
    public static final int MAX_MESSAGES_PER_SEND = 50;

    /** Default user agent for QR login when none is provided. */
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0";

    public static final int QR_EXPIRY_SECONDS = 100;
    public static final long UPLOAD_CALLBACK_TTL_MS = 5 * 60 * 1000L;
    public static final int MD5_CHUNK_SIZE = 2 * 1024 * 1024;
}
