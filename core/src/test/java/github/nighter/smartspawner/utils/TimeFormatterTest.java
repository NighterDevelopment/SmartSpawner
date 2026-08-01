package github.nighter.smartspawner.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing of the duration strings config.yml documents in its header. 1.8.0 added
 * {@code database.autosave-interval} to the keys that go through this, so a misparse there is a
 * spawner save timer running at the wrong rate.
 */
class TimeFormatterTest {

    /**
     * Only the parsing is exercised. {@code parseTimeToTicks} reaches for the plugin's logger on the
     * failure path, so a null plugin is fine for every input that actually parses, and the fallback
     * is covered by {@code SpawnerDatabaseHandler} clamping the result instead.
     */
    private final TimeFormatter formatter = new TimeFormatter(null);

    @ParameterizedTest
    @CsvSource({
            "20s,  400",
            "30s,  600",
            "5m,   6000",
            "3m,   3600",
            "1h,   72000",
            "1d,   1728000",
    })
    @DisplayName("the simple forms convert to ticks")
    void simpleFormsConvert(String input, long expectedTicks) {
        assertEquals(expectedTicks, formatter.parseTimeToTicks(input, -1L));
    }

    @Test
    @DisplayName("the default autosave interval is three minutes")
    void defaultAutosaveIntervalIsThreeMinutes() {
        assertEquals(3 * 60 * 20, formatter.parseTimeToTicks("3m", -1L));
    }

    @Test
    @DisplayName("the compound form adds its parts up")
    void compoundFormAddsUp() {
        assertEquals((1 * 86400L + 2 * 3600L + 30 * 60L + 15) * 20L,
                formatter.parseTimeToTicks("1d_2h_30m_15s", -1L));
    }

    @Test
    @DisplayName("a bare number is taken as ticks")
    void bareNumberIsTicks() {
        assertEquals(6000L, formatter.parseTimeToTicks("6000", -1L));
    }

    @Test
    @DisplayName("the minimum autosave interval is below every sensible configured value")
    void thirtySecondFloorIsBelowTheDefault() {
        assertTrue(formatter.parseTimeToTicks("30s", -1L) < formatter.parseTimeToTicks("3m", -1L),
                "the floor must not silently override the shipped default");
    }
}
