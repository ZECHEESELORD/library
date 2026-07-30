package sh.harold.library.menu;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Defines one menu item once, then applies exactly one state variant when rendered.
 */
public final class MenuItemTemplate<S> {

    private final MenuIcon icon;
    private final Function<? super S, ?> classifier;
    private final BiConsumer<? super S, MenuItemDraft> base;
    private final Map<Object, BiConsumer<? super S, MenuItemDraft>> variants;
    private final BiConsumer<? super S, MenuItemDraft> fallback;

    private MenuItemTemplate(
            MenuIcon icon,
            Function<? super S, ?> classifier,
            BiConsumer<? super S, MenuItemDraft> base,
            Map<Object, BiConsumer<? super S, MenuItemDraft>> variants,
            BiConsumer<? super S, MenuItemDraft> fallback
    ) {
        this.icon = icon;
        this.classifier = classifier;
        this.base = base;
        this.variants = variants;
        this.fallback = fallback;
    }

    public static <S, K> Builder<S, K> builder(
            MenuIcon icon,
            Function<? super S, ? extends K> classifier
    ) {
        return new Builder<>(icon, classifier);
    }

    public MenuItem render(S state) {
        Objects.requireNonNull(state, "state");
        Object key = Objects.requireNonNull(classifier.apply(state), "classifier result");
        BiConsumer<? super S, MenuItemDraft> variant = variants.get(key);
        if (variant == null) {
            variant = fallback;
        }
        if (variant == null) {
            throw new IllegalStateException("No menu item variant registered for state key: " + key);
        }

        MenuItemDraft draft = new MenuItemDraft(icon);
        base.accept(state, draft);
        variant.accept(state, draft);
        return draft.freeze();
    }

    public static final class Builder<S, K> {

        private final MenuIcon icon;
        private final Function<? super S, ? extends K> classifier;
        private final Map<K, BiConsumer<? super S, MenuItemDraft>> variants = new LinkedHashMap<>();
        private final Set<K> duplicateKeys = new LinkedHashSet<>();
        private BiConsumer<? super S, MenuItemDraft> base;
        private BiConsumer<? super S, MenuItemDraft> fallback;

        private Builder(MenuIcon icon, Function<? super S, ? extends K> classifier) {
            this.icon = Objects.requireNonNull(icon, "icon");
            this.classifier = Objects.requireNonNull(classifier, "classifier");
        }

        public Builder<S, K> base(BiConsumer<? super S, MenuItemDraft> base) {
            this.base = Objects.requireNonNull(base, "base");
            return this;
        }

        public Builder<S, K> variant(K key, BiConsumer<? super S, MenuItemDraft> author) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(author, "author");
            if (variants.putIfAbsent(key, author) != null) {
                duplicateKeys.add(key);
            }
            return this;
        }

        public Builder<S, K> otherwise(BiConsumer<? super S, MenuItemDraft> fallback) {
            this.fallback = Objects.requireNonNull(fallback, "fallback");
            return this;
        }

        public MenuItemTemplate<S> build() {
            if (!duplicateKeys.isEmpty()) {
                throw new IllegalStateException("Duplicate menu item variant keys: " + duplicateKeys);
            }
            if (base == null) {
                throw new IllegalStateException("template base is required");
            }
            if (variants.isEmpty() && fallback == null) {
                throw new IllegalStateException("template requires a variant or fallback");
            }

            Map<Object, BiConsumer<? super S, MenuItemDraft>> copied = new LinkedHashMap<>();
            variants.forEach(copied::put);
            return new MenuItemTemplate<>(icon, classifier, base, Map.copyOf(copied), fallback);
        }
    }
}
