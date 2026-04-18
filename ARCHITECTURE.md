# Zava Architecture

> Kiến trúc của Zava - Java SDK cho Zalo Web API
>
> Tài liệu phân tích chi tiết zca-js (codebase gốc) nằm ở [ZCA-JS-REFERENCE.md](ZCA-JS-REFERENCE.md)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Tech Stack](#2-tech-stack)
3. [Package Structure](#3-package-structure)
4. [Core Components](#4-core-components)
5. [Crypto Layer](#5-crypto-layer)
6. [HTTP Layer](#6-http-layer)
7. [WebSocket Layer](#7-websocket-layer)
8. [API Layer](#8-api-layer)
9. [Model Layer](#9-model-layer)
10. [Error Handling](#10-error-handling)
11. [Design Decisions](#11-design-decisions)

---

## 1. Overview

Zava là Java SDK cho Zalo Web API, port từ [zca-js](https://github.com/RFS-ADRENO/zca-js).

```
┌─────────────────────────────────────────────────┐
│                  Consumer Code                   │
├─────────────────────────────────────────────────┤
│                    Zava.java                     │  Entry point: login(), loginQR()
├──────────┬──────────┬──────────┬────────────────┤
│ messages │  groups  │ friends  │  ...services   │  API Layer (domain services)
├──────────┴──────────┴──────────┴────────────────┤
│              ZavaHttpClient                      │  HTTP Layer (OkHttp wrapper)
│              ZavaListener                        │  WebSocket Layer
├─────────────────────────────────────────────────┤
│              CryptoUtils                         │  Crypto Layer (AES-CBC, AES-GCM, MD5)
├─────────────────────────────────────────────────┤
│              ZavaContext                          │  Session state
│              Models + Enums                      │  Data structures
└─────────────────────────────────────────────────┘
```

**Nguyên tắc thiết kế:**
- Blocking API (synchronous) - consumer tự quản lý threading
- 3 runtime dependencies (OkHttp, Jackson, SLF4J)
- Tất cả crypto dùng JDK built-in (`javax.crypto`, `java.security`)
- Domain-oriented services thay vì 1 class khổng lồ

---

## 2. Tech Stack

| Component | Choice | Version | Lý do |
|-----------|--------|---------|-------|
| Java | 11 | LTS | Cân bằng giữa modern API và broad support. Supported đến 2032+ |
| Build | Maven | 3.9.x | Standard, stable |
| HTTP + WS | OkHttp | 4.12.0 | HTTP client + WebSocket + CookieJar trong cùng 1 lib |
| JSON | Jackson | 2.19.0 | Native `long`/`BigInteger`, annotations, tree model |
| Logging | SLF4J | 2.0.17 | Facade - consumer tự chọn backend |
| Test | JUnit 5 | 5.12.2 | Standard |
| Crypto | JDK built-in | - | `javax.crypto.Cipher`, `java.security.MessageDigest` |
| Compression | JDK built-in | - | `java.util.zip.Inflater` |

Chi tiết lý do chọn từng version: xem [ZCA-JS-REFERENCE.md#93-version-decision-notes](ZCA-JS-REFERENCE.md#93-version-decision-notes)

---

## 3. Package Structure

```
com.suprim.zava/
│
├── Zava.java                          // Entry point
│   ├── login(Credentials) -> ZavaClient
│   └── loginQR(QRLoginOptions, QRLoginCallback) -> ZavaClient
│
├── ZavaClient.java                    // API facade, returned after login
│   ├── messages() -> MessageApi
│   ├── groups() -> GroupApi
│   ├── friends() -> FriendApi
│   ├── reactions() -> ReactionApi
│   ├── stickers() -> StickerApi
│   ├── polls() -> PollApi
│   ├── profile() -> ProfileApi
│   ├── conversations() -> ConversationApi
│   ├── settings() -> SettingsApi
│   ├── business() -> BusinessApi
│   ├── listener() -> ZavaListener
│   └── credentials() -> CredentialManager
│
├── core/                              // Internal infrastructure
│   ├── ZavaContext.java               // Session state (uid, secretKey, serviceMap, settings)
│   ├── ZavaOptions.java               // SDK configuration
│   ├── ZavaHttpClient.java            // OkHttp wrapper (headers, cookies, redirects)
│   ├── ZavaCookieJar.java             // Cookie persistence
│   ├── ZavaResponse.java              // Response<T> wrapper (data + error)
│   ├── ResponseHandler.java           // Decrypt response, parse JSON, check errors
│   ├── UrlBuilder.java                // Build API URLs with params + version
│   └── CallbacksMap.java              // TTL-enabled map for upload callbacks
│
├── crypto/                            // All encryption/hashing
│   ├── CryptoUtils.java               // AES-CBC encrypt/decrypt (2 key formats)
│   ├── GcmDecryptor.java              // AES-GCM decrypt (WebSocket events)
│   ├── ParamsEncryptor.java           // Login-specific: zcid, encryptKey derivation
│   ├── SignKeyUtils.java              // getSignKey(type, params) -> MD5
│   └── HashUtils.java                 // MD5 (file checksum, PIN, UUID generation)
│
├── api/                               // Public API surface (1 class per domain)
│   ├── LoginApi.java                  // Cookie login + QR login
│   ├── MessageApi.java                // send, forward, delete, undo
│   ├── AttachmentApi.java             // Chunked file upload
│   ├── EventApi.java                  // Typing, seen, delivered events
│   ├── GroupApi.java                  // CRUD groups, members, settings
│   ├── FriendApi.java                 // Find, request, block, alias
│   ├── ReactionApi.java               // Add reaction
│   ├── StickerApi.java                // Search, get, send stickers
│   ├── PollApi.java                   // Create, vote, lock polls
│   ├── BoardApi.java                  // Notes, boards
│   ├── ReminderApi.java               // CRUD reminders
│   ├── ProfileApi.java                // Update profile, avatar, bio
│   ├── ConversationApi.java           // Delete, pin, mute, archive
│   ├── SettingsApi.java               // Get/update settings, labels
│   ├── BusinessApi.java               // Biz account, catalog, products
│   ├── AutoReplyApi.java              // CRUD auto-reply rules
│   └── QuickMessageApi.java           // CRUD quick messages
│
├── listener/                          // WebSocket real-time events
│   ├── ZavaListener.java              // WebSocket client (connect, ping, reconnect)
│   ├── ZavaEventHandler.java          // Callback interface for events
│   ├── WsFrame.java                   // 4-byte header + payload codec
│   ├── WsCommand.java                 // Command type enum (501, 521, 601, ...)
│   └── EventDecoder.java              // Decrypt/decompress event payloads
│
├── model/                             // Data models (immutable POJOs)
│   ├── Message.java                   // UserMessage, GroupMessage
│   ├── Group.java                     // GroupInfo, GroupSetting, GroupTopic
│   ├── User.java                      // User, UserBasic
│   ├── Reaction.java                  // Reaction data
│   ├── GroupEvent.java                // 22 group event types
│   ├── FriendEvent.java               // 13 friend event types
│   ├── Undo.java                      // Message deletion
│   ├── Typing.java                    // Typing indicators
│   ├── SeenMessage.java               // Seen receipts
│   ├── DeliveredMessage.java          // Delivery receipts
│   ├── Attachment.java                // AttachmentSource (file path, bytes, stream)
│   ├── MessageContent.java            // Rich message (text + styles + mentions + attachments)
│   ├── Credentials.java               // Login credentials (imei, cookies, userAgent)
│   ├── Poll.java                      // Poll data
│   ├── Reminder.java                  // Reminder data
│   ├── Sticker.java                   // Sticker data
│   ├── Catalog.java                   // Catalog + product data
│   ├── Label.java                     // Label data
│   ├── QuickMessage.java              // Quick message template
│   ├── AutoReply.java                 // Auto-reply config
│   └── enums/                         // All enums
│       ├── ThreadType.java            // USER, GROUP
│       ├── Gender.java                // MALE, FEMALE
│       ├── GroupType.java             // GROUP, COMMUNITY
│       ├── GroupEventType.java        // 22 values
│       ├── FriendEventType.java       // 13 values
│       ├── TextStyle.java             // BOLD, ITALIC, colors, lists
│       ├── Urgency.java               // DEFAULT, IMPORTANT, URGENT
│       ├── Reactions.java             // 56 emoji codes
│       └── ...
│
└── exception/                         // Custom exceptions
    ├── ZavaApiException.java          // Base (message + error code)
    ├── ZavaLoginQRAbortedException.java
    ├── ZavaLoginQRDeclinedException.java
    └── ZavaCryptoException.java       // Encrypt/decrypt failures
```

---

## 4. Core Components

### 4.1 Zava (Entry Point)

```java
public class Zava {

    private final ZavaOptions options;

    public Zava() { ... }
    public Zava(ZavaOptions options) { ... }

    /**
     * Login bằng cookie credentials.
     * Flow: parseCookies -> getLoginInfo -> getServerInfo -> build ZavaClient
     */
    public ZavaClient login(Credentials credentials) { ... }

    /**
     * Login bằng QR code.
     * Multi-step: loadPage -> generate QR -> waitScan -> waitConfirm -> checkSession
     */
    public ZavaClient loginQR(QRLoginOptions options, QRLoginCallback callback) { ... }
}
```

### 4.2 ZavaClient (API Facade)

Returned sau khi login thành công. Giữ session state và expose tất cả API services.

```java
public class ZavaClient {

    private final ZavaContext context;

    // Lazy-initialized, thread-safe service instances
    public MessageApi messages() { ... }
    public GroupApi groups() { ... }
    public FriendApi friends() { ... }
    public ReactionApi reactions() { ... }
    public ZavaListener listener() { ... }
    public CredentialManager credentials() { ... }
    // ... other services
}
```

**Tại sao tách thành services thay vì 1 class?**

zca-js có 1 class `API` với 145+ methods. Trong Java đó sẽ là một class khổng lồ,
khó navigate, khó test. Tách theo domain cho phép:
- IDE autocomplete hữu ích hơn (`client.groups().` chỉ show group methods)
- Test từng service riêng
- Lazy init (chỉ tạo service khi dùng)

### 4.3 ZavaContext (Session State)

Shared state giữa tất cả components. Immutable sau khi login xong.

```java
public class ZavaContext {

    private final String uid;                    // User ID
    private final String imei;                   // Device ID
    private final String secretKey;              // AES key (Base64) cho session encrypt/decrypt
    private final String userAgent;
    private final String language;               // Default "vi"
    private final ZavaCookieJar cookieJar;
    private final Map<String, List<String>> serviceMap;  // service name -> endpoint URLs
    private final Settings settings;             // shareFile, socket, keepalive configs
    private final ZavaOptions options;

    // Thread-safe upload callback map
    private final CallbacksMap uploadCallbacks;
}
```

### 4.4 ZavaOptions (Configuration)

```java
public class ZavaOptions {

    private final boolean selfListen;            // Default false
    private final boolean logging;               // Default true
    private final int apiType;                   // Default 30
    private final int apiVersion;                // Default 671
    private final Proxy proxy;                   // Optional HTTP proxy
    private final OkHttpClient httpClient;       // Optional custom OkHttpClient
}
```

---

## 5. Crypto Layer

Đây là phần critical nhất. 5 hàm encrypt/decrypt khác nhau, dùng key format khác nhau.

### 5.1 CryptoUtils

```java
public final class CryptoUtils {

    // --- Session encrypt/decrypt (dùng cho mọi API call sau login) ---

    /** Encrypt params. Key = Base64.decode(secretKey), IV = zero, AES-CBC, output Base64 */
    public static String encodeAES(String secretKey, String data);

    /** Decrypt response. Key = Base64.decode(secretKey), IV = zero, AES-CBC */
    public static String decodeAES(String secretKey, String data);

    // --- Login encrypt/decrypt (key format khác!) ---

    /** Decrypt login response. Key = UTF-8 bytes, IV = zero, AES-CBC */
    public static String decryptResp(String key, String data);
}
```

**Sự khác biệt quan trọng:**
- Session: `new SecretKeySpec(Base64.getDecoder().decode(secretKey), "AES")`
- Login: `new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES")`

### 5.2 GcmDecryptor

```java
public final class GcmDecryptor {

    /**
     * Decrypt WebSocket event payload.
     *
     * @param cipherKey Base64-encoded key (received on WS connect)
     * @param payload   Raw bytes: [IV 16B][AAD 16B][ciphertext...]
     * @return Decrypted JSON string
     */
    public static String decrypt(String cipherKey, byte[] payload);
}
```

### 5.3 ParamsEncryptor

Chỉ dùng khi login. Tạo zcid + derive encryption key.

```java
public class ParamsEncryptor {

    private final String zcid;       // AES-CBC("type,imei,firstLaunchTime", hardcoded key)
    private final String zcidExt;    // Random hex 6-12 chars
    private final String encryptKey; // Derived: MD5(zcidExt).even[0..7] + zcid.even[0..11] + zcid.odd.reverse[0..11]

    public ParamsEncryptor(int type, String imei, long firstLaunchTime);

    public String encrypt(String data);  // AES-CBC with derived encryptKey
    public Map<String, String> getParams(); // {zcid, zcid_ext, enc_ver: "v2"}
}
```

### 5.4 SignKeyUtils

```java
public final class SignKeyUtils {

    /**
     * MD5("zsecure" + type + sorted_param_values)
     */
    public static String getSignKey(String type, Map<String, Object> params);
}
```

### 5.5 HashUtils

```java
public final class HashUtils {

    public static String md5(String input);
    public static String md5Chunked(Path file);              // 2MB chunks
    public static String generateUUID(String userAgent);     // UUID + "-" + MD5(userAgent)
    public static String encryptPin(String pin);             // MD5(pin)
}
```

---

## 6. HTTP Layer

### 6.1 ZavaHttpClient

Wrapper trên OkHttp. Tất cả API calls đi qua đây.

```java
public class ZavaHttpClient {

    private final OkHttpClient client;
    private final ZavaContext context;

    /** GET request, tự đính kèm default headers + cookies */
    public Response get(String url);

    /** POST form-urlencoded */
    public Response post(String url, Map<String, String> params);

    /** POST multipart (file upload) */
    public Response postMultipart(String url, MultipartBody body);

    /** Parse Set-Cookie headers và lưu vào CookieJar */
    // Xử lý tự động trong interceptor
}
```

**Default headers** (đính kèm mọi request):
```
Accept: application/json, text/plain, */*
Accept-Encoding: gzip, deflate, br, zstd
Accept-Language: en-US,en;q=0.9
Content-Type: application/x-www-form-urlencoded
Cookie: <from CookieJar>
Origin: https://chat.zalo.me
Referer: https://chat.zalo.me/
User-Agent: <from context>
```

### 6.2 ResponseHandler

```java
public class ResponseHandler {

    /**
     * Standard flow:
     * 1. Check HTTP status
     * 2. Parse JSON: { error_code, error_message, data }
     * 3. If error_code != 0: throw ZavaApiException
     * 4. If encrypted: decodeAES(secretKey, data)
     * 5. Parse inner JSON, check inner error_code
     * 6. Return typed result via Jackson
     */
    public <T> T handle(Response response, Class<T> type);
    public <T> T handle(Response response, TypeReference<T> type);
}
```

---

## 7. WebSocket Layer

### 7.1 Binary Frame Format

```
Byte 0:     version (UInt8, always 1)
Byte 1-2:   cmd (UInt16 Little-Endian)
Byte 3:     subCmd (Int8)
Byte 4+:    payload (UTF-8 JSON)
```

### 7.2 ZavaListener

```java
public class ZavaListener extends WebSocketListener {

    private WebSocket ws;
    private String cipherKey;          // Received on connect (cmd=1)
    private final List<ZavaEventHandler> handlers;

    /** Connect, start ping, begin receiving events */
    public void start();
    public void start(ListenerOptions options);  // retryOnClose, etc.

    /** Graceful disconnect */
    public void stop();

    /** Register event handler */
    public ZavaListener addHandler(ZavaEventHandler handler);

    /** Builder-style convenience */
    public ZavaListener onMessage(Consumer<Message> handler);
    public ZavaListener onReaction(Consumer<Reaction> handler);
    public ZavaListener onGroupEvent(Consumer<GroupEvent> handler);
    // ...

    // --- WebSocketListener overrides ---

    @Override
    public void onMessage(WebSocket ws, ByteString bytes) {
        // 1. Parse 4-byte header
        // 2. Route by cmd:
        //    501 -> user messages
        //    521 -> group messages
        //    601 -> control events (file_done, group, friend)
        //    602 -> typing
        //    612 -> reactions
        //    502/522 -> seen/delivered
        //    1   -> cipher key exchange
        //    3000 -> duplicate connection
        // 3. Decrypt payload (4 modes based on 'encrypt' field)
        // 4. Dispatch to handlers
    }
}
```

### 7.3 Event Decryption (4 modes)

```java
public class EventDecoder {

    public static Object decode(JsonNode parsed, String cipherKey) {
        int encrypt = parsed.get("encrypt").asInt();
        String data = parsed.get("data").asText();

        switch (encrypt) {
            case 0: return parseJson(data);                              // Plain
            case 1: return parseJson(inflate(base64Decode(data)));       // zlib
            case 2: return parseJson(inflate(gcmDecrypt(data, key)));    // AES-GCM + zlib
            case 3: return parseJson(gcmDecrypt(data, key));             // AES-GCM only
        }
    }
}
```

### 7.4 Command Table

| cmd | subCmd | Direction | Description |
|-----|--------|-----------|-------------|
| 1 | 1 | Receive | Cipher key exchange |
| 2 | 1 | Send | Ping/keepalive |
| 501 | 0 | Receive | User messages |
| 510 | 1 | Both | Old user messages |
| 511 | 1 | Both | Old group messages |
| 521 | 0 | Receive | Group messages |
| 502 | 0 | Receive | User seen/delivered |
| 522 | 0 | Receive | Group seen/delivered |
| 601 | 0 | Receive | Control events (file_done, group, friend) |
| 602 | 0 | Receive | Typing |
| 610 | 0/1 | Both | Old user reactions |
| 611 | 0/1 | Both | Old group reactions |
| 612 | 0 | Receive | Reactions |
| 3000 | 0 | Receive | Duplicate connection |

---

## 8. API Layer

Mỗi API service follow cùng pattern:

```java
public class SomeApi {

    private final ZavaContext context;
    private final ZavaHttpClient http;
    private final ResponseHandler responseHandler;

    SomeApi(ZavaContext context, ZavaHttpClient http, ResponseHandler responseHandler) {
        this.context = context;
        this.http = http;
        this.responseHandler = responseHandler;
    }

    public SomeResponse doSomething(String param) {
        // 1. Validate
        Objects.requireNonNull(param, "param must not be null");

        // 2. Build params
        Map<String, Object> params = Map.of(
            "param", param,
            "imei", context.getImei()
        );

        // 3. Encrypt
        String encrypted = CryptoUtils.encodeAES(
            context.getSecretKey(),
            objectMapper.writeValueAsString(params)
        );

        // 4. HTTP request
        Response response = http.get(
            UrlBuilder.build(context.getServiceUrl("chat"), encrypted)
        );

        // 5. Decrypt + parse response
        return responseHandler.handle(response, SomeResponse.class);
    }
}
```

### API Service Catalog

| Service | Methods | Zalo Service |
|---------|---------|-------------|
| `MessageApi` | send, forward, delete, undo | chat, file |
| `AttachmentApi` | upload (chunked) | file |
| `EventApi` | sendTyping, sendSeen, sendDelivered | chat |
| `GroupApi` | create, getInfo, addUser, removeUser, changeSettings, ... (~30) | group |
| `FriendApi` | find, getUserInfo, getAllFriends, sendRequest, block, alias (~20) | friend |
| `ReactionApi` | addReaction | reaction |
| `StickerApi` | search, getDetail, send | sticker |
| `PollApi` | create, vote, lock, share, getDetail | group_poll |
| `BoardApi` | createNote, editNote, getListBoard | boards, group_board |
| `ReminderApi` | create, edit, remove, getList | todoUrl |
| `ProfileApi` | updateProfile, updateBio, changeAvatar | profile |
| `ConversationApi` | delete, pin, mute, archive | conversation |
| `SettingsApi` | getSettings, updateSettings, labels | settings, label |
| `BusinessApi` | getBizAccount, catalog CRUD, product CRUD | catalog |
| `AutoReplyApi` | CRUD | auto_reply |
| `QuickMessageApi` | CRUD | quick_message |

---

## 9. Model Layer

### 9.1 Design Principles

- **Immutable** - tất cả models dùng final fields
- **Builder pattern** cho models phức tạp (MessageContent, Credentials)
- **Factory methods** cho models đơn giản (`UserMessage.of(data)`)
- **Jackson annotations** cho deserialization (`@JsonProperty`, `@JsonIgnoreProperties`)

### 9.2 Message Envelope Pattern

Hầu hết models dùng chung envelope:

```java
public class UserMessage {
    private final ThreadType type = ThreadType.USER;
    private final TMessage data;
    private final String threadId;
    private final boolean self;
}

public class GroupMessage {
    private final ThreadType type = ThreadType.GROUP;
    private final TGroupMessage data;
    private final String threadId;
    private final boolean self;
}
```

### 9.3 Attachment Input

Hỗ trợ 3 cách truyền file:

```java
public abstract class Attachment {
    public static Attachment fromFile(Path path);
    public static Attachment fromBytes(byte[] data, String filename);
    public static Attachment fromStream(InputStream stream, String filename);
}
```

### 9.4 Credentials Persistence

```java
public class Credentials {
    // Builder pattern cho construction
    public static Builder builder() { ... }

    // Serialization
    public void saveTo(Path path);          // JSON via Jackson
    public static Credentials loadFrom(Path path);
}
```

---

## 10. Error Handling

```
RuntimeException
└── ZavaApiException                    // Base: message + error code
    ├── ZavaLoginQRAbortedException     // QR login aborted
    ├── ZavaLoginQRDeclinedException    // QR login declined by user
    └── ZavaCryptoException             // Encrypt/decrypt failure
```

Tất cả exceptions là **unchecked** (extends RuntimeException):
- Consumer không bị bắt buộc try-catch mọi API call
- Consistent với các SDK hiện đại (OkHttp, Retrofit)
- Error code từ Zalo server được giữ nguyên trong `ZavaApiException.getCode()`

---

## 11. Design Decisions

### Blocking API (không dùng CompletableFuture)

**Lý do:**
- SDK đơn giản nhất có thể cho consumer
- Consumer tự chọn concurrency model (thread pool, virtual threads, reactive)
- Hầu hết use case (chatbot) không cần high throughput
- Nếu cần async, consumer wrap trong `CompletableFuture.supplyAsync()`

### Domain services thay vì 1 API class

**Lý do:**
- zca-js có 145+ methods trong 1 class `API` - trong Java đó sẽ là nightmare
- Mỗi service class nhỏ, focused, dễ test
- IDE autocomplete hữu ích hơn

### OkHttp 4.12.0 thay vì 5.x

**Lý do:**
- OkHttp 5.x requires Java 21 - thu hẹp user base
- 4.12.0 là bản stable cuối cùng, rất ổn định
- API surface gần như giống nhau - upgrade sau chỉ cần bump version
- Xem chi tiết: [ZCA-JS-REFERENCE.md#93-version-decision-notes](ZCA-JS-REFERENCE.md#93-version-decision-notes)

### Jackson thay vì Gson

**Lý do:**
- Native `long`/`BigInteger` support (Zalo IDs)
- Annotation-based mapping mạnh hơn
- Tree model (`JsonNode`) hữu ích cho dynamic parsing
- Streaming API cho performance khi cần
- Ecosystem lớn hơn (modules cho Java 8 date/time, etc.)

### Unchecked exceptions

**Lý do:**
- API errors từ Zalo không phải là recoverable errors trong hầu hết cases
- Consumer có thể catch nếu muốn, không bị forced
- Consistent với OkHttp, Jackson, và hầu hết modern Java libs

### Hardcoded values

Các giá trị được hardcode (giống zca-js):

```
Login encrypt key:     "3FC4F0D2AB50057BCE0D90D9187A22B1"
Sign key prefix:       "zsecure"
API_TYPE default:      30
API_VERSION default:   671
Origin:                "https://chat.zalo.me"
Referer:               "https://chat.zalo.me/"
Login URL:             "https://wpa.chat.zalo.me/api/login/..."
QR Login URL:          "https://id.zalo.me/account/..."
User info URL:         "https://jr.chat.zalo.me/jr/userinfo"
Self UID indicator:    "0" (uidFrom == "0" means message from self)
QR expiry:             100 seconds
Upload callback TTL:   5 minutes
MD5 chunk size:        2MB (2097152 bytes)
```

Tất cả nằm trong `com.suprim.zava.core.Constants.java`.
