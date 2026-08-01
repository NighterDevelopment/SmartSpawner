package github.nighter.smartspawner.spawner.data.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Words MySQL 8 will not accept as a bare identifier. The 1.8.0 renames shortened most columns to
     * a single plain word, which is exactly how a reserved word slips in; {@code spawner_range} became
     * {@code activation_range} rather than {@code range} for this reason.
     */
    private static final Set<String> MYSQL_RESERVED = Set.of(
            "range", "rank", "key", "order", "group", "index", "table", "column", "select", "from",
            "where", "int", "char", "read", "write", "lock", "usage", "system", "interval", "match",
            "leave", "condition", "cube", "function", "optimizer_costs", "resource", "rows");

    @Test
    @DisplayName("no renamed column is a MySQL reserved word")
    void renamedColumnsAreNotReservedWords() {
        for (String[] rename : DatabaseManager.COLUMN_RENAMES) {
            assertFalse(MYSQL_RESERVED.contains(rename[1].toLowerCase(Locale.ROOT)),
                    rename[1] + " is reserved in MySQL 8 and would need quoting in every statement");
        }
    }

    @Test
    @DisplayName("the rename table maps each old column once, onto a distinct new name")
    void renameTableIsWellFormed() {
        Set<String> from = new HashSet<>();
        Set<String> to = new HashSet<>();
        for (String[] rename : DatabaseManager.COLUMN_RENAMES) {
            assertTrue(from.add(rename[0]), "duplicate source column: " + rename[0]);
            assertTrue(to.add(rename[1]), "two columns renamed onto " + rename[1]);
            assertNotEquals(rename[0], rename[1], "pointless rename of " + rename[0]);
        }
        assertTrue(Collections.disjoint(from, to),
                "a new name that is also an old name would make the rename order matter");
    }
}
