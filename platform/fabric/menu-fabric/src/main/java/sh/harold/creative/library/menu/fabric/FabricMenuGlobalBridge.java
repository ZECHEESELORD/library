package sh.harold.creative.library.menu.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class FabricMenuGlobalBridge {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final Set<FabricMenuRuntime> RUNTIMES = ConcurrentHashMap.newKeySet();

    private FabricMenuGlobalBridge() {
    }

    static void register(FabricMenuRuntime runtime) {
        init();
        RUNTIMES.add(Objects.requireNonNull(runtime, "runtime"));
    }

    static void unregister(FabricMenuRuntime runtime) {
        RUNTIMES.remove(runtime);
    }

    private static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        ServerTickEvents.START_SERVER_TICK.register(server -> RUNTIMES.forEach(FabricMenuRuntime::onServerTick));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                RUNTIMES.forEach(runtime -> runtime.onPlayerDisconnect(handler.getPlayer())));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            for (FabricMenuRuntime runtime : RUNTIMES) {
                if (runtime.handleChatMessage(sender, message.signedContent())) {
                    return false;
                }
            }
            return true;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
                Commands.literal(FabricMenuRuntime.PROMPT_COMMAND)
                        .requires(source -> source.getPlayer() != null)
                        .then(Commands.literal("submit")
                                .then(Commands.argument("token", StringArgumentType.word())
                                        .executes(context -> executeSubmit(context.getSource(),
                                                StringArgumentType.getString(context, "token"), ""))
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(context -> executeSubmit(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "token"),
                                                        StringArgumentType.getString(context, "value"))))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("token", StringArgumentType.word())
                                        .executes(context -> executeCancel(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "token")))))));
    }

    private static int executeSubmit(CommandSourceStack source, String token, String value) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        for (FabricMenuRuntime runtime : RUNTIMES) {
            if (runtime.handlePromptCommand(player, token, value, false)) {
                return 1;
            }
        }
        return 0;
    }

    private static int executeCancel(CommandSourceStack source, String token) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        for (FabricMenuRuntime runtime : RUNTIMES) {
            if (runtime.handlePromptCommand(player, token, "", true)) {
                return 1;
            }
        }
        return 0;
    }
}
