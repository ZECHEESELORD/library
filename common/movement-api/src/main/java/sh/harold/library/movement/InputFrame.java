package sh.harold.library.movement;

/**
 * The keys that matter to movement on one tick, as held at the start of the tick's movement. Press edges are worked
 * out by the core against the previous frame, so a frame carries only held state.
 *
 * <p>{@code sneak} is the vanilla "shift" key.
 */
public record InputFrame(boolean forward, boolean back, boolean left, boolean right,
                         boolean jump, boolean sneak, boolean sprint) {

    public static final InputFrame EMPTY = new InputFrame(false, false, false, false, false, false, false);

    /**
     * Parses held keys from letters, in any order: {@code F} forward, {@code B} back, {@code L} left, {@code R} right,
     * {@code J} jump, {@code N} sneak, {@code S} sprint; {@code -} and spaces are ignored, so {@link #compact()}
     * output parses back. {@code keys("FSN")} is sprinting forward with sneak held.
     *
     * @throws IllegalArgumentException for any other character
     */
    public static InputFrame keys(String letters) {
        InputFrame frame = EMPTY;
        for (char c : letters.toCharArray()) {
            frame = switch (c) {
                case 'F' -> frame.withForward(true);
                case 'B' -> frame.withBack(true);
                case 'L' -> frame.withLeft(true);
                case 'R' -> frame.withRight(true);
                case 'J' -> frame.withJump(true);
                case 'N' -> frame.withSneak(true);
                case 'S' -> frame.withSprint(true);
                case '-', ' ' -> frame;
                default -> throw new IllegalArgumentException("unknown key letter '" + c + "' in \"" + letters + "\"");
            };
        }
        return frame;
    }

    /** {@code +1} forward, {@code -1} back, {@code 0} neither or both. */
    public double forwardImpulse() {
        return forward == back ? 0.0 : forward ? 1.0 : -1.0;
    }

    /** {@code +1} left, {@code -1} right, {@code 0} neither or both (vanilla's strafe sign). */
    public double strafeImpulse() {
        return left == right ? 0.0 : left ? 1.0 : -1.0;
    }

    public boolean anyMovementKey() {
        return forwardImpulse() != 0.0 || strafeImpulse() != 0.0;
    }

    public InputFrame withForward(boolean value) {
        return new InputFrame(value, back, left, right, jump, sneak, sprint);
    }

    public InputFrame withBack(boolean value) {
        return new InputFrame(forward, value, left, right, jump, sneak, sprint);
    }

    public InputFrame withLeft(boolean value) {
        return new InputFrame(forward, back, value, right, jump, sneak, sprint);
    }

    public InputFrame withRight(boolean value) {
        return new InputFrame(forward, back, left, value, jump, sneak, sprint);
    }

    public InputFrame withJump(boolean value) {
        return new InputFrame(forward, back, left, right, value, sneak, sprint);
    }

    public InputFrame withSneak(boolean value) {
        return new InputFrame(forward, back, left, right, jump, value, sprint);
    }

    public InputFrame withSprint(boolean value) {
        return new InputFrame(forward, back, left, right, jump, sneak, value);
    }

    /** A compact form for logs and telemetry, for example {@code F--RJ-S}. */
    public String compact() {
        return new String(new char[]{
                forward ? 'F' : '-', back ? 'B' : '-', left ? 'L' : '-', right ? 'R' : '-',
                jump ? 'J' : '-', sneak ? 'N' : '-', sprint ? 'S' : '-'});
    }
}
