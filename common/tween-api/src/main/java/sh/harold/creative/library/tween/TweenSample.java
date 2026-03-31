package sh.harold.creative.library.tween;

import net.kyori.adventure.key.Key;

public record TweenSample<T>(Key key, T value, double progress, double strength, boolean complete) {
}
