package sh.harold.creative.library.statemachine;

import java.util.Objects;

public sealed interface StateChange<S extends Enum<S>> permits StateChange.Stay, StateChange.Move {

    static <S extends Enum<S>> StateChange<S> stay() {
        return new Stay<>();
    }

    static <S extends Enum<S>> StateChange<S> move(S state) {
        return new Move<>(state);
    }

    default boolean isStay() {
        return this instanceof Stay<?>;
    }

    default boolean isMove() {
        return this instanceof Move<?>;
    }

    record Stay<S extends Enum<S>>() implements StateChange<S> {
    }

    record Move<S extends Enum<S>>(S state) implements StateChange<S> {

        public Move {
            Objects.requireNonNull(state, "state");
        }
    }
}
