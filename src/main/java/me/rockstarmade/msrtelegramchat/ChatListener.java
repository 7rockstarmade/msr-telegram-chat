package me.rockstarmade.msrtelegramchat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final MsrTelegramChatPlugin plugin;
    private final PlainTextComponentSerializer plainTextSerializer = PlainTextComponentSerializer.plainText();

    public ChatListener(MsrTelegramChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("telegram.enabled", true)) {
            return;
        }

        String msg = plainTextSerializer.serialize(event.message()).trim();
        if (msg.isBlank()) {
            return;
        }

        String player = event.getPlayer().getName();
        String format = plugin.getConfig().getString("format.message", "[MC] {player}: {message}");
        String text = format
                .replace("{player}", player)
                .replace("{message}", msg);

        plugin.getTelegramService().enqueue(text);
    }
}
