package github.nighter.smartspawner.spawner.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnerConfigNameTest {
    @Test
    void convertsWhitespaceAndUnsafePathCharacters() {
        assertEquals("boss_room_zombie", SpawnerConfigName.normalize("  Boss  Room.Zombie  "));
    }

    @Test
    void keepsUnicodeNames() {
        assertEquals("phòng_boss", SpawnerConfigName.normalize("Phòng Boss"));
    }

    @Test
    void createsTypeBasedDefault() {
        assertEquals("splash_potion_spawner", SpawnerConfigName.defaultName("SPLASH_POTION"));
    }
}
