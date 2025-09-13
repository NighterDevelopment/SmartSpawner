package github.nighter.smartspawner.spawner.gui.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GuiLayout {
    private final Map<String, GuiButton> buttons = new HashMap<>();
    private final Map<Integer, String> slotToButtonType = new HashMap<>();

    public void addButton(String buttonType, GuiButton button) {
        // Check if slot is already occupied by another button
        String existingButtonType = slotToButtonType.get(button.getSlot());
        if (existingButtonType != null && !existingButtonType.equals(buttonType)) {
            // Remove the existing button to replace it with the new one
            buttons.remove(existingButtonType);
        }

        // Remove old button if this buttonType already exists in a different slot
        GuiButton oldButton = buttons.get(buttonType);
        if (oldButton != null) {
            slotToButtonType.remove(oldButton.getSlot());
        }

        buttons.put(buttonType, button);
        slotToButtonType.put(button.getSlot(), buttonType);
    }

    public GuiButton getButton(String buttonType) {
        return buttons.get(buttonType);
    }

    public Optional<String> getButtonTypeAtSlot(int slot) {
        return Optional.ofNullable(slotToButtonType.get(slot));
    }

    public boolean hasButton(String buttonType) {
        return buttons.containsKey(buttonType) && buttons.get(buttonType).isEnabled();
    }

    public Set<String> getButtonTypes() {
        return buttons.keySet();
    }

    public Map<String, GuiButton> getAllButtons() {
        return new HashMap<>(buttons);
    }

    public Set<Integer> getUsedSlots() {
        return slotToButtonType.keySet();
    }

    public boolean isSlotUsed(int slot) {
        return slotToButtonType.containsKey(slot);
    }
}