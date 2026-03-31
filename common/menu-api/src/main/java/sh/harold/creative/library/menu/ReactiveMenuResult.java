package sh.harold.creative.library.menu;

import java.util.List;

public record ReactiveMenuResult<S>(S state, List<ReactiveMenuEffect> effects) {

    public ReactiveMenuResult {
        effects = List.copyOf(effects);
    }

    public static <S> ReactiveMenuResult<S> stay(S state) {
        return new ReactiveMenuResult<>(state, List.of());
    }

    public static <S> ReactiveMenuResult<S> of(S state, ReactiveMenuEffect... effects) {
        return new ReactiveMenuResult<>(state, List.of(effects));
    }
}
