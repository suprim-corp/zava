# Zava

[![CI](https://github.com/suprim-corp/zava/actions/workflows/ci.yml/badge.svg)](https://github.com/suprim-corp/zava/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/suprim-corp/zava.svg)](https://jitpack.io/#suprim-corp/zava)
[![codecov](https://codecov.io/gh/suprim-corp/zava/branch/main/graph/badge.svg)](https://codecov.io/gh/suprim-corp/zava)
![Java](https://img.shields.io/badge/Java-11+-orange?logo=openjdk)
![Maven](https://img.shields.io/badge/Maven-3.9+-blue?logo=apachemaven)
![License](https://img.shields.io/badge/License-MIT-green)

Unofficial Java SDK for Zalo Web API.

Port of [zca-js](https://github.com/RFS-ADRENO/zca-js) to Java.

> **WARNING**: Đây là SDK không chính thức. Zalo có thể thay đổi API, endpoints, hoặc
> encryption protocol bất kỳ lúc nào mà không báo trước. SDK này có thể bị break
> mà không có cách fix ngay.

> **WARNING**: Sử dụng automation trên Zalo có thể dẫn đến việc bị khóa tài khoản.
> Hãy tự chịu trách nhiệm. Rate limiting không được tích hợp sẵn trong SDK -
> consumer tự quản lý tốc độ gọi API.

## Requirements

- Java 11+
- Maven 3.9+

## Installation

Add the JitPack repository and dependency:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.suprim-corp</groupId>
    <artifactId>zava</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

```java
import dev.suprim.zava.*;
import dev.suprim.zava.auth.Credentials;
import dev.suprim.zava.conversation.ThreadType;
import dev.suprim.zava.message.Mention;
import dev.suprim.zava.message.Quote;

// Login
Zava zava = new Zava();
ZavaClient client = zava.login(Credentials.builder()
    .imei("device-id")
    .cookies(cookieList)
    .userAgent("Mozilla/5.0 ...")
    .build());

// Messages
client.messages().send("Hello!", userId, ThreadType.USER);
client.messages().send("Hi group!", groupId, ThreadType.GROUP);
client.messages().send("@all check this", groupId, ThreadType.GROUP,
    List.of(Mention.all(0, 4)));
client.messages().delete(msgId, cliMsgId, uidFrom, threadId, type, onlyMe);
client.messages().undo(msgId, cliMsgId, threadId, type);
client.messages().forward("text", targetIds, type, origMsgId, 0);

// Users
client.users().findUser("0912345678");
client.users().getAllFriends();
client.users().blockUser(userId);
client.users().unblockUser(userId);
client.users().changeFriendAlias(friendId, "Nickname");

// Groups
client.groups().getGroupInfo("group-id-1", "group-id-2");
client.groups().getAllGroups();
client.groups().createGroup("Group Name", List.of("uid1", "uid2"));
client.groups().addUser(groupId, "new-member");
client.groups().removeUser(groupId, "member-to-kick");
client.groups().changeOwner(groupId, "new-owner-uid");
client.groups().changeName(groupId, "New Group Name");

// Reactions
client.reactions().addReaction(ReactionService.Reaction.LIKE,
    msgId, cliMsgId, threadId, type);

// Stickers, Polls, Profile, Settings
client.stickers().search("hello");
client.polls().createPoll(groupId, "Favorite?", List.of("A", "B", "C"));
client.polls().getPollDetail(pollId);
client.profile().fetchAccountInfo();
client.settings().getMute();
client.settings().getLabels();

// Real-time listener
client.listener()
    .onMessage(msg -> System.out.println(msg))
    .onReaction(react -> System.out.println(react))
    .onGroupEvent(event -> System.out.println(event))
    .onTyping(typing -> System.out.println(typing))
    .start();

// Save/restore session
credentials.saveTo(Path.of("session.json"));
Credentials saved = Credentials.loadFrom(Path.of("session.json"));
```

## API Coverage

| Service | Methods |
|---------|---------|
| Messages | `send`, `delete`, `undo`, `forward` (user + group, mentions, quote, TTL) |
| Users | `findUser`, `getAllFriends`, `blockUser`, `unblockUser`, `changeFriendAlias`, `getAliasList` |
| Groups | `getGroupInfo`, `getAllGroups`, `createGroup`, `addUser`, `removeUser`, `changeOwner`, `changeName` |
| Reactions | `addReaction` (7 built-in types + custom) |
| Stickers | `search` |
| Polls | `createPoll`, `getPollDetail` |
| Profile | `fetchAccountInfo` |
| Settings | `getMute`, `getLabels` |
| Listener | `onMessage`, `onReaction`, `onUndo`, `onTyping`, `onGroupEvent`, `onFriendEvent`, `onSeen`, `onDelivered`, `onUploadComplete` |

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| OkHttp | 4.12.0 | HTTP client, WebSocket, cookie management |
| Jackson | 2.19.0 | JSON serialization |
| SLF4J | 2.0.17 | Logging facade |

Tất cả crypto (AES-CBC, AES-GCM, MD5, zlib) sử dụng JDK built-in (`javax.crypto`, `java.security`, `java.util.zip`).

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - Kiến trúc SDK
- [ROADMAP.md](ROADMAP.md) - Kế hoạch triển khai
- [ZCA-JS-REFERENCE.md](ZCA-JS-REFERENCE.md) - Phân tích codebase gốc zca-js

## License

[MIT](LICENSE)
