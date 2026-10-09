# weixin-ilink-sdk

本项目提供对 weixin机器人协议的 Java 封装，包含：

- 二维码登录与会话管理
- 长轮询拉取消息
- 文本消息发送（自动分片）
- 图片/文件/语音/视频发送（自动上传与加密）
- 协议编解码与常见工具能力

## 环境要求

- JDK 17+
- Maven 3.9+

## Maven 依赖

```xml
<dependency>
	<groupId>io.github.morningwn</groupId>
	<artifactId>weixin-ilink-sdk</artifactId>
	<version>1.0.0</version>
</dependency>
```

## 快速上手

```java
import io.github.morningwn.protocol.ILinkAuthSession;
import io.github.morningwn.client.ILinkBot;
import io.github.morningwn.client.ILinkClientConfig;
import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.message.MessageItem;
import io.github.morningwn.protocol.message.TextMessageItem;
import io.github.morningwn.protocol.response.QrCodeResponse;

public final class BotDemo {

	public static void main(String[] args) {
		ILinkClientConfig config = ILinkClientConfig.builder()
				.baseUrl("https://ilinkai.weixin.qq.com")
				.channelVersion("1.0.0")
				.build();

		SessionHandler sessionHandler = new SessionHandler() {
			@Override
			public ILinkAuthSession loadSession() {
				return null;
			}

			@Override
			public void persistSession(ILinkAuthSession session) {
				// 持久化 session
			}

			@Override
			public void clearSession(ILinkAuthSession expiredSession) {
				// 清理过期 session
			}

			@Override
			public void onQrcode(QrCodeResponse qrCodeResponse) {
				System.out.println("请扫码登录: " + qrCodeResponse.qrcodeImgContent());
			}
		};

		try (ILinkBot bot = new ILinkBot(config, sessionHandler)) {
			bot.startAutoPull((message, sender) -> {
				if (message.itemList() == null) {
					return;
				}
				for (MessageItem item : message.itemList()) {
					if (item instanceof TextMessageItem textItem && textItem.textItem() != null) {
						sender.sendText(
								message.fromUserId(),
								message.contextToken(),
								"收到: " + textItem.textItem().text()
						);
					}
				}
			});

			Thread.currentThread().join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
```

## 常用 API

- `startAutoPull(handler)`：启动自动拉取
- `stopAutoPull()`：停止自动拉取
- `sendText(toUserId, contextToken, text)`：发送文本（自动分片）
- `replyText(inbound, text)`：按入站消息直接回复
- `sendImage(toUserId, contextToken, Path/byte[])`：发送图片（自动上传）
- `sendFile(toUserId, contextToken, Path/byte[])`：发送文件（自动上传）
- `sendVoice(toUserId, contextToken, bytes, playtime)`：发送语音
- `sendVideo(toUserId, contextToken, bytes)`：发送视频
- `downloadAndDecryptMedia(media, imageAesKeyHex)`：下载并解密媒体，返回 `DownloadedMedia`（含 Content-Type）

## 2.0 消息模型

2.0 为破坏性升级。入站回调使用 `InboundMessage`，发送请求使用 `OutboundMessage`；`MessageItem` 是 sealed interface，需按具体类型消费，例如 `TextMessageItem`、`ImageMessageItem`。未知协议类型会映射为 `UnknownMessageItem`，并保留其原始字段。

## 可执行示例

控制台示例：

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=io.github.morningwn.example.QuickStartExampleTest -Dexec.classpathScope=test
```

Web 示例：

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=io.github.morningwn.example.WebQuickStartExampleTest -Dexec.classpathScope=test
```

默认访问地址：`http://127.0.0.1:8088`


