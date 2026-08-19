package com.github.M2B5.dailyGP.listener;

import com.github.M2B5.dailyGP.grandprix.GrandPrixManager;
import me.makkuusen.timing.system.api.events.HeatFinishEvent;
import me.makkuusen.timing.system.api.events.driver.DriverFinishHeatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class HeatProgressListener implements Listener {

    private final GrandPrixManager manager;

    public HeatProgressListener(GrandPrixManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDriverFinishHeat(DriverFinishHeatEvent event) {
        manager.onDriverFinished(event.getDriver());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeatFinish(HeatFinishEvent event) {
        manager.onHeatFinished(event.getHeat());
    }
}
