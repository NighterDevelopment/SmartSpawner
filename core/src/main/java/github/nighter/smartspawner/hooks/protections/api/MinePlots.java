package github.nighter.smartspawner.hooks.protections.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import pl.minecodes.plots.api.plot.PlotApi;
import pl.minecodes.plots.api.plot.PlotServiceApi;

public class MinePlots {
    private static RegisteredServiceProvider<PlotServiceApi> serviceProvider = Bukkit.getServicesManager().getRegistration(PlotServiceApi.class);

    public static boolean canPlayerStackBlock(@NotNull Player player, @NotNull Location location) {
        return check(player, location);
    }

    public static boolean canPlayerBreakBlock(@NotNull Player player, @NotNull Location location) {
        return check(player, location);
    }

    public static boolean canPlayerOpenMenu(@NotNull Player player, @NotNull Location location) {
        return check(player, location);
    }

    public static boolean check(@NotNull Player player, @NotNull Location location) {
        if(serviceProvider == null) return true;
        PlotApi plot = serviceProvider.getProvider().getPlot(location);
        if (plot != null) {
            return plot.hasAccess(player);
        }
        // Player is not in plot
        return true;
    }

}
