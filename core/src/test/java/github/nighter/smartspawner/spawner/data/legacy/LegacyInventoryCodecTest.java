package github.nighter.smartspawner.spawner.data.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the pre-1.8 database inventory column. Every stored spawner inventory on an upgrading
 * server goes through this once, and an entry it drops is items the players lose.
 */
class LegacyInventoryCodecTest {

    @Test
    @DisplayName("a quoted array splits into its entries")
    void quotedArraySplits() {
        assertEquals(List.of("STONE:12", "BOW;3:1"),
                LegacyInventoryCodec.parseJsonArray("[\"STONE:12\",\"BOW;3:1\"]"));
    }

    @Test
    @DisplayName("a single entry parses")
    void singleEntryParses() {
        assertEquals(List.of("ROTTEN_FLESH:3456"),
                LegacyInventoryCodec.parseJsonArray("[\"ROTTEN_FLESH:3456\"]"));
    }

    @Test
    @DisplayName("a comma inside a quoted entry does not split it")
    void quotedCommasDoNotSplit() {
        assertEquals(List.of("TIPPED_ARROW;0;POISON:64,extra"),
                LegacyInventoryCodec.parseJsonArray("[\"TIPPED_ARROW;0;POISON:64,extra\"]"));
    }

    @Test
    @DisplayName("escaped characters are kept as written")
    void escapesAreUnwrapped() {
        assertEquals(List.of("NAME\"WITH:1"),
                LegacyInventoryCodec.parseJsonArray("[\"NAME\\\"WITH:1\"]"));
    }

    @Test
    @DisplayName("an empty or absent column yields no entries instead of failing")
    void emptyInputYieldsNoEntries() {
        assertTrue(LegacyInventoryCodec.parseJsonArray(null).isEmpty());
        assertTrue(LegacyInventoryCodec.parseJsonArray("").isEmpty());
        assertTrue(LegacyInventoryCodec.parseJsonArray("[]").isEmpty());
    }

    @Test
    @DisplayName("a value that is not an array yields no entries")
    void nonArrayInputYieldsNoEntries() {
        assertTrue(LegacyInventoryCodec.parseJsonArray("STONE:12").isEmpty());
        assertTrue(LegacyInventoryCodec.parseJsonArray("{\"a\":1}").isEmpty());
    }
}
