package sh.harold.creative.library.menu;

public enum UtilitySlot {
    /**
     * Footer utility slots for the stable v1 house grammar.
     * On 6-row list/tab menus these map to:
     * LEFT_1 = 46, LEFT_2 = 47,
     * RIGHT_1 = 50, RIGHT_2 = 51, RIGHT_3 = 52.
     */
    LEFT_1(1),
    LEFT_2(2),
    RIGHT_1(5),
    RIGHT_2(6),
    RIGHT_3(7);

    private final int footerColumn;

    UtilitySlot(int footerColumn) {
        this.footerColumn = footerColumn;
    }

    public int resolveSlot(int footerRowStart) {
        return footerRowStart + footerColumn;
    }
}
