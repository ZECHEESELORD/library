package sh.harold.library.menu;

@FunctionalInterface
public interface ReactiveMenuCustodyPolicy<S> {

    /**
     * Proposes a destination for one whole-stack gesture.
     *
     * <p>The adapter validates the proposal against current native state and reports the eventual
     * committed or rejected outcome to the reducer.</p>
     */
    MenuCustodyDecision decide(S state, MenuCustodyGesture gesture, MenuCustodySnapshot snapshot);
}
