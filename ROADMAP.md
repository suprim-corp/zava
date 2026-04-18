# Roadmap

> Thứ tự triển khai Zava - Java SDK cho Zalo Web API
> Dựa trên phân tích kiến trúc [zca-js](https://github.com/RFS-ADRENO/zca-js)

---

## Phase 0 - Project Setup

- [ ] Maven project structure (`pom.xml`, source dirs, `.gitignore`)
- [ ] Dependencies: OkHttp 4.12.0, Jackson 2.19.0, SLF4J 2.0.17
- [ ] CI: GitHub Actions (build + test on Java 11, 17, 21)
- [ ] Code style: Google Java Format hoặc Checkstyle

---

## Phase 1 - Core / Crypto

Đây là nền tảng. Mọi thứ phía sau đều phụ thuộc vào phase này.

- [ ] `CryptoUtils` - AES-CBC encrypt/decrypt (2 variant: UTF-8 key vs Base64 key)
- [ ] `CryptoUtils` - AES-GCM decrypt (cho WebSocket events)
- [ ] `CryptoUtils` - MD5 (sign key, file checksum, PIN encrypt)
- [ ] `ParamsEncryptor` - zcid creation, encrypt key derivation, param encryption
- [ ] `SignKeyUtils` - `getSignKey(type, params)` (sorted params + MD5)
- [ ] **Unit tests** với known inputs từ zca-js (critical - phải pass trước khi tiếp tục)

---

## Phase 2 - Context & Models

- [ ] `ZaloContext` - session state (uid, imei, cookie, secretKey, serviceMap, settings)
- [ ] `ZaloOptions` - configuration (selfListen, logging, proxy, apiType, apiVersion)
- [ ] `CallbacksMap` - TTL-enabled map cho upload callbacks
- [ ] Core enums: `ThreadType`, `Gender`, `AvatarSize`, `GroupType`, ...
- [ ] Message models: `UserMessage`, `GroupMessage`, `TMessage`, `TAttachmentContent`
- [ ] User models: `User`, `UserBasic`, `UserSetting`
- [ ] Group models: `GroupInfo`, `GroupSetting`, `GroupTopic`
- [ ] Event models: `GroupEvent` (22 types), `FriendEvent` (13 types)
- [ ] Other models: `Reaction`, `Undo`, `Typing`, `SeenMessage`, `DeliveredMessage`

---

## Phase 3 - HTTP Client & Response Handling

- [ ] `ZaloHttpClient` - wrapper trên OkHttp (default headers, cookie management, redirect handling)
- [ ] `ZaloCookieJar` - OkHttp CookieJar implementation với persistence support
- [ ] `ZaloResponse<T>` - response wrapper (data + error)
- [ ] `ResponseHandler` - decrypt response (AES-CBC), parse JSON, error checking
- [ ] URL builder utility (`makeURL` equivalent)

---

## Phase 4 - Login (Cookie)

Đây là milestone đầu tiên để verify end-to-end: crypto -> HTTP -> parse response.

- [ ] `LoginApi.login()` - encrypt params, call getLoginInfo, decrypt response
- [ ] `LoginApi.getServerInfo()` - fetch settings, service map, WebSocket URLs
- [ ] `Zalo.login(credentials)` - orchestrator (parse cookies -> login -> getServerInfo -> return client)
- [ ] `Credentials` class - imei, cookies, userAgent
- [ ] Credential persistence: `saveTo(Path)` / `loadFrom(Path)`
- [ ] **Integration test** với real credentials (manual, không chạy trong CI)

---

## Phase 5 - Login (QR Code)

- [ ] `LoginApi.loginQR()` - multi-step flow:
  - Load login page, extract JS version
  - getLoginInfo, verifyClient
  - Generate QR code
  - waitingScan (long-poll)
  - waitingConfirm (long-poll)
  - checkSession, getUserInfo
- [ ] `QRLoginCallback` interface - typed events (Generated, Expired, Scanned, Declined, GotLoginInfo)
- [ ] QR image export (save base64 PNG to file)

---

## Phase 6 - WebSocket Listener

- [ ] `ZaloListener` - OkHttp WebSocketListener implementation
- [ ] Binary frame codec: 4-byte header (version + cmd LE + subCmd) + JSON payload
- [ ] Event decoder: 4 decrypt modes (plain, zlib, AES-GCM+zlib, AES-GCM)
- [ ] Cipher key exchange (cmd=1, subCmd=1)
- [ ] Ping/keepalive (cmd=2, subCmd=1, configurable interval)
- [ ] Message dispatch: cmd -> event type -> listener callback
- [ ] `ZaloEventHandler` interface:
  - `onMessage(Message)`
  - `onReaction(Reaction)`
  - `onUndo(Undo)`
  - `onTyping(Typing)`
  - `onGroupEvent(GroupEvent)`
  - `onFriendEvent(FriendEvent)`
  - `onSeenMessages(List<SeenMessage>)`
  - `onDeliveredMessages(List<DeliveredMessage>)`
  - `onUploadComplete(UploadEventData)`
- [ ] Auto-reconnect với retry/rotate logic (configurable)
- [ ] Duplicate connection detection (cmd=3000)

---

## Phase 7 - Messaging

- [ ] `MessageApi.sendMessage()` - text, mentions, styles, urgency, quote, TTL
- [ ] `AttachmentApi.uploadAttachment()` - chunked upload (image, video, file)
- [ ] `MessageApi.sendMessage()` with attachments - upload + send flow
- [ ] GIF handling: upthumb + chunked upload + MD5 checksum
- [ ] `MessageApi.forwardMessage()`
- [ ] `MessageApi.deleteMessage()`
- [ ] `MessageApi.undo()`
- [ ] `EventApi.sendTypingEvent()`
- [ ] `EventApi.sendSeenEvent()`
- [ ] `EventApi.sendDeliveredEvent()`

---

## Phase 8 - Friends & Users

- [ ] `FriendApi.findUser()` (by phone number)
- [ ] `FriendApi.findUserByUsername()`
- [ ] `FriendApi.getUserInfo()`
- [ ] `FriendApi.getAllFriends()`
- [ ] `FriendApi.sendFriendRequest()`
- [ ] `FriendApi.acceptFriendRequest()` / `rejectFriendRequest()`
- [ ] `FriendApi.removeFriend()`
- [ ] `FriendApi.blockUser()` / `unblockUser()`
- [ ] `FriendApi.changeFriendAlias()` / `removeFriendAlias()`

---

## Phase 9 - Groups

- [ ] `GroupApi.createGroup()`
- [ ] `GroupApi.getGroupInfo()`
- [ ] `GroupApi.getGroupMembersInfo()`
- [ ] `GroupApi.addUserToGroup()` / `removeUserFromGroup()`
- [ ] `GroupApi.changeGroupName()` / `changeGroupAvatar()`
- [ ] `GroupApi.changeGroupOwner()`
- [ ] `GroupApi.addGroupDeputy()` / `removeGroupDeputy()`
- [ ] `GroupApi.updateGroupSettings()`
- [ ] `GroupApi.leaveGroup()` / `disperseGroup()`
- [ ] `GroupApi.getGroupChatHistory()`

---

## Phase 10 - Reactions, Stickers, Links, Cards

- [ ] `ReactionApi.addReaction()`
- [ ] `StickerApi.sendSticker()`
- [ ] `StickerApi.searchSticker()`
- [ ] `StickerApi.getStickersDetail()`
- [ ] `MessageApi.sendLink()`
- [ ] `MessageApi.sendCard()`
- [ ] `MessageApi.sendBankCard()`
- [ ] `MessageApi.sendVideo()` / `sendVoice()`

---

## Phase 11 - Polls, Notes, Reminders

- [ ] `PollApi.createPoll()` / `addPollOptions()` / `votePoll()` / `lockPoll()`
- [ ] `PollApi.getPollDetail()` / `sharePoll()`
- [ ] `BoardApi.createNote()` / `editNote()`
- [ ] `BoardApi.getListBoard()`
- [ ] `ReminderApi.createReminder()` / `editReminder()` / `removeReminder()`
- [ ] `ReminderApi.getListReminder()` / `getReminder()`

---

## Phase 12 - Conversation & Settings

- [ ] `ConversationApi.deleteChat()`
- [ ] `ConversationApi.setPinnedConversations()` / `getPinConversations()`
- [ ] `ConversationApi.setMute()` / `getMute()`
- [ ] `ConversationApi.setHiddenConversations()`
- [ ] `ConversationApi.updateArchivedChatList()`
- [ ] `SettingsApi.getSettings()` / `updateSettings()`
- [ ] `SettingsApi.getLabels()` / `updateLabels()`
- [ ] `ProfileApi.updateProfile()` / `updateProfileBio()`
- [ ] `ProfileApi.changeAccountAvatar()`

---

## Phase 13 - Business / Catalog / Auto-reply / Quick Messages

- [ ] `BusinessApi.getBizAccount()`
- [ ] `CatalogApi` - CRUD operations
- [ ] `ProductCatalogApi` - CRUD operations
- [ ] `AutoReplyApi` - CRUD operations
- [ ] `QuickMessageApi` - CRUD operations

---

## Phase 14 - Polish & Release

- [ ] Javadoc cho tất cả public APIs
- [ ] Example project (echo bot)
- [ ] Publish lên Maven Central
- [ ] GitHub releases + changelog

---

## Ngoài scope (không làm)

- E2EE (end-to-end encryption) - zca-js cũng chưa support
- Voice/video call
- Official API integration (Zalo OA)
