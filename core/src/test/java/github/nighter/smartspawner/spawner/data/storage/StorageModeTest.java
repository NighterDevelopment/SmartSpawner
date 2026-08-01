package github.nighter.smartspawner.spawner.data.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Backend selection from {@code database.type}. 1.8.0 removed YAML storage, and this is the last
 * line of defence: an unresolved value here would disable the plugin at startup.
 */
class StorageModeTest {

    @Test
    @DisplayName("the two supported backends resolve")
    void supportedBackendsResolve() {
        assertEquals(StorageMode.SQLITE, StorageMode.fromConfig("SQLITE"));
        assertEquals(StorageMode.MYSQL, StorageMode.fromConfig("MYSQL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sqlite", "SqLiTe", "  SQLITE  "})
    @DisplayName("case and surrounding whitespace do not matter")
    void caseAndWhitespaceAreForgiven(String raw) {
        assertEquals(StorageMode.SQLITE, StorageMode.fromConfig(raw));
    }

    @Test
    @DisplayName("the removed YAML mode falls back to SQLITE")
    void removedYamlModeFallsBack() {
        assertEquals(StorageMode.SQLITE, StorageMode.fromConfig("YAML"),
                "1.7.1.2 configs still holding YAML must start, not fail");
    }

    @Test
    @DisplayName("an unknown or missing value falls back to SQLITE")
    void unknownValuesFallBack() {
        assertEquals(StorageMode.SQLITE, StorageMode.fromConfig(null));
        assertEquals(StorageMode.SQLITE, StorageMode.fromConfig(""));
        assertEquals(StorageMode.SQLITE, StorageMode.fromConfig("POSTGRES"));
        assertEquals(StorageMode.SQLITE, StorageMode.fromConfig("DATABASE"));
    }
}
