package me.rockstarmade.msrtelegramchat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final MsrTelegramChatPlugin plugin;

    public ChatListener(MsrTelegramChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("telegram.enabled", true)) {
            plugin.getLogger().fine("Telegram bridge is disabled, chat message skipped");
            return;
        }

        String msg = PlainTextComponentSerializer.plainText().serialize(event.message());
        String player = event.getPlayer().getName();
        String format = plugin.getConfig().getString("format.message", "[MC] {player}: {message}");
        String text = format
                .replace("{player}", player)
                .replace("{message}", msg);
        long delayTicks = plugin.getConfig().getLong("telegram.send-delay-ticks", 20L);

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
                () -> TelegramService.send(plugin, text),
                Math.max(0L, delayTicks));
    }
}
