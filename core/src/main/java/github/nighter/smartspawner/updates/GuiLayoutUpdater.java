package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import java.io.File;

/**
 * Keeps the bundled GUI layout files in sync with the defaults. Version-less: on every startup it
 * migrates legacy click / sound paths and tops up missing keys, preserving user customisations and
 * comments. See {@link YamlMigrator} and {@link ConfigMigrations#GUI_LAYOUT}.
 */
public class GuiLayoutUpdater {
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String[] LAYOUT_FILES  = {"storage_gui.yml", "main_gui.yml", "sell_confirm_gui.yml"};
    private static final String[] LAYOUT_NAMES  = {"default", "DonutSMP", "DonutSMP_v2"};

    private final SmartSpawner plugin;

    public GuiLayoutUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    /**
     * Ensure each GUI layout file exists and is up-to-date.
     */
    public void checkAndUpdateLayouts() {
        File layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        layoutsDir.mkdirs();

        for (String layoutName : LAYOUT_NAMES) {
            File layoutDir = new File(layoutsDir, layoutName);
            layoutDir.mkdirs();

            for (String fileName : LAYOUT_FILES) {
                File dataFile   = new File(layoutDir, fileName);
                String resource = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + fileName;
                YamlMigrator.migrate(
                        dataFile,
                        plugin.getResource(resource),
                        java.util.List.of(),
                        ConfigMigrations.GUI_LAYOUT,
                        true,
                        YamlMigrator.OwnedSection.fullyUserManaged(
                                (defaults, path) -> !path.contains(".") && path.startsWith("slot_")),
                        plugin.getLogger());
            }
        }
    }
}
