package sh.harold.library.movement;

import net.kyori.adventure.key.Key;

import java.util.List;

/** Keys of the stock moves from 02 ("Movement and camera"). */
public final class MoveKeys {

    public static final String NAMESPACE = "movement";

    /** The variable jump (presets A, B and C). Enabling it hands ground jumps to the core. */
    public static final Key JUMP = Key.key(NAMESPACE, "jump");
    public static final Key MANTLE = Key.key(NAMESPACE, "mantle");
    public static final Key ROLL = Key.key(NAMESPACE, "roll");
    public static final Key SLIDE = Key.key(NAMESPACE, "slide");
    public static final Key WALL_JUMP = Key.key(NAMESPACE, "wall_jump");
    /** The wall slide, part of the wall-jump move: enabled with it, claimed separately. */
    public static final Key WALL_SLIDE = Key.key(NAMESPACE, "wall_slide");

    /** The five moves a moveset enables, in 02's order. */
    public static final List<Key> STOCK = List.of(JUMP, MANTLE, ROLL, SLIDE, WALL_JUMP);

    private MoveKeys() {
    }
}
