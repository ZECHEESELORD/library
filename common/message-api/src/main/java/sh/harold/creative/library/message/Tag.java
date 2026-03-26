package sh.harold.creative.library.message;

public enum Tag {
    STAFF("STAFF"),
    DAEMON("DAEMON");

    private final String label;

    Tag(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
