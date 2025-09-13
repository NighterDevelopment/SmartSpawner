package github.nighter.smartspawner.spawner.gui.layout;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.Map;

@Getter
public class GuiButton {
    private final String buttonType;
    private final int slot;
    private final Material material;
    private final boolean enabled;
    private final Sound sound;
    private final Map<String, Object> conditions;

    public GuiButton(String buttonType, int slot, Material material, boolean enabled, Sound sound, Map<String, Object> conditions) {
        this.buttonType = buttonType;
        this.slot = slot;
        this.material = material;
        this.enabled = enabled;
        this.sound = sound;
        this.conditions = conditions;
    }

    public boolean hasSound() {
        return sound != null;
    }

    public boolean hasConditions() {
        return conditions != null && !conditions.isEmpty();
    }

    @Override
    public String toString() {
        return "GuiButton{" +
                "buttonType='" + buttonType + '\'' +
                ", slot=" + slot +
                ", material=" + material +
                ", enabled=" + enabled +
                ", sound=" + sound +
                ", conditions=" + conditions +
                '}';
    }
}