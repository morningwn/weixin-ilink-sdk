# weixin-ilink-sdk

本项目提供对微信 iLink 机器人协议的 Java 封装，包含：

- 二维码登录与会话管理
- 长轮询拉取消息
- 文本消息发送（自动分片）
- 图片/文件/语音/视频发送（自动上传与加密）
- 协议编解码与常见工具能力

## 环境要求

- JDK 17+
- Maven 3.9+

## Maven 依赖

当前开发版本为 `2.0.0-SNAPSHOT`。在本仓库中开发时，先执行 `mvn -Dgpg.skip=true install`，再在使用方项目中引入：

```xml
<dependency>
    <groupId>io.github.morningwn</groupId>
    <artifactId>weixin-ilink-sdk</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

已发布版本请以 [Maven Central](https://central.sonatype.com/artifact/io.github.morningwn/weixin-ilink-sdk) 的版本号为准。

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
		ILinkClientConfig config = ILinkClientConfig.builder().build();

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
						sender.sendText(message.fromUserId(), message.contextToken(),
								"收到: " + textItem.textItem().text());
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

`SessionHandler` 中的会话数据可用于下次启动时恢复登录；其内容包含认证信息，应保存到受保护的存储中，且不得写入日志或提交到代码仓库。

## 常用 API

- `startAutoPull(handler)`：启动自动拉取
- `stopAutoPull()`：停止自动拉取
- `sendText(toUserId, contextToken, text)`：发送文本（自动分片）
- `replyText(inbound, text)`：按入站消息直接回复
- `sendImage(toUserId, contextToken, Path/byte[])`：发送图片（自动上传）
- `sendFile(toUserId, contextToken, Path)`：发送文件（自动上传）；也可传入文件名和字节数组
- `sendVoice(toUserId, contextToken, bytes, playtime)`：发送语音
- `sendVideo(toUserId, contextToken, bytes)`：发送视频
- `downloadAndDecryptMedia(media, imageAesKeyHex)`：下载并解密媒体，返回 `DownloadedMedia`（含 Content-Type）

## 2.0 消息模型

2.0 是破坏性升级。`startAutoPull` 回调接收 `InboundMessage`，并通过 `MessageSender` 发送消息；底层发送模型为 `OutboundMessage`。`MessageItem` 是 sealed interface，应按具体类型消费，例如 `TextMessageItem`、`ImageMessageItem`。未知协议类型会映射为 `UnknownMessageItem` 并保留原始字段。

## 可执行示例

控制台示例：

```bash
mvn -q -DskipTests compile test-compile exec:java \
  -Dexec.mainClass=io.github.morningwn.example.QuickStartExampleTest \
  -Dexec.classpathScope=test
```

Web 示例：

```bash
mvn -q -DskipTests compile test-compile exec:java \
  -Dexec.mainClass=io.github.morningwn.example.WebQuickStartExampleTest \
  -Dexec.classpathScope=test
```

默认访问地址：`http://127.0.0.1:8088`

## CI 与发布校验

- `CI` 工作流仅在推送 `v*` 标签时执行测试。
- `CodeQL` 工作流执行 Java 静态安全分析，并每周复查一次。
- 发布校验可手动触发：标签必须与 `pom.xml` 的非 SNAPSHOT 版本一致，并验证二进制、源码和 Javadoc 工件。该工作流不会发布到 Maven Central，也不会使用 GPG 私钥。


