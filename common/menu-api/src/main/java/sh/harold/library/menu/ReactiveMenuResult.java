package sh.harold.library.menu;

import java.util.Objects;
import java.util.Optional;

public final class ReactiveMenuResult<S> {

    private final S state;
    private final ReactiveMenuEffect effect;
    private final boolean stateChanged;

    private ReactiveMenuResult(S state, ReactiveMenuEffect effect, boolean stateChanged) {
        this.state = state;
        this.effect = effect;
        this.stateChanged = stateChanged;
    }

    public S state() {
        return state;
    }

    public Optional<ReactiveMenuEffect> effect() {
        return Optional.ofNullable(effect);
    }

    public boolean stateChanged() {
        return stateChanged;
    }

    public static <S> ReactiveMenuResult<S> unchanged() {
        return new ReactiveMenuResult<>(null, null, false);
    }

    public static <S> ReactiveMenuResult<S> effect(ReactiveMenuEffect effect) {
        return new ReactiveMenuResult<>(null, Objects.requireNonNull(effect, "effect"), false);
    }

    public static <S> ReactiveMenuResult<S> update(S state) {
        return new ReactiveMenuResult<>(state, null, true);
    }

    public static <S> ReactiveMenuResult<S> update(S state, ReactiveMenuEffect effect) {
        return new ReactiveMenuResult<>(state, Objects.requireNonNull(effect, "effect"), true);
    }
}
