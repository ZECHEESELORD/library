package sh.harold.library.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import java.time.Duration;

public interface TitleMessage {

    Component title();

    Component subtitle();

    Duration fadeIn();

    Duration stay();

    Duration fadeOut();

    TitleMessage times(Duration fadeIn, Duration stay, Duration fadeOut);

    void show(Audience audience);
}
