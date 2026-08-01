package github.nighter.smartspawner.spawner.data.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code database.table-prefix} handling. The prefix is concatenated straight into SQL, because SQL
 * will not bind an identifier as a parameter, so the sanitizer is the only thing standing between a
 * config value and the statement text.
 */
class DatabaseManagerTest {

    @ParameterizedTest
    @CsvSource({
            "sspawner_,   sspawner_",
            "ss_prod_,    ss_prod_",
            "Server1_,    Server1_",
            "a1_2b,       a1_2b",
    })
    @DisplayName("a valid prefix is passed through unchanged")
    void validPrefixesArePassedThrough(String input, String expected) {
        assertEquals(expected, DatabaseManager.sanitizeTablePrefix(input));
    }

    @ParameterizedTest
    @CsvSource({
            "'ss-prod ',                    ssprod",
            "'ss.prod;',                    ssprod",
            "'a\"b',                        ab",
            "'x`y',                         xy",
    })
    @DisplayName("anything outside letters, digits and underscore is stripped")
    void unsafeCharactersAreStripped(String input, String expected) {
        assertEquals(expected, DatabaseManager.sanitizeTablePrefix(input));
    }

    @Test
    @DisplayName("a SQL fragment is reduced to harmless characters")
    void sqlFragmentsAreDefused() {
        assertEquals("dataDROPTABLEusers",
                DatabaseManager.sanitizeTablePrefix("data; DROP TABLE users;--"),
                "no quote, semicolon, space or dash may reach the statement text");
    }

    @Test
    @DisplayName("a value that sanitizes to nothing falls back to the default prefix")
    void emptyResultsFallBackToTheDefault() {
        assertEquals(DatabaseManager.DEFAULT_TABLE_PREFIX, DatabaseManager.sanitizeTablePrefix(null));
        assertEquals(DatabaseManager.DEFAULT_TABLE_PREFIX, DatabaseManager.sanitizeTablePrefix(""));
        assertEquals(DatabaseManager.DEFAULT_TABLE_PREFIX, DatabaseManager.sanitizeTablePrefix("---"));
    }
}
