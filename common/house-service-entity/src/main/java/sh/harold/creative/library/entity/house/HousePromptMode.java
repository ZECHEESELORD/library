package sh.harold.creative.library.entity.house;

import net.kyori.adventure.text.Component;

public enum HousePromptMode {
    CLICK("CLICK"),
    INTERACT("CLICK to INTERACT"),
    OPEN("CLICK to OPEN"),
    VIEW("CLICK to VIEW"),
    TALK("CLICK to TALK"),
    TRADE("CLICK to TRADE");

    private final String prompt;

    HousePromptMode(String prompt) {
        this.prompt = prompt;
    }

    public Component asComponent() {
        return Component.text(prompt);
    }
}
