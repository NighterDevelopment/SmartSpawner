package github.nighter.smartspawner.hooks.rpg;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class AuraSkillsIntegration {
    private final SmartSpawner plugin;
    private final Map<EntityType, SkillConfig> entitySkillMap;
    private FileConfiguration config;
    private File configFile;
    private boolean enabled = false;
    private AuraSkillsApi auraSkillsApi;

    public AuraSkillsIntegration(SmartSpawner plugin) {
        this.plugin = plugin;
        this.entitySkillMap = new HashMap<>();

        if (initializeAuraSkills()) {
            loadConfig();
            loadEntityMappings();
        }
    }

    private boolean initializeAuraSkills() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("AuraSkills") == null) {
                return false;
            }

            auraSkillsApi = AuraSkillsApi.get();
            if (auraSkillsApi == null) {
                plugin.getLogger().warning("Failed to get AuraSkills API instance");
                return false;
            }

            plugin.getLogger().info("AuraSkills integration initialized successfully!");
            return true;
        } catch (NoClassDefFoundError | Exception e) {
            return false;
        }
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "auraskills.yml");

        if (!configFile.exists()) {
            plugin.saveResource("auraskills.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", true);
    }

    private void loadEntityMappings() {
        if (!enabled) return;

        entitySkillMap.clear();
        ConfigurationSection entitySection = config.getConfigurationSection("entity_skills");

        if (entitySection == null) {
            plugin.getLogger().warning("No entity_skills section found in auraskills.yml");
            return;
        }

        for (String entityName : entitySection.getKeys(false)) {
            try {
                EntityType entityType = EntityType.valueOf(entityName.toUpperCase());
                ConfigurationSection entityConfig = entitySection.getConfigurationSection(entityName);

                if (entityConfig != null) {
                    String skillName = entityConfig.getString("skill", "FIGHTING");
                    double ratio = entityConfig.getDouble("ratio", 0.5);

                    SkillConfig skillConfig = new SkillConfig(skillName, ratio);
                    entitySkillMap.put(entityType, skillConfig);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid entity type in auraskills.yml: " + entityName);
            }
        }

        plugin.getLogger().info("Loaded " + entitySkillMap.size() + " AuraSkills entity mappings");
    }

    public boolean isEnabled() {
        return enabled && auraSkillsApi != null;
    }

    public void giveSkillXp(Player player, EntityType entityType, int spawnerExp) {
        if (!isEnabled()) return;

        SkillConfig skillConfig = entitySkillMap.get(entityType);
        if (skillConfig == null) {
            return;
        }

        try {
            SkillsUser user = auraSkillsApi.getUser(player.getUniqueId());
            if (user == null) {
                return;
            }

            double skillXp = spawnerExp * skillConfig.ratio();

            switch (skillConfig.skillName().toUpperCase()) {
                case "FIGHTING":
                    user.addSkillXp(Skills.FIGHTING, skillXp);
                    break;
                case "FARMING":
                    user.addSkillXp(Skills.FARMING, skillXp);
                    break;
                case "MINING":
                    user.addSkillXp(Skills.MINING, skillXp);
                    break;
                case "FORAGING":
                    user.addSkillXp(Skills.FORAGING, skillXp);
                    break;
                case "FISHING":
                    user.addSkillXp(Skills.FISHING, skillXp);
                    break;
                case "EXCAVATION":
                    user.addSkillXp(Skills.EXCAVATION, skillXp);
                    break;
                case "ARCHERY":
                    user.addSkillXp(Skills.ARCHERY, skillXp);
                    break;
                case "DEFENSE":
                    user.addSkillXp(Skills.DEFENSE, skillXp);
                    break;
                case "ENDURANCE":
                    user.addSkillXp(Skills.ENDURANCE, skillXp);
                    break;
                case "AGILITY":
                    user.addSkillXp(Skills.AGILITY, skillXp);
                    break;
                case "ALCHEMY":
                    user.addSkillXp(Skills.ALCHEMY, skillXp);
                    break;
                case "ENCHANTING":
                    user.addSkillXp(Skills.ENCHANTING, skillXp);
                    break;
                case "SORCERY":
                    user.addSkillXp(Skills.SORCERY, skillXp);
                    break;
                case "HEALING":
                    user.addSkillXp(Skills.HEALING, skillXp);
                    break;
                case "FORGING":
                    user.addSkillXp(Skills.FORGING, skillXp);
                    break;
                default:
                    plugin.getLogger().warning("Unknown skill type: " + skillConfig.skillName());
                    return;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error giving AuraSkills XP to player " + player.getName(), e);
        }
    }

    public void reloadConfig() {
        loadConfig();
        loadEntityMappings();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save auraskills.yml", e);
        }
    }


    private record SkillConfig(String skillName, double ratio) {}
}