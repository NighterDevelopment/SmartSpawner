package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import java.io.File;

public class GuiLayoutUpdater {
    private static final String VERSION_KEY = "gui_layout_version";
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String[] LAYOUT_FILES  = {"storage_gui.yml", "main_gui.yml", "sell_confirm_gui.yml"};
    private static final String[] LAYOUT_NAMES  = {"default", "DonutSMP"};

    private final SmartSpawner plugin;

    public GuiLayoutUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if GUI layouts need to be updated and update them if necessary
     */
    public void checkAndUpdateLayouts() {
        File layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        layoutsDir.mkdirs();

        for (String layoutName : LAYOUT_NAMES) {
            File layoutDir = new File(layoutsDir, layoutName);
            layoutDir.mkdirs();

            for (String fileName : LAYOUT_FILES) {
                File dataFile    = new File(layoutDir, fileName);
                String resource  = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + fileName;
                ConfigVersionService.updateFile(plugin, dataFile, resource, VERSION_KEY);
            }
        }
    }
}