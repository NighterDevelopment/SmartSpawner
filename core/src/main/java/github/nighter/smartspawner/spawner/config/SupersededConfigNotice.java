package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;

import java.io.File;

/**
 * Tells the console, once, that a settings file was replaced by a differently named one.
 *
 * <p>The spawner settings files were renamed in 1.8.0 and their contents are deliberately
 * <b>not</b> carried over: the new files ship in a format the old ones cannot express, and copying
 * an old file across would import loot entries that no longer parse. The old file is left on disk
 * untouched so a server owner can read their customisations out of it by hand.</p>
 *
 * <p>That decision is only defensible if nobody is surprised by it, which is what this notice is
 * for. It is not a migration and it changes nothing on disk.</p>
 */
final class SupersededConfigNotice {

    private SupersededConfigNotice() {
    }

    /**
     * @param currentName the file that was just created from the bundled resource
     * @param legacyName  the file it replaced, still sitting in the data folder
     */
    static void warn(SmartSpawner plugin, String currentName, String legacyName) {
        File legacy = new File(plugin.getDataFolder(), legacyName);
        if (!legacy.exists()) {
            return;
        }

        plugin.getLogger().warning(legacyName + " was replaced by " + currentName + " in this version.");
        plugin.getLogger().warning("Your old file has been left alone and is no longer read. "
                + "Any changes you made in it need copying into " + currentName + " by hand.");
    }
}
