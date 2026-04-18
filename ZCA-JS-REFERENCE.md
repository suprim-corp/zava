# ZCA-JS Architecture Analysis

> Tài liệu phân tích chi tiết kiến trúc của [zca-js](https://github.com/RFS-ADRENO/zca-js) v2.1.2
> Mục đích: Làm cơ sở cho việc port sang Java (project Zava)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Project Structure](#2-project-structure)
3. [Core Modules](#3-core-modules)
   - 3.1 [Zava (Entry Point)](#31-zava-entry-point---srczcalots)
   - 3.2 [Context (Session State)](#32-context-session-state---srccontextts)
   - 3.3 [Utils (Crypto/HTTP Core)](#33-utils-cryptohttp-core---srcutilsts)
   - 3.4 [API (Aggregator)](#34-api-aggregator---srcapists)
   - 3.5 [Listener (WebSocket)](#35-listener-websocket---srcapislistents)
   - 3.6 [Login](#36-login---srcapislogints--loginqrts)
   - 3.7 [SendMessage](#37-sendmessage---srcapissendmessagets)
   - 3.8 [UploadAttachment](#38-uploadattachment---srcapisuploadattachmentts)
4. [Data Models](#4-data-models)
5. [Error Hierarchy](#5-error-hierarchy)
6. [API Factory Pattern](#6-api-factory-pattern)
7. [Encryption & Crypto](#7-encryption--crypto)
8. [WebSocket Binary Protocol](#8-websocket-binary-protocol)
9. [Dependencies & Java Equivalents](#9-dependencies--java-equivalents)
10. [Java Port Notes](#10-java-port-notes)

---

## 1. Overview

**zca-js** là một API client không chính thức cho Zalo Web (chat.zalo.me). Nó simulate browser-based interaction với Zalo, cho phép:
- Đăng nhập bằng cookie hoặc QR code
- Gửi/nhận tin nhắn (text, attachment, quote, mention, styled text)
- Quản lý nhóm, bạn bè, sticker, poll, reminder, catalog...
- Lắng nghe real-time events qua WebSocket

**Runtime**: Node.js >= 18 (cũng support Bun)
**License**: MIT

---

## 2. Project Structure

```
zca-js/
├── src/
│   ├── index.ts              # Re-exports everything
│   ├── zalo.ts               # Zalo class - entry point (maps to Zava.java)
│   ├── apis.ts               # API class - aggregates 145+ API methods
│   ├── context.ts            # Session types, CallbacksMap, factory
│   ├── utils.ts              # Encryption, HTTP, crypto, apiFactory
│   ├── update.ts             # npm version checker
│   │
│   ├── apis/                 # ~149 individual API endpoint files
│   │   ├── listen.ts         # WebSocket Listener (EventEmitter)
│   │   ├── login.ts          # Cookie-based login
│   │   ├── loginQR.ts        # QR code login flow
│   │   ├── sendMessage.ts    # Send messages (text, attachments, quotes, mentions)
│   │   ├── uploadAttachment.ts  # Chunked file upload
│   │   ├── custom.ts         # Custom API extension point
│   │   └── ... (143 more)
│   │
│   ├── models/               # Data model types
│   │   ├── Message.ts        # UserMessage, GroupMessage
│   │   ├── Group.ts          # GroupInfo, GroupSetting, GroupTopic
│   │   ├── User.ts           # User, UserBasic, UserSetting
│   │   ├── Enum.ts           # ThreadType, Gender, AvatarSize, BinBankCard
│   │   ├── Attachment.ts     # AttachmentSource union
│   │   ├── Reaction.ts       # Reaction class, Reactions enum (56 values)
│   │   ├── GroupEvent.ts     # GroupEvent types (22 event types)
│   │   ├── FriendEvent.ts    # FriendEvent types (13 event types)
│   │   ├── Undo.ts           # Message deletion model
│   │   ├── Typing.ts         # Typing indicator models
│   │   ├── SeenMessage.ts    # Seen receipt models
│   │   ├── DeliveredMessage.ts # Delivery receipt models
│   │   ├── Board.ts          # Board, Poll, Note types
│   │   ├── Sticker.ts        # Sticker types
│   │   ├── Reminder.ts       # Reminder types
│   │   ├── Label.ts          # Label types
│   │   ├── Catalog.ts        # Catalog types
│   │   ├── ProductCatalog.ts # Product catalog types
│   │   ├── QuickMessage.ts   # Quick reply template types
│   │   ├── AutoReply.ts      # Auto-reply config types
│   │   └── ZBusiness.ts      # Business account types
│   │
│   └── Errors/               # Custom error classes
│       ├── ZaloApiError.ts
│       ├── ZaloApiMissingImageMetadataGetter.ts
│       ├── ZaloApiLoginQRAborted.ts
│       └── ZaloApiLoginQRDeclined.ts
│
├── examples/
│   ├── echobot.ts
│   └── login.ts
│
└── scripts/
    └── buildAPI.js           # Code generator for apis.ts
```

---

## 3. Core Modules

### 3.1 Zava (Entry Point) - `src/zalo.ts`

**Class `Zalo`** (trong zca-js, maps to `Zava` trong Java): Entry point. Constructor nhận `Partial<Options>`.

```
Zava (Zalo in zca-js)
├── enableEncryptParam: boolean = true        // luôn true
├── options: Partial<Options>
│
├── login(credentials: Credentials) -> Promise<API>
│   1. createContext(apiType, apiVersion)
│   2. validateParams(imei, cookie, userAgent)
│   3. parseCookies() -> CookieJar
│   4. login(ctx, enableEncryptParam)         // gọi getLoginInfo
│   5. getServerInfo(ctx, enableEncryptParam)  // gọi getServerInfo
│   6. Set ctx: secretKey, uid, settings, extraVer, loginInfo
│   7. return new API(ctx, zpw_service_map_v3, zpw_ws)
│
├── loginQR(options?, callback?) -> Promise<API>
│   1. createContext()
│   2. loginQR(ctx, options, callback)        // multi-step QR flow
│   3. generateZaloUUID(userAgent)            // tạo imei
│   4. callback(GotLoginInfo)                 // emit login info
│   5. loginCookie(ctx, credentials)          // reuse cookie flow
│
└── parseCookies(cookie) -> CookieJar
    - Xử lý 3 định dạng: Cookie[], SerializedCookie[], {url, cookies}
    - Strip leading dot từ domain
    - Set vào toughCookie.CookieJar
```

**Type `Credentials`**:
```
{
  imei: string                     // device ID, UUID + MD5(userAgent)
  cookie: Cookie[] | SerializedCookie[] | { url: string; cookies: Cookie[] }
  userAgent: string
  language?: string                // default "vi"
}
```

**Type `Cookie`**:
```
{
  domain: string
  expirationDate: number
  hostOnly: boolean
  httpOnly: boolean
  name: string
  path: string
  sameSite: string
  secure: boolean
  session: boolean
  storeId: string
  value: string
}
```

---

### 3.2 Context (Session State) - `src/context.ts`

Session state được truyền qua mọi API call. Có 2 giai đoạn:

**`ContextBase`** (partial, trước khi login xong):
```
Partial<AppContextBase> & AppContextExtended
```

**`ContextSession`** (đầy đủ, sau login):
```
AppContextBase & AppContextExtended & { secretKey: string }
```

**`AppContextBase`**:
```
{
  uid: string                       // user ID
  imei: string                      // device ID
  cookie: CookieJar                 // tough-cookie jar
  userAgent: string
  language: string
  secretKey: string | null          // AES key from login (zpw_enk)
  zpwServiceMap: ZPWServiceMap      // service name -> URL[] mapping
  settings: {
    features: {
      sharefile: ShareFileSettings
      socket: SocketSettings
      [key: string]: any
    }
    keepalive: {
      alway_keepalive: number
      keepalive_duration: number
      time_deactive: number
    }
  }
  loginInfo: LoginInfo
  extraVer: ExtraVer
}
```

**`AppContextExtended`**:
```
{
  uploadCallbacks: CallbacksMap     // TTL-enabled Map<string, UploadCallback>
  options: Options
  readonly API_TYPE: number         // default 30
  readonly API_VERSION: number      // default 671
}
```

**`Options`**:
```
{
  selfListen: boolean               // default false - nghe tin tu chinh minh?
  checkUpdate: boolean              // default true
  logging: boolean                  // default true
  apiType: number                   // default 30
  apiVersion: number                // default 671
  agent?: Agent                     // HTTP proxy agent
  polyfill: typeof fetch            // custom fetch impl
  imageMetadataGetter?: (filePath: string) => Promise<{width, height, size} | null>
}
```

**`ShareFileSettings`**:
```
{
  big_file_domain_list: string[]
  max_size_share_file_v2: number
  max_size_share_file_v3: number     // MB, dùng để validate
  file_upload_show_icon_1GB: boolean
  restricted_ext: string
  next_file_time: number
  max_file: number                   // max số file/lần gửi
  max_size_photo: number
  max_size_share_file: number
  max_size_resize_photo: number
  max_size_gif: number
  max_size_original_photo: number
  chunk_size_file: number            // chunk size cho upload
  restricted_ext_file: string[]      // extension bị cấm
}
```

**`SocketSettings`**:
```
{
  rotate_error_codes: number[]       // error codes trigger endpoint rotation
  retries: {                         // retry config per close code
    [code: string]: {
      max?: number
      times: number[] | number       // retry delays in ms
    }
  }
  ping_interval: number              // ms between pings
  close_and_retry_codes: number[]    // codes that allow auto-retry
  max_msg_size: number
  reset_endpoint: number
  ...
}
```

**`ZPWServiceMap`** (~40 services):
```
{
  chat: string[]          // chat endpoints
  group: string[]         // group endpoints
  file: string[]          // file upload/download
  friend: string[]        // friend operations
  reaction: string[]      // reactions
  sticker: string[]       // stickers
  profile: string[]       // profile management
  alias: string[]         // friend alias
  label: string[]         // labels
  conversation: string[]  // conversation management
  boards: string[]        // boards/polls/notes
  catalog: string[]       // business catalog
  auto_reply: string[]    // auto-reply
  quick_message: string[] // quick messages
  group_poll: string[]    // group polls
  ... (25+ more)
}
```

**`CallbacksMap`** (extends `Map<string, UploadCallback>`):
- `set(key, value, ttl = 5min)` -- auto-deletes entry after TTL
- Dùng để link async file upload với WebSocket completion event

**`LoginInfo`**:
```
{
  haspcclient: number
  public_ip: string
  language: string
  send2me_id: string
  uid: string
  zpw_enk: string              // <<< SECRET KEY cho AES encrypt/decrypt
  zpw_service_map_v3: ZPWServiceMap
  zpw_ws: string[]             // WebSocket URLs
  [key: string]: any
}
```

---

### 3.3 Utils (Crypto/HTTP Core) - `src/utils.ts`

Đây là backbone của thư viện. 791 dòng bao gồm:

#### 3.3.1 Encryption Functions

**`getSignKey(type, params) -> string`**
```
1. Sort param keys alphabetically
2. Concatenate: "zsecure" + type + value_of_each_sorted_param
3. Return MD5(concatenated_string)
```
Ví dụ: `getSignKey("getlogininfo", {imei: "abc", ts: 123})` -> MD5("zsecuregetlogininfo" + "abc" + 123)

**`ParamsEncryptor`** (class, chỉ dùng khi login):
```
Constructor({ type: number, imei: string, firstLaunchTime: number }):
  1. createZcid(type, imei, firstLaunchTime):
     - msg = "{type},{imei},{firstLaunchTime}"
     - zcid = AES-CBC-encrypt(msg, key="3FC4F0D2AB50057BCE0D90D9187A22B1", hex, uppercase)
     - IV = zero (16 bytes of 0)
     - Padding = PKCS7
  
  2. zcid_ext = randomString(6, 12)  // random hex string 6-12 chars
  
  3. createEncryptKey():
     - md5Hash = MD5(zcid_ext).toUpperCase()
     - processStr(md5Hash) -> {even: [char at 0,2,4,...], odd: [char at 1,3,5,...]}
     - processStr(zcid) -> {even: [...], odd: [...]}
     - encryptKey = md5Hash.even[0..7] + zcid.even[0..11] + zcid.odd.reverse()[0..11]
     - Total: 8 + 12 + 12 = 32 chars

  getParams() -> { zcid, zcid_ext, enc_ver: "v2" }
  getEncryptKey() -> string (32 chars)

  static encodeAES(key, message, type: "hex"|"base64", uppercase, retry=0) -> string:
     - AES-CBC encrypt
     - Key = parse key as UTF-8
     - IV = zero (16 bytes)
     - Padding = PKCS7
     - Output = hex or base64
     - Retry up to 3 times on failure
```

**`encodeAES(secretKey, data, retry=0) -> string | null`** (session encrypt):
```
- Key = Base64.parse(secretKey)  <<< KHÁC với ParamsEncryptor dùng UTF-8
- IV = "00000000000000000000000000000000" (hex, 16 bytes zero)
- Mode = CBC
- Padding = PKCS7
- Output = Base64
- Retry up to 3 times
```

**`decodeAES(secretKey, data, retry=0) -> string | null`** (session decrypt):
```
- data = decodeURIComponent(data)
- Key = Base64.parse(secretKey)
- IV = zero
- Mode = CBC
- Padding = PKCS7
- Output = UTF-8 string
- Retry up to 3 times
```

**`decryptResp(key, data) -> Record | string | null`** (login response decrypt):
```
- Khác với decodeAES: key được parse bằng UTF-8 (không phải Base64)
- data = decodeURIComponent(data)
- AES-CBC decrypt với zero IV
- Try JSON.parse, fallback to raw string
```

**`decodeEventData(parsed, cipherKey?) -> any`** (WebSocket event decrypt):
```
Input: { data: string, encrypt: 0|1|2|3 }

encrypt=0: return JSON.parse(data)

encrypt=1: 
  1. Base64 decode data -> buffer
  2. pako.inflate(buffer)            // zlib decompress
  3. TextDecoder decode
  4. JSONBig.parse                   // safe BigInt parsing

encrypt=2:
  1. decodeURIComponent(data)
  2. Base64 decode -> buffer (>= 48 bytes)
  3. IV = buffer[0..15]              // first 16 bytes
  4. AAD = buffer[16..31]            // next 16 bytes (additionalData)
  5. ciphertext = buffer[32..]
  6. AES-GCM decrypt (key=cipherKey, iv, tagLength=128, aad)
  7. pako.inflate                    // zlib decompress
  8. JSONBig.parse

encrypt=3: Same as 2 but NO zlib decompress
```

#### 3.3.2 HTTP Functions

**`request(ctx, url, options?, raw=false) -> Response`**:
```
1. Set default headers (Accept, Content-Type, Cookie, User-Agent, Origin, Referer)
2. Handle Bun vs Node.js proxy config
3. Call ctx.options.polyfill(url, options)  // global.fetch or custom
4. Parse Set-Cookie headers -> store in CookieJar
   - Dùng getSetCookie() (Node 18+) để handle multi-cookie headers đúng
   - Fallback: split(", ") -- CAVEAT: breaks cookies with Expires dates
5. Handle redirects: follow Location header, change method to GET
6. Return response
```

**`makeURL(ctx, baseURL, params?, apiVersion=true) -> string`**:
```
1. Append params as query string
2. If apiVersion: add zpw_ver={API_VERSION} & zpw_type={API_TYPE}
```

**`getDefaultHeaders(ctx, origin) -> Record`**:
```
{
  Accept: "application/json, text/plain, */*"
  Accept-Encoding: "gzip, deflate, br, zstd"
  Accept-Language: "en-US,en;q=0.9"
  content-type: "application/x-www-form-urlencoded"
  Cookie: <from CookieJar>
  Origin: "https://chat.zalo.me"
  Referer: "https://chat.zalo.me/"
  User-Agent: <from ctx>
}
```

#### 3.3.3 Response Handling

**`handleZaloResponse<T>(ctx, response, isEncrypted=true) -> ZaloResponse<T>`**:
```
ZaloResponse<T> = { data: T | null, error: { message, code? } | null }

1. Check response.ok
2. Parse JSON: { error_code, error_message, data }
3. If error_code != 0: return error
4. If isEncrypted: decodeAES(secretKey, data) -> parse JSON
5. Check inner error_code != 0
6. Return { data: decodedData.data }
```

**`resolveResponse<T>(ctx, res, cb?, isEncrypted?) -> T`**:
```
1. Call handleZaloResponse
2. If error: throw ZaloApiError(message, code)
3. If cb: return cb(result)
4. Return result.data as T
```

#### 3.3.4 File Utilities

**`getMd5LargeFileObject(source, fileSize) -> { currentChunk, data: string }`**:
```
- Read file in 2MB chunks
- SparkMD5.ArrayBuffer for incremental MD5
- Return hex MD5 hash
```

**`getImageMetaData(ctx, filePath) -> { fileName, totalSize, width, height }`**
- Delegate to ctx.options.imageMetadataGetter

**`getFileSize(filePath) -> number`** - fs.stat

**`getGifMetaData(ctx, filePath) -> { fileName, totalSize, width, height }`**
- Delegate to ctx.options.imageMetadataGetter

#### 3.3.5 API Factory

**`apiFactory<T>() -> (callback) -> (ctx, api) -> ReturnType<callback>`**:
```typescript
// Double-curried factory:
apiFactory<ResponseType>()(
  (api: API, ctx: ContextSession, utils: FactoryUtils<T>) => {
    // Setup service URLs, closures
    return async function apiMethodName(params...) {
      // Implementation
    };
  }
);

// FactoryUtils<T>:
{
  makeURL:   (baseURL, params?, apiVersion?) => string
  encodeAES: (data, retry?) => string | null       // binds ctx.secretKey
  request:   (url, options?, raw?) => Response      // binds ctx
  logger:    { verbose, info, warn, error, success, timestamp }
  resolve:   (res, cb?, isEncrypted?) => T          // binds ctx
}
```

#### 3.3.6 Utility Functions

```
getClientMessageType(msgType: string) -> number
  webchat -> 1, chat.voice -> 31, chat.photo -> 32, chat.sticker -> 36,
  chat.doodle -> 37, chat.recommended -> 38, chat.link -> 38,
  chat.video.msg -> 44, share.file -> 46, chat.gif -> 49,
  chat.location.new -> 43

getGroupEventType(act: string) -> GroupEventType  // string -> enum mapping
getFriendEventType(act: string) -> FriendEventType

generateZaloUUID(userAgent) -> string
  = crypto.randomUUID() + "-" + MD5(userAgent)

encryptPin(pin: string) -> string                 // MD5 hash
validatePin(encryptedPin, pin) -> boolean          // compare MD5

hexToNegativeColor(hex) -> number                 // "#00FF00" -> -16711936
negativeColorToHex(number) -> string              // -16711936 -> "#00FF00"
```

---

### 3.4 API (Aggregator) - `src/apis.ts`

**Class `API`**: Tap hop 145+ public methods + Listener.

```
API
├── zpwServiceMap: ZPWServiceMap
├── listener: Listener
│
├── constructor(ctx: ContextSession, zpwServiceMap, wsUrls[])
│   1. Store zpwServiceMap
│   2. Create Listener(ctx, wsUrls)
│   3. Initialize ALL 145+ methods via factory pattern:
│      this.sendMessage = sendMessageFactory(ctx, this)
│      this.addReaction = addReactionFactory(ctx, this)
│      ... (one line per API)
│
├── [145+ methods - each created via apiFactory]
│   - Messaging: sendMessage, sendLink, sendCard, sendBankCard, sendSticker,
│                sendVideo, sendVoice, forwardMessage, deleteMessage, undo
│   - Events: sendTypingEvent, sendSeenEvent, sendDeliveredEvent
│   - Groups: createGroup, addUserToGroup, removeUserFromGroup, changeGroupName,
│             changeGroupAvatar, changeGroupOwner, updateGroupSettings,
│             getGroupInfo, getGroupMembersInfo, getGroupChatHistory, ...
│   - Friends: findUser, getUserInfo, sendFriendRequest, acceptFriendRequest,
│              removeFriend, blockUser, getAllFriends, ...
│   - Profile: updateProfile, updateProfileBio, changeAccountAvatar, ...
│   - Stickers: getStickers, searchSticker, sendSticker, ...
│   - Polls: createPoll, addPollOptions, votePoll, lockPoll, sharePoll
│   - Business: getBizAccount, getCatalogList, createCatalog, ...
│   - Settings: getSettings, updateSettings, getLabels, updateLabels, ...
│   - Conversation: deleteChat, setPinnedConversations, setMute, ...
│   - Custom: custom (register arbitrary API methods at runtime)
│
└── custom: allows Object.defineProperty to add new methods dynamically
```

Mỗi method được tạo bởi pattern:
```
this.methodName = methodNameFactory(ctx, this);
```
=> `methodNameFactory` là kết quả của `apiFactory<T>()(callback)` được export từ file API tương ứng.

**Lưu ý cho Java port**: Class API trong TS rất đơn giản - chỉ là nơi chứa tất cả methods.
Trong Java, có thể dùng interface + implementation, hoặc chia thành service classes theo domain
(MessageService, GroupService, FriendService, ...).

---

### 3.5 Listener (WebSocket) - `src/apis/listen.ts`

**Class `Listener`** (extends `EventEmitter<ListenerEvents>`):

```
Listener
├── Fields:
│   ├── wsURL: string                    // current WS endpoint
│   ├── cookie: string                   // cookie string
│   ├── userAgent: string
│   ├── ws: WebSocket | null
│   ├── cipherKey?: string               // received on connect
│   ├── selfListen: boolean
│   ├── pingInterval?: Timer
│   ├── id: number                       // auto-increment request ID
│   ├── retryCount: Record<string, {count, max, times[]}>
│   ├── rotateCount: number
│   └── urls: string[]                   // all WS endpoints
│
├── Events emitted:
│   ├── connected: []
│   ├── disconnected: [code, reason]
│   ├── closed: [code, reason]
│   ├── error: [error]
│   ├── message: [Message]               // UserMessage | GroupMessage
│   ├── old_messages: [Message[], ThreadType]
│   ├── typing: [Typing]
│   ├── reaction: [Reaction]
│   ├── old_reactions: [Reaction[], isGroup]
│   ├── upload_attachment: [UploadEventData]
│   ├── undo: [Undo]
│   ├── friend_event: [FriendEvent]
│   ├── group_event: [GroupEvent]
│   ├── seen_messages: [SeenMessage[]]
│   ├── delivered_messages: [DeliveredMessage[]]
│   └── cipher_key: [key]
│
├── start({ retryOnClose? }) -> void
│   1. Create WebSocket with headers (cookie, user-agent, origin)
│   2. onopen: emit "connected"
│   3. onclose: reset(), emit "disconnected"
│      - If retryOnClose && canRetry(code): rotate endpoint + setTimeout -> start()
│      - Else: emit "closed"
│   4. onerror: emit "error"
│   5. onmessage: see Message Handling below
│
├── stop() -> void
│   - ws.close(1000), reset()
│
├── sendWs(payload: WsPayload, requireId=true)
│   - Encode 4-byte header + JSON payload
│   - Send binary via ws.send()
│
├── requestOldMessages(threadType, lastMsgId?)
│   - Send cmd=510 (User) or cmd=511 (Group), subCmd=1
│
└── requestOldReactions(threadType, lastMsgId?)
    - Send cmd=610 (User) or cmd=611 (Group), subCmd=1
```

**Message Handling (onmessage)**:
```
1. Extract 4-byte header from Buffer
2. Parse JSON from remaining bytes

cmd=1, subCmd=1:  Cipher key exchange
  - Store cipherKey
  - Start ping interval (cmd=2, subCmd=1, data={eventId: timestamp})

cmd=501, subCmd=0:  User messages
  - decodeEventData(parsed, cipherKey)
  - For each msg:
    - If content.deleteMsg: emit "undo" (Undo object)
    - Else: emit "message" (UserMessage object)
  - Filter by selfListen

cmd=521, subCmd=0:  Group messages
  - Same as 501 but GroupMessage + groupMsgs

cmd=601, subCmd=0:  Control events
  - act_type="file_done":  Trigger uploadCallback, emit "upload_attachment"
  - act_type="group":      Parse data, initializeGroupEvent(), emit "group_event"
    - SKIP "join_reject" (Zalo bug: sends both join + join_reject for approved)
  - act_type="fr":         Parse data, initializeFriendEvent(), emit "friend_event"
    - SKIP "req" (Zalo sends both req + req_v2)

cmd=612:              Reactions
  - Parse reacts[] and reactGroups[]
  - emit "reaction" for each

cmd=610/611:          Old reactions (user/group)
  - emit "old_reactions"

cmd=510, subCmd=1:    Old user messages
  - emit "old_messages"

cmd=511, subCmd=1:    Old group messages
  - emit "old_messages"

cmd=602, subCmd=0:    Typing
  - act_type="typing", act="typing" -> UserTyping
  - act_type="typing", act="gtyping" -> GroupTyping
  - emit "typing"

cmd=502, subCmd=0:    User seen/delivered
  - Parse delivereds[] -> UserDeliveredMessage
  - Parse seens[] -> UserSeenMessage

cmd=522, subCmd=0:    Group seen/delivered
  - Parse delivereds[] -> GroupDeliveredMessage
  - Parse groupSeens[] -> GroupSeenMessage

cmd=3000, subCmd=0:   Duplicate connection
  - Log error, close with code 3000
```

**CloseReason enum**:
```
ManualClosure = 1000
AbnormalClosure = 1006
DuplicateConnection = 3000
KickConnection = 3003
```

---

### 3.6 Login - `src/apis/login.ts` & `loginQR.ts`

#### Cookie Login (`login.ts`)

```
login(ctx, encryptParams) -> loginData
  1. getEncryptParam(ctx, encryptParams, "getlogininfo")
     a. Build data = { computer_name, imei, language, ts }
     b. If encryptParams:
        - Create ParamsEncryptor({ type, imei, firstLaunchTime })
        - JSON.stringify(data)
        - Encrypt with ParamsEncryptor.encodeAES(encryptKey, data, "base64", false)
        - Return { encrypted_data, encrypted_params: {zcid, zcid_ext, enc_ver}, enk }
     c. Else: return raw data
  2. Add type, client_version, signkey to params
     - signkey = getSignKey("getlogininfo", params)
  3. GET https://wpa.chat.zalo.me/api/login/getLoginInfo?{params}
  4. If encrypted: decryptResp(enk, data.data) -> loginData

getServerInfo(ctx, encryptParams) -> serverInfo
  1. getEncryptParam(ctx, encryptParams, "getserverinfo")
  2. signkey = getSignKey("getserverinfo", {imei, type, client_version, computer_name})
  3. GET https://wpa.chat.zalo.me/api/login/getServerInfo?{params}
  4. Return data.data (contains settings, extra_ver)
```

#### QR Login (`loginQR.ts`)

Multi-step flow simulating browser:

```
loginQR(ctx, options, callback?) -> { cookies, userInfo }
  1. loadLoginPage(ctx)
     - GET https://id.zalo.me/account?continue=https://chat.zalo.me/
     - Parse HTML to extract JS version from main-{version}.js URL
     
  2. getLoginInfo(ctx, version)
     - POST https://id.zalo.me/account/logininfo
     - Body: continue=https://zalo.me/pc&v={version}
     
  3. verifyClient(ctx, version)
     - POST https://id.zalo.me/account/verify-client
     - Body: type=device&continue=https://zalo.me/pc&v={version}
     
  4. generate(ctx, version)
     - POST https://id.zalo.me/account/authen/qr/generate
     - Returns: { code, image (base64 PNG), token, options }
     - callback(QRCodeGenerated) hoac save to file
     
  5. Set QR expiry timeout (100 seconds)
     - On expire: callback(QRCodeExpired) hoac retry()
     
  6. waitingScan(ctx, version, code, signal)
     - POST https://id.zalo.me/account/authen/qr/waiting-scan
     - Long-poll: if error_code=8, recurse (timeout retry)
     - Returns: { avatar, display_name }
     - callback(QRCodeScanned)
     
  7. waitingConfirm(ctx, version, code, signal)
     - POST https://id.zalo.me/account/authen/qr/waiting-confirm
     - Long-poll: if error_code=8, recurse
     - If error_code=-13: declined -> callback(QRCodeDeclined) or throw
     
  8. checkSession(ctx)
     - GET https://id.zalo.me/account/checksession?continue=...
     - Manual redirect handling
     
  9. getUserInfo(ctx)
     - GET https://jr.chat.zalo.me/jr/userinfo
     - Verify logged=true
     
  10. Return { cookies: cookieJar.toJSON().cookies, userInfo }
```

**LoginQRCallbackEventType enum**:
```
QRCodeGenerated    // QR image ready
QRCodeExpired      // QR expired (100s)
QRCodeScanned      // Phone scanned QR
QRCodeDeclined     // User declined on phone
GotLoginInfo       // Cookies + IMEI ready
```

---

### 3.7 SendMessage - `src/apis/sendMessage.ts`

Complex API handling text, attachments, mentions, quotes, styles.

```
sendMessage(message: MessageContent | string, threadId, type) -> SendMessageResponse

MessageContent:
{
  msg: string                        // text content
  styles?: Style[]                   // bold, italic, colors, lists
  urgency?: Urgency                  // Default, Important, Urgent
  quote?: SendMessageQuote           // quoted message
  mentions?: Mention[]               // @mentions (group only)
  attachments?: AttachmentSource[]   // files to send
  ttl?: number                       // time to live
}

Flow:
1. If attachments:
   a. If can't be desc (multi-file, non-image, has quote + msg):
      - Send text message separately first
   b. Call api.uploadAttachment() for non-gif files
   c. For each uploaded file:
      - image: Build params with photoId, width, height, rawUrl, hdUrl, thumbUrl, groupLayout
      - video: Build params with fileId, checksum, fileName, fileUrl
      - others: Same as video
   d. For gif files:
      - upthumb() to get thumbnail URL
      - Build FormData with chunkContent
      - Calculate MD5 checksum
   e. Encrypt params, POST to file service

2. If msg text:
   a. Handle mentions: validate pos, uid, len
   b. Handle styles: map TextStyle to JSON
   c. Handle urgency: add metaData
   d. Build params:
      - User: toid={threadId}, imei
      - Group: grid={threadId}, visibility=0
   e. If quote: add qmsgOwner, qmsgId, qmsgCliId, qmsgType, qmsgTs, qmsg, qmsgAttach
   f. Encrypt params
   g. POST to:
      - User: chat[0]/api/message/sms (normal) or /quote
      - Group: group[0]/api/group/sendmsg or /mention or /quote

Service URLs:
  message:
    User:  {chat[0]}/api/message?nretry=0
    Group: {group[0]}/api/group?nretry=0
  attachment:
    User:  {file[0]}/api/message/
    Group: {file[0]}/api/group/

Attachment URL types:
  image:  photo_original/send?
  gif:    gif?
  video:  asyncfile/msg?
  others: asyncfile/msg?
```

**TextStyle enum**:
```
Bold="b", Italic="i", Underline="u", StrikeThrough="s",
Red="c_db342e", Orange="c_f27806", Yellow="c_f7b503", Green="c_15a85f",
Small="f_13", Big="f_18",
UnorderedList="lst_1", OrderedList="lst_2",
Indent="ind_$"  // $ replaced with {indentSize}0
```

**Urgency enum**: `Default=0, Important=1, Urgent=2`

---

### 3.8 UploadAttachment - `src/apis/uploadAttachment.ts`

Chunked file upload:

```
uploadAttachment(sources, threadId, type) -> UploadAttachmentType[]

Flow:
1. Validate: sources not empty, not exceed max_file, extension valid
2. For each source:
   a. Determine fileType by extension:
      - jpg/jpeg/png/webp -> "image"
      - mp4 -> "video"
      - others -> "others"
   b. Get file data (size, image metadata if applicable)
   c. Validate file size against max_size_share_file_v3
   d. Calculate totalChunk = ceil(totalSize / chunk_size_file)
   e. Slice file buffer into chunks -> FormData with "chunkContent"
   
3. For each attachment, for each chunk:
   a. Encrypt params
   b. POST to {file[0]}/api/{message|group}/{photo_original/upload|asyncfile/upload}
   c. Parse response
   d. For video/others:
      - fileId != -1: Register callback in uploadCallbacks
      - Wait for WebSocket "file_done" event (cmd=601)
      - Callback resolves with { fileUrl, fileId, checksum }
   e. For image:
      - Resolve immediately with { normalUrl, hdUrl, thumbUrl, photoId, width, height }

Response types:
  UploadAttachmentImageResponse: { normalUrl, photoId, hdUrl, thumbUrl, width, height, totalSize, ... }
  UploadAttachmentVideoResponse: { fileUrl, fileId, checksum, totalSize, fileName, ... }
  UploadAttachmentFileResponse:  { fileUrl, fileId, checksum, totalSize, fileName, ... }
```

---

## 4. Data Models

### 4.1 Core Enums

```
ThreadType:     User=0, Group=1
DestType:       Group=1, User=3, Page=5
Gender:         Male=0, Female=1
AvatarSize:     Small=120, Large=240
BinBankCard:    55 Vietnamese bank BIN codes (e.g. Vietcombank=970436)
BoardType:      Note=1, PinnedMessage=2, Poll=3
GroupType:      Group=1, Community=2
GroupTopicType: Note=0, Message=2, Poll=3
ReminderRepeatMode: None=0, Daily=1, Weekly=2, Monthly=3
AutoReplyScope: Everyone=0, Stranger=1, SpecificFriends=2, FriendsExcept=3
BusinessCategory: Other=0, RealEstate=1, ... (15 categories)
```

### 4.2 Common Wrapper Pattern

Hầu hết models đều dùng discriminated union pattern:
```
{
  type: ThreadType.User | ThreadType.Group
  data: T<specific raw data>
  threadId: string
  isSelf: boolean
}
```

Áp dụng cho: Message, DeliveredMessage, SeenMessage, Typing, Reaction, Undo, GroupEvent, FriendEvent

### 4.3 Message Models

**`TMessage`** (raw wire data):
```
{
  actionId: string
  msgId: string
  cliMsgId: string                    // client-generated message ID
  msgType: string                     // "webchat", "chat.photo", "chat.sticker", ...
  uidFrom: string                     // "0" = self
  idTo: string
  dName: string                       // display name
  ts: string                          // timestamp
  status: number
  content: string | TAttachmentContent | TOtherContent
  notify: string
  ttl: number
  userId: string
  uin: string
  cmd: number
  st: number
  at: number
  realMsgId: string
  quote?: TQuote
  propertyExt?: { color, size, type, subType, ext }
  paramsExt: { countUnread, containType, platformType }
}
```

**`TGroupMessage`** (extends TMessage):
```
+ mentions?: TMention[]              // { uid, pos, len, type: 0|1 }
```

**`TAttachmentContent`**:
```
{ title, description, href, thumb, childnumber, action, params, type }
```

**`TQuote`**:
```
{ ownerId, cliMsgId, globalMsgId, cliMsgType, ts, msg, attach, fromD, ttl }
```

**Class `UserMessage`**: `{ type: User, data: TMessage, threadId, isSelf }`
- threadId = isSelf ? data.idTo : data.uidFrom

**Class `GroupMessage`**: `{ type: Group, data: TGroupMessage, threadId, isSelf }`
- threadId = data.idTo (always group ID)
- isSelf = uidFrom == "0"

### 4.4 Group Models

**`GroupInfo`**:
```
{
  groupId, name, desc, type: GroupType, creatorId, version, avt, fullAvt,
  memberIds: string[], adminIds: string[], currentMems: GroupCurrentMem[],
  totalMember, maxMember, setting: GroupSetting, createdTime, visibility,
  globalId, e2ee, extraInfo: { enable_media_store }
}
```

**`GroupSetting`**:
```
{
  blockName, signAdminMsg, addMemberOnly, setTopicOnly, enableMsgHistory,
  joinAppr, lockCreatePost, lockCreatePoll, lockSendMsg, lockViewMember,
  bannFeature, dirtyMedia, banDuration
}
```

**`GroupTopic`** (pinned topics):
```
{
  type: GroupTopicType, color, emoji, startTime, duration,
  params: NoteParams | MessageParams | PollParams | OtherParams,
  id, creatorId, createTime, editorId, editTime, repeat, action
}
```

### 4.5 User Models

**`User`**:
```
{
  userId, username, displayName, zaloName, avatar, bgavatar, cover,
  gender: Gender, dob, sdob, status, phoneNumber,
  isFr, isBlocked, lastActionTime, lastUpdateTime,
  isActive, isActivePC, isActiveWeb, isValid,
  key, type, userKey, accountStatus, oaInfo, user_mode,
  globalId, bizPkg: ZBusinessPackage, createdTs, oa_status
}
```

**`UserBasic`**:
```
{ avatar, cover, status, gender, dob, sdob, globalId, bizPkg, uid, zalo_name, display_name }
```

### 4.6 Event Models

**`GroupEvent`** (22 types):
```
type GroupEventType:
  JOIN_REQUEST, JOIN, LEAVE, REMOVE_MEMBER, BLOCK_MEMBER,
  UPDATE_SETTING, UPDATE, NEW_LINK, ADD_ADMIN, REMOVE_ADMIN, UPDATE_AVATAR,
  NEW_PIN_TOPIC, UPDATE_PIN_TOPIC, REORDER_PIN_TOPIC,
  UPDATE_BOARD, REMOVE_BOARD, UPDATE_TOPIC, UNPIN_TOPIC, REMOVE_TOPIC,
  ACCEPT_REMIND, REJECT_REMIND, REMIND_TOPIC, UNKNOWN
```

**`FriendEvent`** (13 types):
```
type FriendEventType:
  ADD, REMOVE, REQUEST, UNDO_REQUEST, REJECT_REQUEST, SEEN_FRIEND_REQUEST,
  BLOCK, UNBLOCK, BLOCK_CALL, UNBLOCK_CALL,
  PIN_UNPIN, PIN_CREATE, UNKNOWN
```

### 4.7 Reaction Model

**`Reactions`** enum: 56 emoji codes
```
HEART="/-heart", LIKE="/-strong", HAHA=":>", WOW=":o", CRY=":-((", ANGRY=":-h",
KISS=":-*", TEARS_OF_JOY=":')", SHIT="/-shit", ROSE="/-rose", ...
```

**`TReaction`**:
```
{
  actionId, msgId, cliMsgId, msgType, uidFrom, idTo, dName?, ts, ttl,
  content: {
    rMsg: [{ gMsgID, cMsgID, msgType }],  // target messages
    rIcon: Reactions,                       // reaction emoji
    rType: number,
    source: number
  }
}
```

### 4.8 Other Models

**`Undo`**: Same envelope as TMessage but content = `{ globalMsgId, cliMsgId, deleteMsg, srcId, destId }`

**`Typing`**: `{ uid, ts, isPC: 0|1 }` + Group adds `gid`

**`SeenMessage`**: User: `{ idTo, msgId, realMsgId }` / Group: `{ msgId, groupId, seenUids[] }`

**`DeliveredMessage`**: `{ msgId, seen, deliveredUids[], seenUids[], realMsgId, mSTs }` + Group adds `groupId`

**`AttachmentSource`**: `string` (file path) | `{ data: Buffer, filename: \`${string}.${string}\`, metadata }`

**`PollDetail`**: `{ creator, question, options[], joined, closed, poll_id, allow_multi_choices, ... }`

**`ReminderUser`**: `{ creatorUid, toUid, emoji, color, reminderId, repeat, startTime, params: {title} }`

**`ReminderGroup`**: `{ editorId, emoji, color, groupId, creatorId, responseMem, params: {title}, repeat, ... }`

**`CatalogItem`**: `{ id, name, version, ownerId, isDefault, path, catalogPhoto, totalProduct, created_time }`

**`ProductCatalogItem`**: `{ price, description, path, product_id, product_name, currency_unit, product_photos[], catalog_id, owner_id }`

**`LabelData`**: `{ id, text, textKey, conversations[], color, offset, emoji, createTime }`

**`QuickMessage`**: `{ id, keyword, type, createdTime, lastModified, message: {title, params}, media }`

**`AutoReplyItem`**: `{ id, weight, enable, startTime, endTime, content, scope: AutoReplyScope, uids, recurrence }`

**`StickerDetail`**: `{ id, cateId, type, text, uri, stickerUrl, stickerSpriteUrl, stickerWebpUrl, totalFrames, duration, ... }`

**`ZBusinessPackage`**: `{ label?: Record<string,string> | null, pkgId: number }`

---

## 5. Error Hierarchy

```
Error
└── ZaloApiError
    ├── code: number | null
    ├── name: "ZcaApiError"
    │
    ├── ZaloApiLoginQRAborted
    │   └── name: "ZaloApiLoginQRAborted"
    │   └── default: "Operation aborted"
    │
    ├── ZaloApiLoginQRDeclined
    │   └── name: "ZaloApiLoginQRDeclined"
    │   └── default: "Login QR request declined"
    │
    └── ZaloApiMissingImageMetadataGetter
        └── hardcoded message about missing imageMetadataGetter
```

---

## 6. API Factory Pattern

Mỗi API endpoint file follow cùng pattern:

```typescript
// file: src/apis/someApi.ts
export type SomeApiResponse = { ... };

export const someApiFactory = apiFactory<SomeApiResponse>()(
  (api, ctx, utils) => {
    // 1. Setup: Build service URLs from api.zpwServiceMap
    const serviceURL = utils.makeURL(`${api.zpwServiceMap.chat[0]}/api/...`);

    // 2. Return the actual function
    return async function someApi(param1: string, param2?: number) {
      // 3. Validate
      if (!param1) throw new ZaloApiError("Missing param1");

      // 4. Build params
      const params = {
        param1,
        param2: param2 ?? defaultValue,
        imei: ctx.imei,
      };

      // 5. Encrypt
      const encryptedParams = utils.encodeAES(JSON.stringify(params));
      if (!encryptedParams) throw new ZaloApiError("Failed to encrypt");

      // 6. HTTP request (GET or POST)
      // GET style:
      const response = await utils.request(
        utils.makeURL(serviceURL, { params: encryptedParams })
      );
      // POST style:
      const response = await utils.request(serviceURL, {
        method: "POST",
        body: new URLSearchParams({ params: encryptedParams }),
      });

      // 7. Resolve
      return utils.resolve(response);
      // or with transform:
      return utils.resolve(response, (result) => transformResult(result));
    };
  }
);
```

**API categories** (by service URL):
| Service | APIs | Count |
|---------|------|-------|
| chat | sendMessage (sms/quote), keepAlive, sendSeenEvent, sendDeliveredEvent | ~5 |
| group | createGroup, addUser, removeUser, changeSettings, getGroupInfo, ... | ~30 |
| friend | findUser, sendFriendRequest, getAllFriends, blockUser, ... | ~20 |
| file | uploadAttachment, sendMessage (attachments), sendVideo, sendVoice | ~5 |
| reaction | addReaction | 1 |
| sticker | getStickers, searchSticker, getStickerCategoryDetail | ~3 |
| profile | updateProfile, updateProfileBio, changeAccountAvatar, ... | ~8 |
| alias | changeFriendAlias, removeFriendAlias, getAliasList | 3 |
| label | getLabels, updateLabels | 2 |
| conversation | deleteChat, setPinnedConversations, getArchivedChatList, ... | ~10 |
| boards | getListBoard | 1 |
| group_poll | createPoll, addPollOptions, votePoll, lockPoll, sharePoll, getPollDetail | 6 |
| group_board | createNote, editNote | 2 |
| catalog | getCatalogList, createCatalog, updateCatalog, deleteCatalog | 4 |
| auto_reply | getAutoReplyList, createAutoReply, updateAutoReply, deleteAutoReply | 4 |
| quick_message | getQuickMessageList, addQuickMessage, updateQuickMessage, removeQuickMessage | 4 |
| todoUrl | createReminder, editReminder, removeReminder, getListReminder, ... | 6 |

---

## 7. Encryption & Crypto

### 7.1 Summary of All Crypto Operations

| Operation | Algorithm | Key Format | IV | Use |
|-----------|-----------|-----------|-----|-----|
| Login param encrypt | AES-CBC | UTF-8 (hardcoded 32 chars) | zero 16 bytes | ParamsEncryptor.encodeAES |
| Login zcid creation | AES-CBC | UTF-8 "3FC4F0D2AB50057BCE0D90D9187A22B1" | zero 16 bytes | createZcid |
| Login response decrypt | AES-CBC | UTF-8 (derived encryptKey) | zero 16 bytes | decryptResp |
| API param encrypt | AES-CBC | Base64.parse(secretKey) | zero 16 bytes | encodeAES |
| API response decrypt | AES-CBC | Base64.parse(secretKey) | zero 16 bytes | decodeAES |
| WebSocket event decrypt | AES-GCM | Raw bytes (Base64 decoded cipherKey) | first 16 bytes of payload | decodeEventData |
| Sign key | MD5 | - | - | getSignKey |
| File checksum | MD5 | - | - | SparkMD5 chunked |
| PIN encrypt | MD5 | - | - | crypto.createHash |
| UUID generation | UUID + MD5 | - | - | generateZaloUUID |

### 7.2 Key Differences Between Encrypt Functions

**CRITICAL cho Java port** (key format khác nhau giữa login và session):

1. **ParamsEncryptor.encodeAES** (login only):
   - Key: `CryptoJS.enc.Utf8.parse(key)` -- key là UTF-8 string
   - IV: `{ words: [0,0,0,0], sigBytes: 16 }` -- zero IV
   - Output: hex hoac base64

2. **encodeAES** (session, mỗi API call):
   - Key: `CryptoJS.enc.Base64.parse(secretKey)` -- key là Base64 encoded
   - IV: `CryptoJS.enc.Hex.parse("0000...0000")` -- zero IV
   - Output: Base64

3. **decryptResp** (login response):
   - Key: `CryptoJS.enc.Utf8.parse(key)` -- UTF-8
   - Input: Base64 ciphertext (URL decoded)

4. **decodeAES** (session response):
   - Key: `CryptoJS.enc.Base64.parse(secretKey)` -- Base64
   - Input: Base64 ciphertext (URL decoded)

5. **WebSocket AES-GCM**:
   - Key: raw bytes from Base64 decode of cipherKey
   - IV: bytes [0..15] of payload
   - AAD: bytes [16..31] of payload
   - Ciphertext: bytes [32..] of payload
   - tagLength: 128 bits

---

## 8. WebSocket Binary Protocol

### 8.1 Frame Format

```
[version: 1 byte][cmd: 2 bytes LE][subCmd: 1 byte][payload: UTF-8 JSON]
```

- version: UInt8 (always 1)
- cmd: UInt16 Little-Endian
- subCmd: Int8
- payload: JSON encoded as UTF-8

### 8.2 Sending

```java
// Pseudo-code
DataView dv = new DataView(4 + payloadLength);
dv.setUint8(0, version);                  // byte 0
dv.setInt32(1, cmd, littleEndian=true);    // bytes 1-4 (nhưng chỉ dùng 2 bytes LE)
dv.setInt8(3, subCmd);                     // byte 3
// Copy encoded payload at offset 4
```

**Note**: Code TS dùng `setInt32(1, cmd, true)` nhưng chỉ đọc lại bằng `readUInt16LE(1)`.
Byte 3 sẽ bị ghi đè bởi `setInt8(3, subCmd)`. Nên thực tế:
- byte 0: version
- bytes 1-2: cmd as UInt16 LE
- byte 3: subCmd as Int8

### 8.3 Receiving (getHeader)

```typescript
buffer[0]              // version
buffer.readUInt16LE(1) // cmd
buffer[3]              // subCmd
```

### 8.4 Command Table

| cmd | subCmd | Direction | Description |
|-----|--------|-----------|-------------|
| 1 | 1 | Receive | Cipher key exchange |
| 2 | 1 | Send | Ping/keepalive |
| 501 | 0 | Receive | User messages |
| 510 | 1 | Both | Old user messages (request & response) |
| 511 | 1 | Both | Old group messages |
| 521 | 0 | Receive | Group messages |
| 502 | 0 | Receive | User seen/delivered |
| 522 | 0 | Receive | Group seen/delivered |
| 601 | 0 | Receive | Control events (file_done, group, friend) |
| 602 | 0 | Receive | Typing events |
| 610 | 0/1 | Both | Old user reactions |
| 611 | 0/1 | Both | Old group reactions |
| 612 | 0 | Receive | Reactions |
| 3000 | 0 | Receive | Duplicate connection |

### 8.5 Event Payload Decryption

Mỗi event có field `encrypt`:
```
0: Plain JSON
1: Base64 -> zlib inflate -> JSON
2: Base64 -> URL decode -> AES-GCM decrypt -> zlib inflate -> JSON (via JSONBig)
3: Base64 -> URL decode -> AES-GCM decrypt -> JSON (via JSONBig, no zlib)
```

---

## 9. Dependencies & Java Equivalents

### 9.1 JS -> Java Mapping

| JS Package | Purpose | Java Equivalent |
|------------|---------|-----------------|
| `crypto-js` | AES-CBC encrypt/decrypt, MD5 | `javax.crypto.Cipher`, `java.security.MessageDigest` |
| `node:crypto` | AES-GCM, MD5, UUID | `javax.crypto.Cipher` (GCM), `java.util.UUID` |
| `ws` | WebSocket client | OkHttp WebSocket |
| `tough-cookie` | Cookie jar management | OkHttp `CookieJar` |
| `form-data` | Multipart form data | OkHttp `MultipartBody` |
| `json-bigint` | Safe BigInt JSON parsing | Jackson (native `long`/`BigInteger`) |
| `pako` | Zlib inflate | `java.util.zip.Inflater` |
| `spark-md5` | Chunked MD5 hashing | `java.security.MessageDigest` (update per chunk) |
| `semver` | Version comparison | Not needed (SDK does not check npm updates) |
| `node:fs` | File I/O | `java.nio.file.Files` |

### 9.2 Zava Dependencies (verified from Maven Central, 2025-04-18)

**Target: Java 11 (LTS, supported until 2032+)**

**Build tool: Maven 3.9.x**

**Runtime dependencies (3 total):**

| Dependency | groupId:artifactId | Version | Released | Java min | Purpose |
|---|---|---|---|---|---|
| OkHttp | `com.squareup.okhttp3:okhttp` | **4.12.0** | 2023-10-17 | Java 8 | HTTP client + WebSocket + CookieJar |
| Jackson Databind | `com.fasterxml.jackson.core:jackson-databind` | **2.19.0** | 2025-04-24 | Java 8 | JSON serialization/deserialization |
| SLF4J API | `org.slf4j:slf4j-api` | **2.0.17** | 2025-02-25 | Java 8 | Logging facade (consumer picks impl) |

**Test dependencies:**

| Dependency | groupId:artifactId | Version | Released |
|---|---|---|---|
| JUnit Jupiter | `org.junit.jupiter:junit-jupiter` | **5.12.2** | 2025-04-11 |
| SLF4J Simple | `org.slf4j:slf4j-simple` | **2.0.17** | 2025-02-25 |

**Built-in (JDK, zero deps):**
- `javax.crypto.Cipher` - AES-CBC, AES-GCM
- `java.security.MessageDigest` - MD5
- `java.util.Base64` - Base64 encode/decode
- `java.util.zip.Inflater` - zlib decompress
- `java.util.UUID` - UUID generation
- `java.nio.file.Files` - File I/O
- `java.nio.ByteBuffer` - Binary protocol parsing

### 9.3 Version Decision Notes

**Why OkHttp 4.12.0 (not 5.x)?**
- OkHttp 5.x (latest: 5.3.2) requires Java 21 (`jvmToolchain(21)` in build.gradle.kts)
- OkHttp 5.x is Kotlin multiplatform, depends on Okio - heavier footprint
- 4.12.0 is the final stable release of the 4.x line. No longer actively maintained,
  but extremely stable and sufficient for our use case (HTTP + WebSocket + cookies)
- API surface is nearly identical - migration to 5.x later only requires bumping Java version

**Why Jackson 2.19.0 (not 3.x)?**
- Jackson 3.x (latest: 3.1.2) requires Java 17 (`javac.src.version=17` in pom.xml)
- Jackson 3.x changed groupId from `com.fasterxml.jackson` to `tools.jackson` - breaking change
- Jackson 2.x is still actively maintained (2.18.4 released May 2025, 2.19.0 released April 2025)
- 2.x has identical functionality for our needs

**Why SLF4J 2.0.17 (not 1.x)?**
- SLF4J 2.0.x is the current stable line (2.1.0-alpha1 exists but is not stable)
- 2.0.x supports Java 8+, uses ServiceLoader (no more classpath scanning)
- SDK should only depend on slf4j-api. Consumer chooses their own backend (Logback, Log4j2, etc.)

**Why Java 11 (not 8, not 17)?**
- Java 8: Still widely used but increasingly dropped by libraries.
  OkHttp 4.x supports it, but future upgrades would be blocked.
- Java 11: LTS, supported until 2032+. Has `var`, `HttpClient`, `String.isBlank()`,
  `Files.readString()`. All major libs support it. This is the safest floor for a modern SDK.
- Java 17: Would allow Jackson 3.x and sealed classes, but narrows the user base unnecessarily.
- Java 21: Would allow OkHttp 5.x and virtual threads, but too new for broad SDK adoption.

---

## 10. Java Port Notes

### 10.1 Architecture Recommendations

```
com.zava/
├── Zava.java                    // Entry point (login methods)
├── ZavaApi.java                 // API facade (or split into service classes)
├── ZavaListener.java            // WebSocket listener (EventEmitter -> Observer/Listener pattern)
│
├── core/
│   ├── Context.java             // Session state
│   ├── CryptoUtils.java         // AES-CBC, AES-GCM, MD5, ParamsEncryptor
│   ├── HttpClient.java          // HTTP client wrapper (OkHttp)
│   ├── ApiFactory.java          // Base class for API methods
│   └── ZavaResponse.java        // Response wrapper
│
├── api/                         // One class per API domain
│   ├── MessageApi.java          // sendMessage, forwardMessage, deleteMessage, undo
│   ├── GroupApi.java            // createGroup, getGroupInfo, addUserToGroup, ...
│   ├── FriendApi.java           // findUser, sendFriendRequest, ...
│   ├── AttachmentApi.java       // uploadAttachment
│   ├── ReactionApi.java         // addReaction
│   ├── StickerApi.java
│   ├── PollApi.java
│   ├── ProfileApi.java
│   ├── ConversationApi.java
│   ├── BusinessApi.java
│   ├── SettingsApi.java
│   └── LoginApi.java            // cookie login, QR login
│
├── model/                       // Data models (POJOs)
│   ├── Message.java             // UserMessage, GroupMessage
│   ├── Group.java               // GroupInfo, GroupSetting
│   ├── User.java                // User, UserBasic
│   ├── Reaction.java
│   ├── GroupEvent.java
│   ├── FriendEvent.java
│   ├── ... (all other models)
│   └── enums/                   // All enums
│
├── exception/                   // Custom exceptions
│   ├── ZavaApiException.java
│   ├── ZavaLoginQRAbortedException.java
│   └── ZavaLoginQRDeclinedException.java
│
└── protocol/                    // Wire protocol
    ├── WsFrame.java             // 4-byte header + payload
    ├── WsCommandType.java       // cmd enum
    └── EventDecoder.java        // decrypt/decompress events
```

### 10.2 Key Implementation Notes

1. **AES-CBC**: Java `Cipher.getInstance("AES/CBC/PKCS5Padding")`.
   PKCS5Padding and PKCS7Padding are equivalent for block size 16 in Java.

2. **AES-GCM**: Java `Cipher.getInstance("AES/GCM/NoPadding")`.
   `GCMParameterSpec(128, iv)` + `cipher.updateAAD(aad)`.

3. **Zero IV**: `new IvParameterSpec(new byte[16])`.

4. **Key parsing** (critical - different formats for login vs session):
   - Login: `new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES")`
   - Session: `new SecretKeySpec(Base64.getDecoder().decode(secretKey), "AES")`

5. **WebSocket**: OkHttp `WebSocketListener`. Override `onMessage(WebSocket, ByteString)`.
   Parse 4-byte header with `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`.

6. **EventEmitter -> Java**: Use typed listener interface pattern:
   ```java
   public interface ZavaEventListener {
     void onMessage(Message message);
     void onReaction(Reaction reaction);
     void onGroupEvent(GroupEvent event);
     // ...
   }
   ```
   Or use builder-style for convenience:
   ```java
   client.listener()
       .onMessage(msg -> handleMessage(msg))
       .onGroupEvent(event -> handleGroup(event))
       .start();
   ```

7. **async/await -> Java**: Blocking (synchronous) API. Document that consumers
   should use virtual threads (Java 21+) or thread pools for concurrent use.

8. **Cookie**: OkHttp `CookieJar` implementation with persistence support.

9. **BigInt JSON**: Jackson handles `long` natively. Zalo IDs fit in `long` (64-bit).

10. **File upload chunks**: `FileInputStream` + `byte[]` buffer + OkHttp `MultipartBody.Part`.

### 10.3 Port Priority Order

1. `CryptoUtils` -- foundation, must be tested thoroughly with known inputs
2. `Context` + Models -- data structures
3. `LoginApi` (cookie login) -- verify crypto chain works end-to-end
4. `ZavaListener` -- WebSocket + event decode
5. `MessageApi.sendMessage` -- end-to-end verification
6. Remaining ~140 APIs -- all follow the same pattern, could be code-generated

### 10.4 Hardcoded Values

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
