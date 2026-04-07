# Quick Start

下面示例展示如何使用高级封装 `ILinkBot`：

- 自动长轮询拉取消息并交给 `MessageHandler`
- 文本发送自动处理大消息分片
- 图片/文件发送自动处理 `getuploadurl + AES 加密 + CDN 上传 + sendmessage`

```java
package com.github.morningwn.demo;

import com.github.morningwn.client.ILinkAuthSession;
import com.github.morningwn.client.ILinkBot;
import com.github.morningwn.client.ILinkClientConfig;
import com.github.morningwn.handler.SessionHandler;
import com.github.morningwn.protocol.MessageItem;
import com.github.morningwn.protocol.ProtocolValues;
import com.github.morningwn.protocol.QrCodeResponse;

import java.nio.file.Path;

public final class BotDemo {

    public static void main(String[] args) {
        ILinkClientConfig config = ILinkClientConfig.builder()
                .baseUrl("https://ilinkai.weixin.qq.com")
                .channelVersion("1.0.0")
                .build();

        SessionHandler sessionHandler = new SessionHandler() {
            @Override
            public ILinkAuthSession loadSession() {
                // 从本地文件/数据库读取历史 session，不存在时返回 null。
                return null;
            }

            @Override
            public void persistSession(ILinkAuthSession session) {
                // 在这里持久化 session。
            }

            @Override
            public void clearSession(ILinkAuthSession expiredSession) {
                // session 过期后清理持久化记录。
            }

            @Override
            public void onQrcode(QrCodeResponse qrCodeResponse) {
                // 展示二维码 URL，提示用户扫码。
                System.out.println("请扫码登录: " + qrCodeResponse.qrcodeImgContent());
            }
        };

        // 不传 session 时，SDK 会自动尝试 loadSession；为空则自动触发二维码登录。
        try (ILinkBot bot = new ILinkBot(config, sessionHandler)) {
            bot.startAutoPull(message -> {
                for (MessageItem item : message.itemList()) {
                    if (item.type() == ProtocolValues.ITEM_TYPE_TEXT && item.textItem() != null) {
                        String text = item.textItem().text();

                        // 文本发送：自动分片，无需手动处理超长文本。
                        bot.replyText(message, "收到你的消息: " + text);

                        if (text.contains("发图片")) {
                            // 媒体发送：自动上传，无需自己调用 getuploadurl / upload。
                            bot.sendImage(message.fromUserId(), message.contextToken(), Path.of("./demo.png"));
                        }

                        if (text.contains("发文件")) {
                            bot.sendFile(message.fromUserId(), message.contextToken(), Path.of("./demo.pdf"));
                        }
                    }
                }
            });

            // 保持主线程运行（示例）。
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

## 常用 API

- `startAutoPull(handler)`：启动自动拉取并分发消息
- `stopAutoPull()`：停止自动拉取
- `sendText(toUserId, contextToken, text)`：发送文本（自动分片）
- `replyText(inbound, text)`：按入站消息自动取 `from_user_id + context_token` 回复
- `sendImage(toUserId, contextToken, Path/byte[])`：发送图片（自动上传）
- `sendFile(toUserId, contextToken, Path/byte[])`：发送文件（自动上传）
- `sendVoice(toUserId, contextToken, bytes, playtime)`：发送语音（自动上传）
- `sendVideo(toUserId, contextToken, bytes)`：发送视频（自动上传）

## Session 自动恢复

- 可以不传初始 `session`。
- SDK 在调用业务接口前会自动检查 session。
- session 不存在时自动触发二维码流程。
- session 过期（如 ret=-14）时自动清理并重新走二维码流程。
- `SessionHandler` 是可选的；不实现也能工作。

## 可执行示例（Program）

- 可直接运行的示例程序在 [src/test/java/com/github/morningwn/example/QuickStartExampleTest.java](src/test/java/com/github/morningwn/example/QuickStartExampleTest.java) 。
- 程序启动后会自动拉消息，session 缺失/过期时会触发二维码登录，并使用 ZXing 在控制台打印可扫码二维码图案。
- 程序会监听控制台输入；当收到用户消息后，输入文本并回车即可回复该用户（`/quit` 可退出）。
