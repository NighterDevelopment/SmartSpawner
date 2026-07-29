package github.nighter.smartspawner.updates;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic, version-less YAML migrator that runs on every startup for every config / language file.
 *
 * <p>Unlike the old {@code config_version}-based updater, this keeps the user's file exactly as it is
 * (values <em>and</em> comments) and only ever <em>adds</em> what is missing. There is no version
 * number to bump, no full-file rewrite, and no backup churn – the file is only written when something
 * actually changed.</p>
 *
 * <p>What it does, in order:</p>
 * <ol>
 *   <li>Strip legacy version keys ({@code config_version} / {@code language_version} /
 *       {@code gui_layout_version}) left over from the old system.</li>
 *   <li>Apply key renames – transfer the user's old value to the new key name.</li>
 *   <li>Run an optional {@link CustomMigration} for anything renames can't express
 *       (conditional value rewrites, pattern-based key moves, ...).</li>
 *   <li>Add missing keys – fill in any key absent from the user's file with the bundled default,
 *       carrying its comments across too. Sections marked by an {@link OwnedSection} are skipped,
 *       because their contents are a list the user curates.</li>
 *   <li>Write to disk – only when at least one change was made.</li>
 * </ol>
 *
 * <p>User values for existing, unchanged keys are NEVER touched.</p>
 */
public final class YamlMigrator {

    /** Legacy keys from the old versioned updater; always removed so they don't linger as dead entries. */
    private static final List<String> LEGACY_VERSION_KEYS =
            List.of("config_version", "language_version", "gui_layout_version");

    /** A key rename: the user's value at {@code oldPath} is moved to {@code newPath}. */
    public record Rename(String oldPath, String newPath) {}

    /**
     * Hook for migrations a static rename list can't describe (conditional value rewrites, scoped
     * top-ups, pattern-based moves). Receives the loaded {@code defaults} (may be {@code null}).
     * Return {@code true} if it changed anything.
     */
    @FunctionalInterface
    public interface CustomMigration {
        boolean apply(YamlConfiguration user, YamlConfiguration defaults);
    }

    /** Picks out sections in the defaults by path, with the defaults available for shape checks. */
    @FunctionalInterface
    public interface SectionMatcher {
        /**
         * @param defaults    the bundled defaults, for inspecting a candidate section's own children
         * @param sectionPath a section path in the defaults, for example {@code BOGGED.loot}
         */
        boolean matches(YamlConfiguration defaults, String sectionPath);
    }

    /**
     * What an <em>absent</em> owned section means. A section the user still has is left alone
     * whichever value this takes; these only decide whether a missing one is written back.
     */
    public enum Ownership {
        /**
         * A list the user curates. A section they removed stays removed, as long as they still
         * have its parent. Deleting a mob's whole {@code loot} block has to stick.
         */
        CURATED,

        /**
         * A section the plugin looks up by name. A section they removed comes back in full,
         * because code asking for a message key that is not there warns in the console and shows
         * the player a missing-key notice.
         */
        RESTORED_WHEN_ABSENT,

        /**
         * A top-level section the user places themselves. It is never written back, because the
         * key encodes where the entry lives, so an absent one usually means the user moved it
         * rather than that they have never seen it.
         */
        USER_MANAGED
    }

    /**
     * Marks sections whose <em>contents</em> belong to the user, not to the defaults.
     *
     * <p>Top-up is right for settings, where a missing key means "this option is new". It is wrong
     * for a curated list such as a mob's {@code loot} section or a single message entry, where a
     * missing child usually means the server owner removed it on purpose, or renamed it. Adding the
     * bundled children back would resurrect deleted drops, leave the user holding both the old and
     * the new name of a relabelled entry so every drop happens twice, or put a chat line back under
     * a message the owner turned into an action bar.</p>
     *
     * <p>Either way, once the user has the section its contents are left alone. The factories
     * differ only in what an <em>absent</em> section means, which is not the same question for a
     * drop list, a message, and a GUI button the user can move to another slot.</p>
     */
    public record OwnedSection(SectionMatcher matcher, Ownership ownership) {

        /** @see Ownership#CURATED */
        public static OwnedSection curated(SectionMatcher matcher) {
            return new OwnedSection(matcher, Ownership.CURATED);
        }

        /** @see Ownership#RESTORED_WHEN_ABSENT */
        public static OwnedSection restoredWhenAbsent(SectionMatcher matcher) {
            return new OwnedSection(matcher, Ownership.RESTORED_WHEN_ABSENT);
        }

        /** @see Ownership#USER_MANAGED */
        public static OwnedSection userManaged(SectionMatcher matcher) {
            return new OwnedSection(matcher, Ownership.USER_MANAGED);
        }
    }

    private YamlMigrator() {}

    public static boolean migrate(File file, InputStream defaultStream, List<Rename> renames, Logger log) {
        return migrate(file, defaultStream, renames, null, true, log);
    }

    public static boolean migrate(File file, InputStream defaultStream, List<Rename> renames,
                                  CustomMigration custom, Logger log) {
        return migrate(file, defaultStream, renames, custom, true, log);
    }

    public static boolean migrate(File file, InputStream defaultStream, List<Rename> renames,
                                  CustomMigration custom, boolean addMissing, Logger log) {
        return migrate(file, defaultStream, renames, custom, addMissing, null, log);
    }

    /**
     * Migrates {@code file} against {@code defaultStream}.
     *
     * @param file          the on-disk YAML file to upgrade
     * @param defaultStream the bundled resource stream with canonical default keys/values (may be {@code null})
     * @param renames       ordered list of key renames (applied before the custom migration)
     * @param custom        optional callback for complex migrations, or {@code null}
     * @param addMissing    when {@code true}, every key present in the defaults but missing from the user's
     *                      file is added; set {@code false} for files where the custom migration decides
     *                      exactly which keys to top up (e.g. items.yml)
     * @param ownedSections optional marker for sections whose contents the user curates, or {@code null}
     * @param log           logger for summary output
     * @return {@code true} if the file was modified and saved
     */
    public static boolean migrate(File file, InputStream defaultStream, List<Rename> renames,
                                  CustomMigration custom, boolean addMissing,
                                  OwnedSection ownedSections, Logger log) {
        // First install: write the bundled resource verbatim so its formatting/comments are preserved.
        if (!file.exists()) {
            return extract(file, defaultStream, log);
        }

        YamlConfiguration user;
        try {
            user = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not read " + file.getName() + " for migration", e);
            return false;
        }

        YamlConfiguration defaults = null;
        if (defaultStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
                defaults = YamlConfiguration.loadConfiguration(reader);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Could not read default resource for " + file.getName(), e);
                return false;
            }
        }

        int stripped = stripLegacyKeys(user);
        int renamed  = applyRenames(user, renames);
        boolean customChanged = custom != null && custom.apply(user, defaults);
        int added    = (addMissing && defaults != null) ? addMissingKeys(user, defaults, ownedSections) : 0;

        if (stripped == 0 && renamed == 0 && !customChanged && added == 0) {
            return false;
        }

        try {
            user.save(file);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to save migrated " + file.getName(), e);
            return false;
        }

        StringBuilder summary = new StringBuilder("Updated ").append(file.getName()).append(':');
        if (added > 0)    summary.append(" added ").append(added).append(" key(s);");
        if (renamed > 0)  summary.append(" renamed ").append(renamed).append(" key(s);");
        if (stripped > 0) summary.append(" removed ").append(stripped).append(" legacy key(s);");
        if (customChanged) summary.append(" applied value migrations;");
        log.info(summary.substring(0, summary.length() - 1));
        return true;
    }

    // Writes the bundled resource to disk verbatim (creating parent dirs). Returns true on success.
    private static boolean extract(File file, InputStream defaultStream, Logger log) {
        if (defaultStream == null) {
            log.warning("No bundled resource to create " + file.getName());
            return false;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            byte[] bytes = defaultStream.readAllBytes();
            java.nio.file.Files.write(file.toPath(), bytes);
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to create " + file.getName(), e);
            return false;
        }
    }

    // Removes leftover version keys from the old versioned updater. Returns how many were removed.
    private static int stripLegacyKeys(YamlConfiguration user) {
        int count = 0;
        for (String key : LEGACY_VERSION_KEYS) {
            if (user.contains(key)) {
                user.set(key, null);
                count++;
            }
        }
        return count;
    }

    // Returns the number of renames applied.
    private static int applyRenames(YamlConfiguration user, List<Rename> renames) {
        int count = 0;
        for (Rename r : renames) {
            if (!user.contains(r.oldPath())) continue;
            // Copy the value to the new key only if the user hasn't already set it there.
            if (!user.contains(r.newPath())) {
                user.set(r.newPath(), user.get(r.oldPath()));
            }
            user.set(r.oldPath(), null);
            count++;
        }
        return count;
    }

    // Returns the number of keys added, carrying comments across for each new leaf.
    private static int addMissingKeys(YamlConfiguration user, YamlConfiguration defaults, OwnedSection owned) {
        Set<String> locked = owned == null ? Set.of() : lockedSections(user, defaults, owned);
        int count = 0;
        for (String path : defaults.getKeys(true)) {
            // Skip intermediate sections – setting a leaf key auto-creates its parent sections.
            if (defaults.isConfigurationSection(path)) continue;
            if (isInside(path, locked)) continue;
            if (!user.contains(path)) {
                user.set(path, defaults.get(path));
                user.setComments(path, defaults.getComments(path));
                user.setInlineComments(path, defaults.getInlineComments(path));
                count++;
            }
        }
        return count;
    }

    /**
     * The owned sections that are off limits for this run, decided <em>before</em> anything is added.
     *
     * <p>Snapshotting matters. Deciding per key while the file is being written would let the first
     * leaf of a brand new section create that section, after which every remaining leaf of it looks
     * user-owned and gets skipped, leaving the section half filled.</p>
     *
     * <p>A section the user has is always owned. A {@link Ownership#USER_MANAGED} section is owned
     * whether or not they have it. Any other absent section is owned only for
     * {@link Ownership#CURATED}, and then only when they still have its parent, which is what tells
     * a block they deleted apart from one they have never seen.</p>
     */
    private static Set<String> lockedSections(YamlConfiguration user, YamlConfiguration defaults, OwnedSection owned) {
        Set<String> locked = new HashSet<>();
        for (String path : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(path) || !owned.matcher().matches(defaults, path)) continue;
            if (owned.ownership() == Ownership.USER_MANAGED || user.isConfigurationSection(path)) {
                locked.add(path);
                continue;
            }
            if (owned.ownership() != Ownership.CURATED) continue;
            int dot = path.lastIndexOf('.');
            if (dot > 0 && user.isConfigurationSection(path.substring(0, dot))) {
                locked.add(path);
            }
        }
        return locked;
    }

    /** True when any ancestor of {@code path} is a locked section. */
    private static boolean isInside(String path, Set<String> locked) {
        if (locked.isEmpty()) return false;
        for (int dot = path.indexOf('.'); dot >= 0; dot = path.indexOf('.', dot + 1)) {
            if (locked.contains(path.substring(0, dot))) return true;
        }
        return false;
    }
}
