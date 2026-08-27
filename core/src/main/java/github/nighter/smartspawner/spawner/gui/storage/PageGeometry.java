package github.nighter.smartspawner.spawner.gui.storage;

/**
 * Pure page-geometry math for the storage GUI: the single source of truth that turns a spawner's
 * total usable slot count into per-page inventory sizes, item regions, filler counts and the
 * control-row position. No Bukkit dependency, no state – every method is a static function of the
 * inputs, so it is safe to call from any thread and trivial to reason about.
 *
 * <p>Layout of a page (row = 9 slots):
 * <ul>
 *   <li>Full page: size 54, first 45 slots are items, bottom row (45..53) is controls.</li>
 *   <li>Last (partial) page: {@code itemRows = max(2, ceil(remainder/9))} rows of items, then one
 *       control row. Item slots beyond the stored count are filler (red glass). The 2-row floor
 *       avoids a cramped one-row page.</li>
 * </ul>
 */
public final class PageGeometry {

    /** Item slots on a full page (5 rows of 9). */
    public static final int FULL_PAGE_ITEMS = 45;
    /** Slots in one row / the control row height. */
    public static final int ROW = 9;
    /** Minimum item rows on the partial last page, to avoid a cramped page. */
    public static final int MIN_ITEM_ROWS = 2;
    /** Maximum inventory size (6 rows). */
    public static final int MAX_SIZE = 54;

    /**
     * Geometry of a single page.
     *
     * @param page        1-indexed page number
     * @param size        inventory size (multiple of 9, 27..54)
     * @param usableStart first item slot (always 0)
     * @param usableCount item slots that actually hold stored items on this page
     * @param fillerCount item slots after the usable region to fill with filler (last page only)
     */
    public record PageSlot(int page, int size, int usableStart, int usableCount, int fillerCount) {
        /** First slot of the bottom control row. */
        public int controlRowStart() {
            return size - ROW;
        }
    }

    private PageGeometry() {
    }

    /** Number of pages needed for {@code totalSlots} usable slots; always at least 1. */
    public static int pageCount(int totalSlots) {
        if (totalSlots <= 0) {
            return 1;
        }
        int fullPages = totalSlots / FULL_PAGE_ITEMS;
        int remainder = totalSlots % FULL_PAGE_ITEMS;
        return Math.max(1, fullPages + (remainder > 0 ? 1 : 0));
    }

    /** Global usable-slot offset at which {@code page} (1-indexed) starts: {@code (page-1)*45}. */
    public static int globalSlotOffset(int page) {
        return Math.max(0, (page - 1) * FULL_PAGE_ITEMS);
    }

    /** First slot of the bottom control row for an inventory of {@code size}. */
    public static int controlRowStart(int size) {
        return size - ROW;
    }

    /**
     * Geometry for a single 1-indexed page of a store holding {@code totalSlots} usable slots.
     * Pages before the last are full (size 54, 45 items). The last page shrinks to fit the
     * remainder, floored at 2 item rows, with any leftover item slots reported as filler.
     */
    public static PageSlot page(int totalSlots, int page) {
        int total = Math.max(0, totalSlots);
        int safePage = Math.max(1, page);
        int fullPages = total / FULL_PAGE_ITEMS;
        int remainder = total % FULL_PAGE_ITEMS;
        boolean hasPartial = remainder > 0;
        int lastPage = Math.max(1, fullPages + (hasPartial ? 1 : 0));

        if (safePage < lastPage || !hasPartial) {
            // Full page (or an empty store's single page).
            return new PageSlot(safePage, MAX_SIZE, 0, FULL_PAGE_ITEMS, 0);
        }

        // Partial last page.
        int itemRows = Math.max(MIN_ITEM_ROWS, ceilDiv(remainder, ROW));
        int size = Math.min(MAX_SIZE, (itemRows + 1) * ROW);
        int itemSlots = itemRows * ROW;
        int usableCount = Math.min(remainder, itemSlots);
        int fillerCount = itemSlots - usableCount;
        return new PageSlot(safePage, size, 0, usableCount, fillerCount);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
