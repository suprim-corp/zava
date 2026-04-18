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
+------------------------+--------------------------------------+
|                         Consumer Code                         |
+------------------------+--------------------------------------+
| Zava.java              | Entry point: login(), loginQR()      |
+------------------------+--------------------------------------+
| messages | groups      |                                      |
| friends  | ...services | API Layer (domain services)          |
+------------------------+--------------------------------------+
| ZavaHttpClient         | HTTP Layer (OkHttp wrapper)          |
| ZavaListener           | WebSocket Layer                      |
+------------------------+--------------------------------------+
| CryptoUtils            | Crypto (AES-CBC, AES-GCM, MD5)       |
+------------------------+--------------------------------------+
| ZavaContext            | Session state                        |
| Models + Enums         | Data structures                      |
+------------------------+--------------------------------------+
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
dev.suprim.zava
│
│   # ── Public API ──────────────────────────────────────────────
│
├── Zava.java                              # Entry point: login(), loginQR()
├── ZavaClient.java                        # Facade returned after login
├── ZavaOptions.java                       # SDK configuration (builder)
│
│   # ── Authentication ──────────────────────────────────────────
│
├── auth/
│   ├── Credentials.java                   # imei, cookies, userAgent (builder)
│   ├── CredentialStore.java               # saveTo(Path) / loadFrom(Path)
│   ├── CookieLogin.java                   # Cookie-based login flow
│   ├── QRLogin.java                       # QR code multi-step login flow
│   ├── QRLoginCallback.java               # Event interface (generated, scanned, declined)
│   └── QRLoginOptions.java                # QR login configuration
│
│   # ── Internal infrastructure ─────────────────────────────────
│
├── internal/
│   ├── crypto/
│   │   ├── AesCbc.java                    # AES-CBC encrypt/decrypt (session + login variants)
│   │   ├── AesGcm.java                    # AES-GCM decrypt (WebSocket events)
│   │   ├── ParamsEncryptor.java           # Login-specific: zcid, key derivation
│   │   ├── Signer.java                    # getSignKey(type, params) -> MD5
│   │   └── Hashing.java                   # MD5, UUID generation, PIN encrypt
│   │
│   ├── http/
│   │   ├── HttpClient.java               # OkHttp wrapper (headers, cookies, redirects)
│   │   ├── CookieJar.java                # OkHttp CookieJar with persistence
│   │   ├── ResponseHandler.java          # Decrypt + parse + error check pipeline
│   │   └── Urls.java                     # URL builder (service map + params + version)
│   │
│   ├── ws/
│   │   ├── Connection.java               # WebSocket lifecycle (connect, ping, reconnect)
│   │   ├── Frame.java                    # 4-byte binary header codec
│   │   ├── Command.java                  # cmd enum (501, 521, 601, 612, ...)
│   │   └── EventDecoder.java             # 4-mode decrypt/decompress pipeline
│   │
│   └── session/
│       ├── Context.java                   # Session state (uid, secretKey, serviceMap)
│       ├── ServiceMap.java                # Service name -> endpoint URL[] mapping
│       ├── Settings.java                  # ShareFile, Socket, Keepalive configs
│       └── CallbackMap.java              # TTL-enabled Map for async upload callbacks
│
│   # ── Domain modules ──────────────────────────────────────────
│   # Mỗi module tự chứa: service + models + exceptions
│
├── message/
│   ├── MessageService.java                # send, forward, delete, undo
│   ├── AttachmentService.java             # Chunked file upload
│   ├── Message.java                       # UserMessage, GroupMessage
│   ├── MessageContent.java               # Rich message (text + styles + mentions + attachments)
│   ├── Attachment.java                    # fromFile(), fromBytes(), fromStream()
│   ├── Quote.java                         # Quoted message reference
│   ├── Mention.java                       # @mention in group messages
│   ├── TextStyle.java                     # enum: BOLD, ITALIC, colors, lists
│   └── Urgency.java                       # enum: DEFAULT, IMPORTANT, URGENT
│
├── conversation/
│   ├── ConversationService.java           # delete, pin, mute, archive, history
│   ├── Conversation.java                  # Conversation metadata
│   └── ThreadType.java                    # enum: USER, GROUP
│
├── group/
│   ├── GroupService.java                  # create, getInfo, addUser, settings, ...
│   ├── Group.java                         # GroupInfo
│   ├── GroupSetting.java                  # Permissions and config
│   ├── GroupMember.java                   # Member info
│   ├── GroupTopic.java                    # Pinned topics
│   ├── GroupType.java                     # enum: GROUP, COMMUNITY
│   ├── GroupEvent.java                    # Incoming group events
│   └── GroupEventType.java                # enum: 22 types (JOIN, LEAVE, ...)
│
├── user/
│   ├── UserService.java                   # find, getInfo, getAllFriends, block, alias
│   ├── User.java                          # Full user profile
│   ├── UserBasic.java                     # Lightweight user info
│   ├── FriendRequest.java                 # Friend request data
│   ├── FriendEvent.java                   # Incoming friend events
│   ├── FriendEventType.java              # enum: 13 types (ADD, REMOVE, ...)
│   └── Gender.java                        # enum: MALE, FEMALE
│
├── reaction/
│   ├── ReactionService.java               # addReaction
│   ├── Reaction.java                      # Reaction data
│   └── ReactionType.java                  # enum: 56 emoji codes
│
├── sticker/
│   ├── StickerService.java                # search, getDetail, send
│   └── Sticker.java                       # Sticker data
│
├── poll/
│   ├── PollService.java                   # create, vote, lock, share, getDetail
│   └── Poll.java                          # Poll + options data
│
├── board/
│   ├── BoardService.java                  # createNote, editNote, getListBoard
│   ├── Board.java                         # Board data
│   └── Note.java                          # Note data
│
├── reminder/
│   ├── ReminderService.java               # CRUD + list
│   ├── Reminder.java                      # Reminder data
│   └── RepeatMode.java                    # enum: NONE, DAILY, WEEKLY, MONTHLY
│
├── profile/
│   ├── ProfileService.java                # updateProfile, updateBio, changeAvatar
│   └── Profile.java                       # Profile data
│
├── settings/
│   ├── SettingsService.java               # get/update settings, labels
│   ├── AppSettings.java                   # App settings
│   └── Label.java                         # Label data
│
├── business/
│   ├── BusinessService.java               # Biz account, catalog CRUD, product CRUD
│   ├── BizAccount.java                    # Business account info
│   ├── Catalog.java                       # Catalog data
│   ├── Product.java                       # Product data
│   ├── AutoReply.java                     # Auto-reply config
│   └── QuickMessage.java                  # Quick message template
│
│   # ── Listener (real-time events) ─────────────────────────────
│
├── listener/
│   ├── ZavaListener.java                  # Public: start(), stop(), onMessage(), ...
│   ├── EventHandler.java                  # Callback interface
│   ├── Typing.java                        # Typing indicator model
│   ├── SeenMessage.java                   # Seen receipt model
│   └── DeliveredMessage.java              # Delivery receipt model
│
│   # ── Exceptions ──────────────────────────────────────────────
│
└── exception/
    ├── ZavaException.java                 # Base unchecked exception (message + code)
    ├── ZavaCryptoException.java           # Encrypt/decrypt failures
    ├── ZavaAuthException.java             # Login failures
    └── ZavaTimeoutException.java          # Request/WS timeout
```

**Nguyên tắc tổ chức:**

- **Package-by-feature, không package-by-layer.** Mỗi domain (`message/`, `group/`, `user/`, ...)
  tự chứa service + model + enum của nó. Khi cần thêm field cho `Group`, không phải
  nhảy sang `model/` rồi quay lại `api/`.
- **`internal/`** là implementation details, không phải public API.
  Consumer chỉ thấy top-level classes + domain packages.
- **Flat domain packages.** Mỗi domain package nhỏ (3-8 files), không cần sub-package.
  Nếu sau này phình ra thì refactor.
- **Exception riêng package** vì được dùng cross-domain.

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

Returned sau khi login thành công. Giữ session state và expose domain services.

```java
public class ZavaClient {

    private final Context context;

    // Lazy-initialized, thread-safe
    public MessageService messages() { ... }
    public GroupService groups() { ... }
    public UserService users() { ... }
    public ReactionService reactions() { ... }
    public ConversationService conversations() { ... }
    public PollService polls() { ... }
    public StickerService stickers() { ... }
    public ProfileService profile() { ... }
    public SettingsService settings() { ... }
    public BusinessService business() { ... }
    public ZavaListener listener() { ... }
    public CredentialStore credentials() { ... }
}
```

**Tại sao package-by-feature?**

| | Package-by-layer (cũ) | Package-by-feature (mới) |
|---|---|---|
| Thêm field cho Group | Sửa `model/Group.java` + `api/GroupApi.java` | Sửa trong `group/` |
| Tìm mọi thứ về reactions | Tìm trong `api/` + `model/` + `enums/` | Mở `reaction/` |
| Delete 1 feature | Xóa scattered files | Xóa 1 package |
| IDE navigation | Scroll qua 20+ files trong `model/` | 3-8 files per package |

### 4.3 Context (Session State)

Shared state giữa tất cả components. Immutable sau khi login xong.
Nằm trong `internal/session/` - consumer không truy cập trực tiếp.

```java
// dev.suprim.zava.internal.session
public class Context {

    private final String uid;
    private final String imei;
    private final String secretKey;              // AES key (Base64)
    private final String userAgent;
    private final String language;               // Default "vi"
    private final CookieJar cookieJar;
    private final ServiceMap serviceMap;
    private final Settings settings;
    private final ZavaOptions options;
    private final CallbackMap uploadCallbacks;   // TTL-enabled, thread-safe
}
```

### 4.4 ZavaOptions (Configuration)

```java
// dev.suprim.zava
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

### 5.1 AesCbc

```java
// dev.suprim.zava.internal.crypto
public final class AesCbc {

    // --- Session encrypt/decrypt (dùng cho mọi API call sau login) ---

    /** Encrypt params. Key = Base64.decode(secretKey), IV = zero, AES-CBC, output Base64 */
    public static String encodeAES(String secretKey, String data);

    public static String decodeAES(String secretKey, String data);

    // --- Login encrypt/decrypt (key format khác!) ---

    public static String decryptResp(String key, String data);
}
```

### 5.2 AesGcm

```java
// dev.suprim.zava.internal.crypto
public final class AesGcm {

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

### 5.4 Signer

```java
// dev.suprim.zava.internal.crypto
public final class Signer {

    /**
     * MD5("zsecure" + type + sorted_param_values)
     */
    public static String getSignKey(String type, Map<String, Object> params);
}
```

### 5.5 Hashing

```java
// dev.suprim.zava.internal.crypto
public final class Hashing {

    public static String md5(String input);
    public static String md5Chunked(Path file);              // 2MB chunks
    public static String generateUUID(String userAgent);     // UUID + "-" + MD5(userAgent)
    public static String encryptPin(String pin);             // MD5(pin)
}
```

---

## 6. HTTP Layer

### 6.1 HttpClient

Wrapper trên OkHttp. Tất cả API calls đi qua đây.

```java
// dev.suprim.zava.internal.http
public class HttpClient {

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
    private final List<EventHandler> handlers;

    /** Connect, start ping, begin receiving events */
    public void start();
    public void start(ListenerOptions options);  // retryOnClose, etc.

    /** Graceful disconnect */
    public void stop();

    /** Register event handler */
    public ZavaListener addHandler(EventHandler handler);

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

Mỗi domain service follow cùng pattern:

```java
// dev.suprim.zava.reaction
public class ReactionService {

    private final Context context;
    private final HttpClient http;
    private final ResponseHandler responseHandler;

    ReactionService(Context context, HttpClient http, ResponseHandler responseHandler) {
        this.context = context;
        this.http = http;
        this.responseHandler = responseHandler;
    }

    public Reaction addReaction(ReactionType icon, String msgId, String threadId) {
        // 1. Validate
        Objects.requireNonNull(icon, "icon must not be null");

        // 2. Build params
        Map<String, Object> params = Map.of(
            "rIcon", icon.getCode(),
            "msgId", msgId,
            "threadId", threadId,
            "imei", context.getImei()
        );

        // 3. Encrypt
        String encrypted = AesCbc.encode(
            context.getSecretKey(),
            objectMapper.writeValueAsString(params)
        );

        // 4. HTTP request
        Response response = http.get(
            Urls.build(context.getServiceUrl("reaction"), encrypted)
        );

        // 5. Decrypt + parse response
        return responseHandler.handle(response, Reaction.class);
    }
}
```

### Service Catalog

| Package | Service | Methods | Zalo Endpoint |
|---------|---------|---------|---------------|
| `message` | `MessageService` | send, forward, delete, undo | chat, file |
| `message` | `AttachmentService` | upload (chunked) | file |
| `conversation` | `ConversationService` | delete, pin, mute, archive, typing, seen | conversation, chat |
| `group` | `GroupService` | create, getInfo, addUser, settings, ... (~30) | group |
| `user` | `UserService` | find, getInfo, getAllFriends, block, alias (~20) | friend |
| `reaction` | `ReactionService` | addReaction | reaction |
| `sticker` | `StickerService` | search, getDetail, send | sticker |
| `poll` | `PollService` | create, vote, lock, share, getDetail | group_poll |
| `board` | `BoardService` | createNote, editNote, getListBoard | boards, group_board |
| `reminder` | `ReminderService` | create, edit, remove, getList | todoUrl |
| `profile` | `ProfileService` | updateProfile, updateBio, changeAvatar | profile |
| `settings` | `SettingsService` | getSettings, updateSettings, labels | settings, label |
| `business` | `BusinessService` | getBizAccount, catalog CRUD, auto-reply, quick msg | catalog, auto_reply, quick_message |

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
└── ZavaException                       // Base: message + error code
    ├── ZavaCryptoException             // Encrypt/decrypt failure
    ├── ZavaAuthException               // Login failures (cookie/QR)
    └── ZavaTimeoutException            // Request/WS timeout
```

Tất cả exceptions là **unchecked** (extends RuntimeException):
- Consumer không bị bắt buộc try-catch mọi API call
- Consistent với các SDK hiện đại (OkHttp, Retrofit)
- Error code từ Zalo server được giữ nguyên trong `ZavaException.getCode()`

---

## 11. Design Decisions

### Blocking API (không dùng CompletableFuture)

**Lý do:**
- SDK đơn giản nhất có thể cho consumer
- Consumer tự chọn concurrency model (thread pool, virtual threads, reactive)
- Hầu hết use case (chatbot) không cần high throughput
- Nếu cần async, consumer wrap trong `CompletableFuture.supplyAsync()`

### Package-by-feature thay vì package-by-layer

**Lý do:**
- zca-js có 145+ methods trong 1 class `API` - flat structure, mọi thứ dump vào 1 chỗ
- Package-by-layer (`api/`, `model/`, `exception/`) vẫn là JS mindset trong Java clothing
- Package-by-feature: mỗi domain tự chứa service + model + enum, cohesion cao
- Xóa/thêm feature = xóa/thêm 1 package, không sờ vào nhiều nơi
- IDE navigation: mở `group/` thấy ngay tất cả liên quan đến groups

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

Tất cả nằm trong `dev.suprim.zava.internal.session.Constants.java`.
