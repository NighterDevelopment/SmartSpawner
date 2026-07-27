package github.nighter.smartspawner.commands.config.editor;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Every form of the config editor. All Dialog API use lives in this one class, so a Paper breaking
 * change stays confined to it.
 *
 * <p>Each screen is a whole form rather than one field at a time: an entry's numbers and its head
 * texture are edited together and saved once, which is both fewer round trips and fewer chances to
 * leave an entry half-changed. Item lists stay in the inventory GUI, because a dialog cannot show
 * an item the way a slot can.</p>
 *
 * <p>Two rules this file exists to enforce:</p>
 * <ul>
 *   <li>Every action goes through {@link #click}. {@code DialogAction.customClick} dereferences its
 *       options argument, so passing null there throws inside Paper before the dialog is ever built.</li>
 *   <li>Callbacks arrive on a network thread. Nothing in them touches the server directly; they hop
 *       through {@link Scheduler#runEntityTask} first.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class ConfigEditorDialogs {

    /** One content width for the whole editor so bodies, inputs and buttons share an edge. */
    private static final int CONTENT_WIDTH = 320;
    private static final int HALF_WIDTH = CONTENT_WIDTH / 2;
    private static final int TEXT_MAX_LENGTH = 256;

    /** Appends the live value to a slider's caption, e.g. "Experience: 5". */
    private static final String SLIDER_LABEL = "%s: %s";

    /** Durability is optional, so its sliders use one step below zero to mean "not set". */
    private static final float DURABILITY_UNSET = -1f;

    private final SmartSpawner plugin;
    private final ConfigEditorService service;
    private final ConfigEditorUI ui;

    public ConfigEditorDialogs(SmartSpawner plugin, ConfigEditorService service, ConfigEditorUI ui) {
        this.plugin = plugin;
        this.service = service;
        this.ui = ui;
    }

    // ============== Entry options ==============

    /**
     * The form behind clicking a mob or item in the list: experience, drop chance and head texture in
     * one page, plus the buttons that lead to loot and deletion.
     */
    public void openEntryOptions(Player player, ConfigEditorTarget target, String entryKey, int listPage) {
        if (!service.hasEntry(target, entryKey)) {
            show(player, () -> ui.openEntryList(player, target, listPage));
            return;
        }

        int experience = service.getExperience(target, entryKey);
        Double dropChance = service.getDropChance(target, entryKey);
        String headMaterial = service.getHeadMaterial(target, entryKey);
        String headTexture = service.getHeadTexture(target, entryKey);
        int lootCount = service.listLootKeys(target, entryKey).size();

        List<DialogInput> inputs = new ArrayList<>(4);
        inputs.add(DialogInput.numberRange("experience", label("dialog_experience_label"), 0f, 1000f)
                .width(CONTENT_WIDTH).labelFormat(SLIDER_LABEL)
                .initial((float) experience).step(1f).build());

        if (target.supportsDropChance()) {
            inputs.add(DialogInput.numberRange("drop_chance", label("dialog_drop_chance_label"), 0f, 100f)
                    .width(CONTENT_WIDTH).labelFormat(SLIDER_LABEL)
                    .initial(dropChance == null ? 100f : dropChance.floatValue()).step(0.5f).build());
        }

        inputs.add(DialogInput.text("head_material", label("dialog_head_material"))
                .width(CONTENT_WIDTH).maxLength(64)
                .initial(headMaterial == null ? "" : headMaterial).build());
        inputs.add(DialogInput.text("head_texture", label("dialog_head_texture"))
                .width(CONTENT_WIDTH).maxLength(TEXT_MAX_LENGTH)
                .initial(headTexture == null ? "" : headTexture).build());

        ActionButton save = ActionButton.create(label("dialog_save"), null, HALF_WIDTH,
                click((view, audience) -> saveEntryOptions(player, target, entryKey, listPage, view)));

        ActionButton loot = ActionButton.create(
                label("dialog_loot_button", Map.of("count", String.valueOf(lootCount))), null, HALF_WIDTH,
                click((view, audience) -> show(player, () -> ui.openLootList(player, target, entryKey, listPage))));

        ActionButton delete = ActionButton.create(label("dialog_delete_entry"), null, HALF_WIDTH,
                click((view, audience) -> show(player, () -> confirmDeleteEntry(player, target, entryKey, listPage))));

        ActionButton back = ActionButton.create(label("dialog_back"), null, HALF_WIDTH,
                click((view, audience) -> show(player, () -> ui.openEntryList(player, target, listPage))));

        Component body = label("dialog_entry_body", Map.of("entry", entryKey));

        show(player, () -> player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(label("dialog_entry_title", Map.of("entry", entryKey)))
                        .body(List.of(DialogBody.plainMessage(body, CONTENT_WIDTH)))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(List.of(save, loot, delete, back), null, 2)))));
    }

    private void saveEntryOptions(Player player, ConfigEditorTarget target, String entryKey,
                                  int listPage, DialogResponseView view) {
        Float experience = view.getFloat("experience");
        Float dropChance = view.getFloat("drop_chance");
        String headMaterial = view.getText("head_material");
        String headTexture = view.getText("head_texture");

        show(player, () -> {
            if (!service.hasEntry(target, entryKey)) {
                plugin.getMessageService().sendMessage(player, "config_editor.entry_missing");
                ui.openEntryList(player, target, listPage);
                return;
            }

            if (experience != null) {
                service.setExperience(target, entryKey, Math.round(experience));
            }
            if (dropChance != null && target.supportsDropChance()) {
                service.setDropChance(target, entryKey, dropChance.doubleValue());
            }
            if (headMaterial != null && !headMaterial.isBlank()) {
                service.setHeadTexture(target, entryKey, headMaterial.trim(), headTexture);
            }

            plugin.getMessageService().sendMessage(player, "config_editor.entry_saved",
                    Map.of("entry", entryKey));
            openEntryOptions(player, target, entryKey, listPage);
        });
    }

    /** Deleting an entry throws away its whole loot table, so it gets its own yes/no page. */
    private void confirmDeleteEntry(Player player, ConfigEditorTarget target, String entryKey, int listPage) {
        ActionButton confirm = ActionButton.create(label("dialog_delete_confirm"), null, HALF_WIDTH,
                click((view, audience) -> show(player, () -> {
                    service.deleteEntry(target, entryKey);
                    plugin.getMessageService().sendMessage(player, "config_editor.entry_deleted",
                            Map.of("entry", entryKey));
                    ui.openEntryList(player, target, listPage);
                })));

        ActionButton cancel = ActionButton.create(label("dialog_cancel"), null, HALF_WIDTH,
                click((view, audience) -> openEntryOptions(player, target, entryKey, listPage)));

        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(label("dialog_delete_title", Map.of("entry", entryKey)))
                        .body(List.of(DialogBody.plainMessage(
                                label("dialog_delete_body", Map.of("entry", entryKey)), CONTENT_WIDTH)))
                        .build())
                .type(DialogType.multiAction(List.of(confirm, cancel), null, 2))));
    }

    // ============== Loot entry ==============

    /** Amount range, chance and durability range for one loot row, in a single form. */
    public void openLootEditor(Player player, ConfigEditorTarget target, String entryKey,
                               String lootKey, int listPage) {
        ConfigEditorService.LootView loot = service.readLoot(target, entryKey, lootKey);
        if (loot == null) {
            show(player, () -> ui.openLootList(player, target, entryKey, listPage));
            return;
        }

        boolean hasDurability = loot.minDurability() != null && loot.maxDurability() != null;

        List<DialogInput> inputs = List.of(
                DialogInput.numberRange("min", label("dialog_loot_min"), 0f, 64f)
                        .width(CONTENT_WIDTH).labelFormat(SLIDER_LABEL)
                        .initial((float) loot.minAmount()).step(1f).build(),
                DialogInput.numberRange("max", label("dialog_loot_max"), 0f, 64f)
                        .width(CONTENT_WIDTH).labelFormat(SLIDER_LABEL)
                        .initial((float) loot.maxAmount()).step(1f).build(),
                DialogInput.numberRange("chance", label("dialog_loot_chance"), 0f, 100f)
                        .width(CONTENT_WIDTH).labelFormat(SLIDER_LABEL)
                        .initial((float) loot.chance()).step(0.5f).build(),
                DialogInput.numberRange("durability_min", label("dialog_loot_durability_min"), DURABILITY_UNSET, 2000f)
                        .width(CONTENT_WIDTH).labelFormat(SLIDER_LABEL)
                        .initial(hasDurability ? loot.minDurability().floatValue() : DURABILITY_UNSET)
                        .step(1f).build(),
                DialogInput.numberRange("durability_max", label("dialog_loot_durability_max"), DURABILITY_UNSET, 2000f)
                        .width(CONTENT_WIDTH).labelFormat(SLIDER_LABEL)
                        .initial(hasDurability ? loot.maxDurability().floatValue() : DURABILITY_UNSET)
                        .step(1f).build());

        ActionButton save = ActionButton.create(label("dialog_save"), null, HALF_WIDTH,
                click((view, audience) -> saveLoot(player, target, entryKey, lootKey, listPage, view)));

        ActionButton changeItem = ActionButton.create(label("dialog_loot_change_item"), null, HALF_WIDTH,
                click((view, audience) -> show(player, () -> ui.openItemCapture(player,
                        new ItemCaptureHolder(target, ItemCaptureHolder.Purpose.REPLACE_LOOT,
                                entryKey, lootKey, listPage)))));

        ActionButton remove = ActionButton.create(label("dialog_loot_remove"), null, HALF_WIDTH,
                click((view, audience) -> show(player, () -> {
                    service.removeLoot(target, entryKey, lootKey);
                    plugin.getMessageService().sendMessage(player, "config_editor.loot_removed",
                            Map.of("label", lootKey));
                    ui.openLootList(player, target, entryKey, listPage);
                })));

        ActionButton back = ActionButton.create(label("dialog_back"), null, HALF_WIDTH,
                click((view, audience) -> show(player, () -> ui.openLootList(player, target, entryKey, listPage))));

        show(player, () -> player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(label("dialog_loot_title", Map.of("label", lootKey)))
                        .body(List.of(DialogBody.plainMessage(
                                label("dialog_loot_body", Map.of("item", loot.rawItem())), CONTENT_WIDTH)))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(List.of(save, changeItem, remove, back), null, 2)))));
    }

    private void saveLoot(Player player, ConfigEditorTarget target, String entryKey, String lootKey,
                          int listPage, DialogResponseView view) {
        Float min = view.getFloat("min");
        Float max = view.getFloat("max");
        Float chance = view.getFloat("chance");
        Float durabilityMin = view.getFloat("durability_min");
        Float durabilityMax = view.getFloat("durability_max");

        show(player, () -> {
            if (service.readLoot(target, entryKey, lootKey) == null) {
                plugin.getMessageService().sendMessage(player, "config_editor.entry_missing");
                ui.openLootList(player, target, entryKey, listPage);
                return;
            }

            if (min != null && max != null) {
                service.setLootAmount(target, entryKey, lootKey, Math.round(min), Math.round(max));
            }
            if (chance != null) {
                service.setLootChance(target, entryKey, lootKey, chance);
            }

            // Either slider left below zero means the entry keeps no durability range at all.
            boolean clear = durabilityMin == null || durabilityMax == null
                    || durabilityMin < 0f || durabilityMax < 0f;
            service.setLootDurability(target, entryKey, lootKey,
                    clear ? null : Math.round(durabilityMin),
                    clear ? null : Math.round(durabilityMax));

            plugin.getMessageService().sendMessage(player, "config_editor.loot_saved",
                    Map.of("label", lootKey));
            openLootEditor(player, target, entryKey, lootKey, listPage);
        });
    }

    // ============== Helpers ==============

    /**
     * Wraps a click body into a custom-click action.
     *
     * <p>{@code DialogAction.customClick} reads {@code options.lifetime()} while registering, so the
     * options argument must be a real instance. A null there fails inside Paper, not here.</p>
     */
    private static DialogAction click(BiConsumer<DialogResponseView, Audience> body) {
        return DialogAction.customClick(body::accept, ClickCallback.Options.builder().build());
    }

    /** Dialog callbacks arrive off the server thread, so anything they do is scheduled back onto it. */
    private void show(Player player, Runnable action) {
        Scheduler.runEntityTask(player, () -> {
            if (player.isOnline()) {
                action.run();
            }
        });
    }

    private Component label(String key) {
        return label(key, Map.of());
    }

    /**
     * The language files hold legacy colour codes, which a plain {@code Component.text} would show to
     * the player verbatim, so they are deserialized rather than wrapped.
     */
    private Component label(String key, Map<String, String> placeholders) {
        return LegacyComponentSerializer.legacySection().deserialize(
                plugin.getLanguageManager().commandGui().name("config_editor." + key + ".name", placeholders));
    }
}
