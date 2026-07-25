package sh.harold.library.npc.behavior;

public enum NpcTimingBand {
    QUICK(4, 7),
    SHORT(8, 14),
    MEDIUM(18, 30),
    LONG(36, 60);

    private final int minimumTicks;
    private final int maximumTicks;

    NpcTimingBand(int minimumTicks, int maximumTicks) {
        this.minimumTicks = minimumTicks;
        this.maximumTicks = maximumTicks;
    }

    public int minimumTicks() {
        return minimumTicks;
    }

    public int maximumTicks() {
        return maximumTicks;
    }
}
