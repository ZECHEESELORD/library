package sh.harold.creative.library.sound;

import net.kyori.adventure.key.Key;

public final class SoundCueKeys {

    public static final String NAMESPACE = "creative-library";

    public static final Key MENU_CLICK = key("menu/click");
    public static final Key MENU_SCROLL = key("menu/scroll");
    public static final Key INTERACTION_NPC = key("interaction/npc");
    public static final Key RESULT_CONFIRM = key("result/confirm");
    public static final Key RESULT_DENY = key("result/deny");
    public static final Key REWARD_LEVEL_UP = key("reward/level_up");
    public static final Key REWARD_DISCOVERY = key("reward/discovery");

    private SoundCueKeys() {
    }

    private static Key key(String path) {
        return Key.key(NAMESPACE, path);
    }
}
