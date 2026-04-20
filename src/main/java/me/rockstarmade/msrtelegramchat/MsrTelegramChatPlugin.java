package me.rockstarmade.msrtelegramchat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class MsrTelegramChatPlugin extends JavaPlugin {

    private TelegramPollingService pollingService;
    private TelegramService telegramService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        telegramService = new TelegramService(this);
        pollingService = new TelegramPollingService(this);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        boolean enabled = getConfig().getBoolean("telegram.enabled", true);
        String token = getConfig().getString("telegram.bot-token", "");
        String chatId = getConfig().getString("telegram.chat-id", "");

        getLogger().info("Plugin enabled");
        getLogger().info("Telegram bridge enabled: " + enabled);
        getLogger().info("Telegram bot token configured: " + isConfigured(token));
        getLogger().info("Telegram chat id configured: " + isConfigured(chatId) + " (" + describeChatId(chatId) + ")");

        telegramService.start();
        getServer().getScheduler().runTaskAsynchronously(this, () -> TelegramService.diagnose(this));
        pollingService.start();
    }

    @Override
    public void onDisable() {
        if (pollingService != null) {
            pollingService.stop();
        }
        if (telegramService != null) {
            telegramService.stop();
        }
    }

    public TelegramService getTelegramService() {
        return telegramService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("msrtg")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /msrtg reload | test | diagnose");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("MsrTelegramChat config reloaded");
            getLogger().info("Config reloaded by " + sender.getName());
            telegramService.start();
            getServer().getScheduler().runTaskAsynchronously(this, () -> TelegramService.diagnose(this));
            pollingService.start();
            return true;
        }

        if (args[0].equalsIgnoreCase("test")) {
            telegramService.enqueue("[MC] Telegram bridge test");
            sender.sendMessage("Telegram test message queued");
            return true;
        }

        if (args[0].equalsIgnoreCase("diagnose")) {
            sender.sendMessage("Running Telegram diagnostics. Check server log for the result.");
            getServer().getScheduler().runTaskAsynchronously(this, () -> TelegramService.diagnose(this));
            return true;
        }

        sender.sendMessage("Usage: /msrtg reload | test | diagnose");
        return true;
    }

    private boolean isConfigured(String value) {
        return value != null
                && !value.isBlank()
                && !value.startsWith("PUT_");
    }

    private String describeChatId(String chatId) {
        if (chatId == null || chatId.isBlank() || chatId.startsWith("PUT_")) {
            return "not configured";
        }

        String trimmed = chatId.trim();
        boolean numeric = trimmed.matches("-?\\d+");
        boolean supergroupLike = trimmed.startsWith("-100");

        if (chatId.length() <= 4) {
            return "masked=****, length=" + trimmed.length()
                    + ", numeric=" + numeric
                    + ", startsWith-100=" + supergroupLike;
        }

        return "masked=****" + trimmed.substring(trimmed.length() - 4)
                + ", length=" + trimmed.length()
                + ", numeric=" + numeric
                + ", startsWith-100=" + supergroupLike;
    }
}
