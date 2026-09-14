package sh.harold.library.menu;

public enum MenuClick {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT;

    public MenuClick withShift(boolean shift) {
        return switch (this) {
            case LEFT, SHIFT_LEFT -> shift ? SHIFT_LEFT : LEFT;
            case RIGHT, SHIFT_RIGHT -> shift ? SHIFT_RIGHT : RIGHT;
        };
    }
}
