package com.github.M2B5.dailyGP.listener;

import com.github.M2B5.dailyGP.grandprix.GrandPrixManager;
import com.github.M2B5.dailyGP.grandprix.GrandPrixState;
import com.github.M2B5.dailyGP.message.GrandPrixCard;
import com.github.M2B5.dailyGP.util.Announcer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class PlayerJoinListener implements Listener {
    private static final long SEND_DELAY_TICKS = 40L;

    private final Plugin plugin;
    private final GrandPrixManager manager;

    public PlayerJoinListener(Plugin plugin, GrandPrixManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (manager.getState() != GrandPrixState.QUALIFYING) {
            return;
        }

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || manager.getState() != GrandPrixState.QUALIFYING) {
                return;
            }
            List<Component> card = GrandPrixCard.build(manager);
            if (!card.isEmpty()) {
                Announcer.send(player, card);
            }
        }, SEND_DELAY_TICKS);
    }
}
