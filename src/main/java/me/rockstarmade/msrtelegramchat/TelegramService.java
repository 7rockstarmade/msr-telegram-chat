package me.rockstarmade.msrtelegramchat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramService {

    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final MsrTelegramChatPlugin plugin;
    private final Queue<String> outboundQueue = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService senderExecutor;

    public TelegramService(MsrTelegramChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        if (!plugin.getConfig().getBoolean("telegram.enabled", true)) {
            plugin.getLogger().info("Telegram send bridge disabled");
            return;
        }

        long intervalMs = Math.max(100L, plugin.getConfig().getLong("telegram.sender-interval-ms", 250L));
        senderExecutor = Executors.newSingleThreadScheduledExecutor(new TelegramThreadFactory());
        senderExecutor.scheduleAtFixedRate(this::flushSafely, 0L, intervalMs, TimeUnit.MILLISECONDS);
        plugin.getLogger().info("Telegram send bridge enabled, sender interval: " + intervalMs + " ms");
    }

    public void stop() {
        if (senderExecutor != null) {
            senderExecutor.shutdownNow();
            senderExecutor = null;
        }
    }

    public void enqueue(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        int maxQueueSize = Math.max(10, plugin.getConfig().getInt("telegram.max-queue-size", 500));
        if (outboundQueue.size() >= maxQueueSize) {
            plugin.getLogger().warning("Telegram outbound queue is full, dropping message");
            return;
        }

        outboundQueue.offer(text);
    }

    public boolean sendNow(String text) {
        return sendInternal(text);
    }

    public static void diagnose(MsrTelegramChatPlugin plugin) {
        String token = plugin.getConfig().getString("telegram.bot-token");
        String chatId = normalize(plugin.getConfig().getString("telegram.chat-id"));

        if (!isConfigured(token)) {
            plugin.getLogger().warning("Telegram diagnostics failed: telegram.bot-token is not configured");
            return;
        }

        if (!isConfigured(chatId)) {
            plugin.getLogger().warning("Telegram diagnostics failed: telegram.chat-id is not configured");
            return;
        }

        boolean botOk = request(plugin, token, "getMe", null, "bot token");
        boolean chatOk = request(plugin, token, "getChat", "chat_id=" + encode(chatId), "chat access");

        if (botOk && chatOk) {
            plugin.getLogger().info("Telegram diagnostics OK: bot token and chat id are valid");
        }
    }

    private void flushSafely() {
        try {
            int batchSize = Math.max(1, plugin.getConfig().getInt("telegram.sender-batch-size", 3));
            for (int i = 0; i < batchSize; i++) {
                String text = outboundQueue.poll();
                if (text == null) {
                    return;
                }

                boolean sent = sendInternal(text);
                if (!sent) {
                    boolean requeueFailed = plugin.getConfig().getBoolean("telegram.requeue-failed-messages", false);
                    if (requeueFailed) {
                        outboundQueue.offer(text);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Telegram sender failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean sendInternal(String text) {
        try {
            String token = plugin.getConfig().getString("telegram.bot-token");
            String chatId = normalize(plugin.getConfig().getString("telegram.chat-id"));

            if (!isConfigured(token)) {
                plugin.getLogger().warning("Telegram message was not sent: telegram.bot-token is not configured");
                return false;
            }

            if (!isConfigured(chatId)) {
                plugin.getLogger().warning("Telegram message was not sent: telegram.chat-id is not configured");
                return false;
            }

            String url = "https://api.telegram.org/bot" + token + "/sendMessage";
            String body = "chat_id=" + encode(chatId) + "&text=" + encode(text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(3, plugin.getConfig().getInt("telegram.send-timeout-seconds", 8))))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }

            plugin.getLogger().warning("Telegram message failed, status=" + response.statusCode()
                    + ", error=" + errorDescription(response.body()));
            return false;

        } catch (Exception e) {
            plugin.getLogger().warning("Telegram error while sending message: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean request(MsrTelegramChatPlugin plugin, String token, String method, String body, String label) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                    .timeout(Duration.ofSeconds(10));

            if (body == null) {
                builder.GET();
            } else {
                builder.POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/x-www-form-urlencoded");
            }

            HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                plugin.getLogger().info("Telegram " + label + " check: OK");
                return true;
            }

            plugin.getLogger().warning("Telegram " + label + " check failed, status=" + response.statusCode()
                    + ", error=" + errorDescription(response.body()));
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Telegram " + label + " check failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim();
    }

    private static boolean isConfigured(String value) {
        return value != null
                && !value.isBlank()
                && !value.startsWith("PUT_");
    }

    private static String errorDescription(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty response";
        }

        Matcher matcher = DESCRIPTION_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "unexpected response";
    }

    private static final class TelegramThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "msr-telegram-sender");
            thread.setDaemon(true);
            return thread;
        }
    }
}
