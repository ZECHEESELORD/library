package sh.harold.library.entity.capability;

import net.kyori.adventure.text.Component;

public interface TextDisplayCapable extends DisplayCapable {

    Component text();

    void text(Component text);
}
