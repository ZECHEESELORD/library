package sh.harold.library.movement;

import java.util.Objects;

/** The server's answer to a claim: accept it, reject it with a reason, or wait for its view of the body to catch up. */
public record ClaimCheck(Verdict verdict, String reason) {

    public static final ClaimCheck ACCEPT = new ClaimCheck(Verdict.ACCEPT, "ok");

    public ClaimCheck {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reason, "reason");
    }

    public static ClaimCheck reject(String reason) {
        return new ClaimCheck(Verdict.REJECT, reason);
    }

    public static ClaimCheck defer(String reason) {
        return new ClaimCheck(Verdict.DEFER, reason);
    }

    public enum Verdict {
        ACCEPT,
        REJECT,
        /** The server's view of the body lags the claim; ask again next tick, up to the pending timeout. */
        DEFER
    }
}
