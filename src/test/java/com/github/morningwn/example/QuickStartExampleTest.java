package com.github.morningwn.example;

import com.github.morningwn.client.ILinkAuthSession;
import com.github.morningwn.client.ILinkBot;
import com.github.morningwn.client.ILinkClientConfig;
import com.github.morningwn.handler.SessionHandler;
import com.github.morningwn.protocol.MessageItem;
import com.github.morningwn.protocol.ProtocolValues;
import com.github.morningwn.protocol.QrCodeResponse;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable quick-start example.
 *
 * <p>Run this class directly to start the bot:
 * <pre>
 * mvn -q -DskipTests exec:java -Dexec.mainClass=com.github.morningwn.example.QuickStartExampleTest -Dexec.classpathScope=test
 * </pre>
 * </p>
 */
public final class QuickStartExampleTest {

    private QuickStartExampleTest() {
    }

    /**
     * Program entry.
     *
     * @param args ignored
     * @throws Exception when startup fails
     */
    public static void main(String[] args) throws Exception {
        ILinkClientConfig config = ILinkClientConfig.builder()
                .baseUrl("https://ilinkai.weixin.qq.com")
                .channelVersion("1.0.0")
                .build();

        Path sessionPath = Path.of(".ilink-session.txt");
        SessionHandler sessionHandler = new ConsoleSessionHandler(sessionPath);
        AtomicReference<ReplyTarget> latestReplyTarget = new AtomicReference<>();

        try (ILinkBot bot = new ILinkBot(config, sessionHandler)) {
            Runtime.getRuntime().addShutdownHook(new Thread(bot::close));

            bot.startAutoPull(message -> {
                if (message.itemList() == null) {
                    return;
                }
                for (MessageItem item : message.itemList()) {
                    if (item != null && item.type() == ProtocolValues.ITEM_TYPE_TEXT && item.textItem() != null) {
                        String text = item.textItem().text();
                        System.out.println("[inbound] from=" + message.fromUserId() + " text=" + text);
                        latestReplyTarget.set(new ReplyTarget(message.fromUserId(), message.contextToken()));
                        System.out.println("[hint] 输入内容并回车可回复该用户，输入 /quit 退出");
                    }
                }
            });

            System.out.println("Bot started. Waiting for messages...");
            runConsoleReplyLoop(bot, latestReplyTarget);
        }
    }

    private static void runConsoleReplyLoop(ILinkBot bot, AtomicReference<ReplyTarget> latestReplyTarget)
            throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return;
            }
            String text = line.trim();
            if (text.isEmpty()) {
                continue;
            }
            if ("/quit".equalsIgnoreCase(text) || "/exit".equalsIgnoreCase(text)) {
                System.out.println("Exiting...");
                return;
            }

            ReplyTarget target = latestReplyTarget.get();
            if (target == null) {
                System.out.println("[warn] 尚未收到可回复的用户消息");
                continue;
            }

            bot.sendText(target.toUserId(), target.contextToken(), text);
            System.out.println("[outbound] to=" + target.toUserId() + " text=" + text);
        }
    }

    private static final class ConsoleSessionHandler implements SessionHandler {

        private final Path sessionPath;

        private ConsoleSessionHandler(Path sessionPath) {
            this.sessionPath = sessionPath;
        }

        @Override
        public ILinkAuthSession loadSession() {
            if (!Files.exists(sessionPath)) {
                return null;
            }
            try {
                List<String> lines = Files.readAllLines(sessionPath, StandardCharsets.UTF_8);
                if (lines.size() < 4) {
                    return null;
                }
                return new ILinkAuthSession(lines.get(0), lines.get(1), lines.get(2), lines.get(3));
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public void persistSession(ILinkAuthSession session) {
            List<String> lines = List.of(
                    safe(session.token()),
                    safe(session.baseUrl()),
                    safe(session.accountId()),
                    safe(session.userId())
            );
            try {
                Files.write(
                        sessionPath,
                        lines,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
                System.out.println("[session] persisted to " + sessionPath.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("[session] persist failed: " + e.getMessage());
            }
        }

        @Override
        public void clearSession(ILinkAuthSession expiredSession) {
            try {
                Files.deleteIfExists(sessionPath);
                System.out.println("[session] expired session cleared");
            } catch (IOException e) {
                System.out.println("[session] clear failed: " + e.getMessage());
            }
        }

        @Override
        public void onQrcode(QrCodeResponse qrCodeResponse) {
            String qrUrl = Optional.ofNullable(qrCodeResponse)
                    .map(QrCodeResponse::qrcodeImgContent)
                    .orElse("");
            System.out.println("========================================");
            System.out.println("请扫码登录微信 iLink");
            System.out.println("二维码链接: " + qrUrl);
            renderQrcodeToConsole(qrUrl);
            System.out.println("========================================");
        }

        private static void renderQrcodeToConsole(String content) {
            if (content == null || content.isBlank()) {
                return;
            }
            try {
                BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 1, 1);
                int width = matrix.getWidth();
                int height = matrix.getHeight();
                int quietZone = 2;

                for (int y = -quietZone; y < height + quietZone; y++) {
                    StringBuilder row = new StringBuilder((width + quietZone * 2) * 2);
                    for (int x = -quietZone; x < width + quietZone; x++) {
                        boolean dark = x >= 0 && x < width && y >= 0 && y < height && matrix.get(x, y);
                        row.append(dark ? "██" : "  ");
                    }
                    System.out.println(row);
                }
            } catch (WriterException e) {
                System.out.println("[warn] 二维码渲染失败: " + e.getMessage());
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record ReplyTarget(String toUserId, String contextToken) {
    }
}
