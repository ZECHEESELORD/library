package sh.harold.library.telemetry.fabric;

import net.minecraft.world.entity.player.Input;
import sh.harold.library.telemetry.TelemetryRegistry;

import java.util.function.Function;

/** Registers the seven movement-input columns ({@code input_forward} ... {@code input_sprint}) from an {@link Input}. */
public final class InputColumns {

    private InputColumns() {
    }

    public static <C> void register(TelemetryRegistry<C> registry, Function<C, Input> input) {
        registry.registerBoolean("input_forward", context -> input.apply(context).forward());
        registry.registerBoolean("input_backward", context -> input.apply(context).backward());
        registry.registerBoolean("input_left", context -> input.apply(context).left());
        registry.registerBoolean("input_right", context -> input.apply(context).right());
        registry.registerBoolean("input_jump", context -> input.apply(context).jump());
        registry.registerBoolean("input_shift", context -> input.apply(context).shift());
        registry.registerBoolean("input_sprint", context -> input.apply(context).sprint());
    }
}
