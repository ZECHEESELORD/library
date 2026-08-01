package sh.harold.library.message.core;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.title.Title;
import sh.harold.library.message.TitleMessage;

import java.time.Duration;
import java.util.Objects;

public record DefaultTitleMessage(
        Component title,
        Component subtitle,
        Duration fadeIn,
        Duration stay,
        Duration fadeOut
) implements TitleMessage {

    private static final Duration DEFAULT_FADE_IN = Duration.ofMillis(500);
    private static final Duration DEFAULT_STAY = Duration.ofMillis(2_500);
    private static final Duration DEFAULT_FADE_OUT = Duration.ofMillis(500);

    public DefaultTitleMessage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
        requireDuration(fadeIn, "fadeIn");
        requireDuration(stay, "stay");
        requireDuration(fadeOut, "fadeOut");
    }

    public static TitleMessage create(ComponentLike title, ComponentLike subtitle) {
        return new DefaultTitleMessage(
                Objects.requireNonNull(title, "title").asComponent(),
                Objects.requireNonNull(subtitle, "subtitle").asComponent(),
                DEFAULT_FADE_IN,
                DEFAULT_STAY,
                DEFAULT_FADE_OUT
        );
    }

    @Override
    public TitleMessage times(Duration fadeIn, Duration stay, Duration fadeOut) {
        return new DefaultTitleMessage(title, subtitle, fadeIn, stay, fadeOut);
    }

    @Override
    public void show(Audience audience) {
        Objects.requireNonNull(audience, "audience").showTitle(Title.title(
                title,
                subtitle,
                Title.Times.times(fadeIn, stay, fadeOut)
        ));
    }

    private static void requireDuration(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }
}
