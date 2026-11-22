package sh.harold.library.movement.fabric.client;

import sh.harold.library.movement.InputFrame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A playing simulated input script (the test hook). Closing it hands input back to the keyboard at once.
 *
 * <pre>{@code
 * try (InputScript run = ClientMovement.current().orElseThrow().simulateInput("FS*15 FSN*30")) {
 *     context.waitFor(mc -> run.done());
 * }
 * }</pre>
 */
public final class InputScript implements AutoCloseable {

    private final ClientMovement owner;

    InputScript(ClientMovement owner) {
        this.owner = owner;
    }

    /** Frames not yet played. */
    public int remaining() {
        return owner.simulatedRemaining();
    }

    public boolean done() {
        return remaining() == 0;
    }

    @Override
    public void close() {
        owner.stopSimulation();
    }

    /**
     * Parses {@code "FS*15 FSN*30 -*5"}: space-separated tokens, each {@link InputFrame#keys} letters with an optional
     * {@code *n} repeat; {@code -} alone is no keys.
     */
    public static List<InputFrame> parse(String script) {
        List<InputFrame> frames = new ArrayList<>();
        for (String token : script.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int star = token.indexOf('*');
            String letters = star < 0 ? token : token.substring(0, star);
            int count = star < 0 ? 1 : Integer.parseInt(token.substring(star + 1));
            if (count < 0) {
                throw new IllegalArgumentException("negative repeat in " + token);
            }
            frames.addAll(Collections.nCopies(count, InputFrame.keys(letters)));
        }
        return frames;
    }
}
