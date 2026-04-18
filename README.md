# Zava

Unofficial Java SDK for Zalo Web API.

Port of [zca-js](https://github.com/RFS-ADRENO/zca-js) to Java.

> **WARNING**: Đây là SDK không chính thức. Zalo có thể thay đổi API, endpoints, hoặc
> encryption protocol bất kỳ lúc nào mà không báo trước. SDK này có thể bị break
> mà không có cách fix ngay.

> **WARNING**: Sử dụng automation trên Zalo có thể dẫn đến việc bị khóa tài khoản.
> Hãy tự chịu trách nhiệm. Rate limiting không được tích hợp sẵn trong SDK -
> consumer tự quản lý tốc độ gọi API phù hợp với use case của mình.

## Requirements

- Java 11+
- Maven 3.9+

## Installation

```xml
<dependency>
    <groupId>com.suprim</groupId>
    <artifactId>zava</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Usage (Preview)

API chưa ổn định. Đây là hướng thiết kế dự kiến:

```java
// Login bằng cookie
Zava zava = new Zava();
ZavaClient client = zava.login(Credentials.builder()
    .imei("device-id")
    .cookies(cookieList)
    .userAgent("Mozilla/5.0 ...")
    .build());

// Gửi tin nhắn
client.messages().send("Hello!", threadId, ThreadType.USER);

// Gửi tin nhắn với attachment
client.messages().send(MessageContent.builder()
    .msg("Check this out")
    .attachments(Attachment.fromFile(Path.of("photo.jpg")))
    .build(), threadId, ThreadType.GROUP);

// Lắng nghe events
client.listener()
    .onMessage(msg -> {
        System.out.println(msg.getData().getContent());
    })
    .onGroupEvent(event -> {
        System.out.println(event.getType() + ": " + event.getThreadId());
    })
    .start();

// Lưu session để dùng lại
client.credentials().saveTo(Path.of("session.json"));

// Khôi phục session
Credentials saved = Credentials.loadFrom(Path.of("session.json"));
ZavaClient client = zava.login(saved);
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| OkHttp | 4.12.0 | HTTP client, WebSocket, cookie management |
| Jackson | 2.19.0 | JSON serialization |
| SLF4J | 2.0.17 | Logging facade |

Tất cả crypto (AES-CBC, AES-GCM, MD5) sử dụng `javax.crypto` và `java.security` có sẵn trong JDK.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - Phân tích chi tiết kiến trúc zca-js và mapping sang Java
- [ROADMAP.md](ROADMAP.md) - Thứ tự triển khai (15 phases)

## Rate Limiting

SDK **không** tích hợp rate limiter. Consumer tự chịu trách nhiệm:

- Không gửi quá nhiều request trong thời gian ngắn
- Thêm delay giữa các lần gọi API nếu cần
- Implement retry logic với exponential backoff nếu bị throttle

Gợi ý: Dùng `ScheduledExecutorService`, Guava `RateLimiter`, hoặc Resilience4j tùy nhu cầu.

## License

[MIT](LICENSE)
