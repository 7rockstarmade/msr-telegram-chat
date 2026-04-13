package me.rockstarmade.msrtelegramchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelegramPollingService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final MsrTelegramChatPlugin plugin;
    private final AtomicBoolean polling = new AtomicBoolean(false);

    private BukkitTask task;
    private long nextUpdateId = -1L;
    private boolean initialized;

    public TelegramPollingService(MsrTelegramChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        if (!plugin.getConfig().getBoolean("telegram.enabled", true)
                || !plugin.getConfig().getBoolean("telegram.receive-enabled", true)) {
            plugin.getLogger().info("Telegram receive bridge disabled");
            return;
        }

        long intervalTicks = Math.max(20L, plugin.getConfig().getLong("telegram.poll-interval-ticks", 40L));
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::pollSafely, 20L, intervalTicks);
        plugin.getLogger().info("Telegram receive bridge enabled, poll interval: " + intervalTicks + " ticks");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        polling.set(false);
        initialized = false;
        nextUpdateId = -1L;
    }

    private void pollSafely() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }

        try {
            poll();
        } catch (Exception e) {
            plugin.getLogger().warning("Telegram receive polling failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            polling.set(false);
        }
    }

    private void poll() throws Exception {
        String token = plugin.getConfig().getString("telegram.bot-token");
        String chatId = normalize(plugin.getConfig().getString("telegram.chat-id"));

        if (!isConfigured(token) || !isConfigured(chatId)) {
            return;
        }

        JsonArray updates = getUpdates(token, initialized ? nextUpdateId : null);
        if (!initialized) {
            markInitialized(updates);
            return;
        }

        for (JsonElement element : updates) {
            JsonObject update = element.getAsJsonObject();
            long updateId = getLong(update, "update_id", -1L);
            if (updateId >= nextUpdateId) {
                nextUpdateId = updateId + 1L;
            }

            JsonObject message = getObject(update, "message");
            if (message == null) {
                continue;
            }

            if (!chatId.equals(getMessageChatId(message))) {
                continue;
            }

            JsonObject from = getObject(message, "from");
            if (from != null && getBoolean(from, "is_bot", false)) {
                continue;
            }

            String text = getString(message, "text");
            if (text == null || text.isBlank()) {
                continue;
            }

            String username = formatUser(from);
            String format = plugin.getConfig().getString("format.telegram-message", "[TG] {user}: {message}");
            String minecraftMessage = format
                    .replace("{user}", username)
                    .replace("{message}", text.replace('\n', ' '));

            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.getServer().broadcast(Component.text(minecraftMessage)));
        }
    }

    private JsonArray getUpdates(String token, Long offset) throws Exception {
        StringBuilder url = new StringBuilder("https://api.telegram.org/bot")
                .append(token)
                .append("/getUpdates?limit=100&timeout=0&allowed_updates=")
                .append(encode("[\"message\"]"));

        if (offset != null) {
            url.append("&offset=").append(offset);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() < 200 || response.statusCode() >= 300 || !getBoolean(root, "ok", false)) {
            plugin.getLogger().warning("Telegram receive getUpdates failed, status=" + response.statusCode()
                    + ", error=" + getString(root, "description"));
            return new JsonArray();
        }

        JsonArray result = root.getAsJsonArray("result");
        return result == null ? new JsonArray() : result;
    }

    private void markInitialized(JsonArray updates) {
        long maxUpdateId = -1L;
        for (JsonElement element : updates) {
            JsonObject update = element.getAsJsonObject();
            maxUpdateId = Math.max(maxUpdateId, getLong(update, "update_id", -1L));
        }

        nextUpdateId = maxUpdateId + 1L;
        initialized = true;
        plugin.getLogger().info("Telegram receive bridge initialized");
    }

    private String getMessageChatId(JsonObject message) {
        JsonObject chat = getObject(message, "chat");
        if (chat == null || !chat.has("id")) {
            return "";
        }

        return chat.get("id").getAsString();
    }

    private String formatUser(JsonObject from) {
        if (from == null) {
            return "unknown";
        }

        String username = getString(from, "username");
        if (username != null && !username.isBlank()) {
            return "@" + username;
        }

        String firstName = getString(from, "first_name");
        String lastName = getString(from, "last_name");
        String fullName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }

        return getString(from, "id", "unknown");
    }

    private static JsonObject getObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String getString(JsonObject object, String key, String fallback) {
        String value = getString(object, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsLong();
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
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
}
