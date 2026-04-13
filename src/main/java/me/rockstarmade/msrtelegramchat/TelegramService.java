package me.rockstarmade.msrtelegramchat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramService {

    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static boolean send(MsrTelegramChatPlugin plugin, String text) {
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
                    .timeout(Duration.ofSeconds(15))
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

    private static boolean request(MsrTelegramChatPlugin plugin, String token, String method, String body, String label) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                    .timeout(Duration.ofSeconds(15));

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

}
