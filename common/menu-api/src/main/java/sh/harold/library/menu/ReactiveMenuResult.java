package sh.harold.library.menu;

import java.util.Objects;
import java.util.List;
import java.util.Optional;

public final class ReactiveMenuResult<S> {

    private final S state;
    private final List<ReactiveMenuEffect> effects;
    private final boolean stateChanged;

    private ReactiveMenuResult(S state, Object effectOrEffects, boolean stateChanged) {
        this.state = state;
        this.effects = normalizeEffects(effectOrEffects);
        this.stateChanged = stateChanged;
    }

    private static List<ReactiveMenuEffect> normalizeEffects(Object effectOrEffects) {
        if (effectOrEffects == null) {
            return List.of();
        }
        if (effectOrEffects instanceof List<?> list) {
            return list.stream().map(ReactiveMenuEffect.class::cast).toList();
        }
        return List.of(ReactiveMenuEffect.class.cast(effectOrEffects));
    }

    public S state() {
        return state;
    }

    public Optional<ReactiveMenuEffect> effect() {
        return effects.stream().findFirst();
    }

    public List<ReactiveMenuEffect> effects() {
        return effects;
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

    public static <S> ReactiveMenuResult<S> stay(S state) {
        return new ReactiveMenuResult<>(state, List.of(), true);
    }

    public static <S> ReactiveMenuResult<S> of(S state, ReactiveMenuEffect... effects) {
        return new ReactiveMenuResult<>(state, List.of(effects), true);
    }
}
