package sh.harold.creative.library.scoreboard;

import net.kyori.adventure.text.Component;

import java.util.List;

@FunctionalInterface
public interface ScoreboardContent {

    List<Component> render(ScoreboardContext context);
}
