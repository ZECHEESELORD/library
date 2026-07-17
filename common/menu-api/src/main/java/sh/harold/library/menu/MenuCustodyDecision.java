package sh.harold.library.menu;

import java.util.Objects;

public sealed interface MenuCustodyDecision permits MenuCustodyDecision.Move, MenuCustodyDecision.Reject {

    static MenuCustodyDecision move(MenuCustodyDestination destination) {
        return new Move(destination);
    }

    static MenuCustodyDecision reject() {
        return new Reject();
    }

    record Move(MenuCustodyDestination destination) implements MenuCustodyDecision {

        public Move {
            destination = Objects.requireNonNull(destination, "destination");
        }
    }

    record Reject() implements MenuCustodyDecision {
    }
}
