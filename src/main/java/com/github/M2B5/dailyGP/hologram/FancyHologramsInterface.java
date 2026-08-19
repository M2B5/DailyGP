package com.github.M2B5.dailyGP.hologram;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FancyHologramsInterface {

    private final Plugin plugin;
    private final Logger logger;

    public FancyHologramsInterface(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("FancyHolograms");
    }

    public boolean createHologram(String id, Location location, List<String> lines) {
        try {
            HologramManager manager = getManager();
            if (manager == null) {
                return false;
            }

            manager.getHologram(id).ifPresent(manager::removeHologram);

            TextHologramData data = new TextHologramData(id, location);
            data.setText(lines);
            data.setBillboard(Display.Billboard.FIXED);
            data.setTextAlignment(TextDisplay.TextAlignment.LEFT);

            Hologram hologram = manager.create(data);
            if (hologram == null) {
                logger.warning("FancyHolograms would not create the hologram '" + id + "'.");
                return false;
            }

            manager.addHologram(hologram);
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create the hologram '" + id + "'", e);
            return false;
        }
    }

    public boolean updateHologram(String id, List<String> lines) {
        try {
            HologramManager manager = getManager();
            if (manager == null) {
                return false;
            }

            Optional<Hologram> maybeHologram = manager.getHologram(id);
            if (maybeHologram.isEmpty()) {
                return false;
            }

            HologramData data = maybeHologram.get().getData();
            if (!(data instanceof TextHologramData textData)) {
                logger.warning("The hologram '" + id + "' is not a text hologram, so it cannot be updated.");
                return false;
            }

            if (!lines.equals(textData.getText())) {
                textData.setText(lines);
                maybeHologram.get().queueUpdate();
            }
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to update the hologram '" + id + "'", e);
            return false;
        }
    }

    public boolean deleteHologram(String id) {
        try {
            HologramManager manager = getManager();
            if (manager == null) {
                return false;
            }

            Optional<Hologram> maybeHologram = manager.getHologram(id);
            if (maybeHologram.isEmpty()) {
                return false;
            }

            manager.removeHologram(maybeHologram.get());
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to delete the hologram '" + id + "'", e);
            return false;
        }
    }

    private HologramManager getManager() {
        if (!isAvailable()) {
            return null;
        }
        FancyHologramsPlugin fancyHolograms = FancyHologramsPlugin.get();
        if (fancyHolograms == null) {
            return null;
        }
        return fancyHolograms.getHologramManager();
    }
}
