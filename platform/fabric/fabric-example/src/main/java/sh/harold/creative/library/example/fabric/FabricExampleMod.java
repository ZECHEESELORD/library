package sh.harold.creative.library.example.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.fabric.FabricMessageSender;
import sh.harold.creative.library.menu.ActionVerb;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuButton;
import sh.harold.creative.library.menu.MenuDisplayItem;
import sh.harold.creative.library.menu.MenuIcon;
import sh.harold.creative.library.menu.ReactiveListView;
import sh.harold.creative.library.menu.ReactiveMenu;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveMenuResult;
import sh.harold.creative.library.menu.ReactiveTextPromptRequest;
import sh.harold.creative.library.menu.UtilitySlot;
import sh.harold.creative.library.menu.fabric.FabricMenuPlatform;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.fabric.FabricServerSoundCuePlatform;

public final class FabricExampleMod implements ModInitializer {

    private final FabricMessageSender messages = new FabricMessageSender();
    private final FabricServerSoundCuePlatform sounds = new FabricServerSoundCuePlatform();
    private final FabricMenuPlatform menus = new FabricMenuPlatform();

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            menus.close();
            sounds.close();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            messages.send(handler.getPlayer(), Message.success("Welcome to the Fabric example!"));
            sounds.play(handler.getPlayer(), SoundCueKeys.REWARD_DISCOVERY);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
                Commands.literal("creative-library-menu")
                        .requires(source -> source.getPlayer() != null)
                        .then(Commands.literal("compiled")
                                .executes(context -> {
                                    openCompiledMenu(context.getSource().getPlayer());
                                    return 1;
                                }))
                        .then(Commands.literal("prompt")
                                .executes(context -> {
                                    openReactivePromptMenu(context.getSource().getPlayer());
                                    return 1;
                                }))));
    }

    private void openCompiledMenu(ServerPlayer player) {
        menus.open(player, compiledMenu());
    }

    private void openReactivePromptMenu(ServerPlayer player) {
        menus.open(player, reactivePromptMenu(new PromptState("")));
    }

    private Menu compiledMenu() {
        return menus.list()
                .title("Fabric Compiled")
                .addItem(MenuButton.builder(MenuIcon.vanilla("book"))
                        .name("Server-Side Menu")
                        .description("This list menu is rendered entirely from the Fabric server backend.")
                        .action(ActionVerb.VIEW, context -> { })
                        .build())
                .addItem(MenuButton.builder(MenuIcon.vanilla("compass"))
                        .name("Reactive Prompt Demo")
                        .description("Run /creative-library-menu prompt to open the native text prompt example.")
                        .action(ActionVerb.BROWSE, context -> { })
                        .build())
                .build();
    }

    private ReactiveMenu reactivePromptMenu(PromptState initialState) {
        return menus.reactiveList()
                .state(initialState)
                .render(state -> ReactiveListView.builder("Fabric Prompt")
                        .addItem(MenuDisplayItem.builder(MenuIcon.vanilla("book"))
                                .name(state.query().isBlank() ? "No Query" : "Query: " + state.query())
                                .description("The search button uses the shared PROMPT flow and reopens the menu on submit.")
                                .build())
                        .utility(UtilitySlot.RIGHT_1, MenuButton.builder(MenuIcon.vanilla("writable_book"))
                                .name(state.query().isBlank() ? "Search" : "Search: " + state.query())
                                .emit(ActionVerb.BROWSE, "search", "open-search")
                                .build())
                        .build())
                .reduce((state, input) -> {
                    if (input instanceof ReactiveMenuInput.Click click && "open-search".equals(click.message())) {
                        return ReactiveMenuResult.of(state, new ReactiveMenuEffect.RequestTextPrompt(
                                ReactiveTextPromptRequest.prompt("prompt-search", "Search", state.query())));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted && submitted.key().equals("prompt-search")) {
                        return ReactiveMenuResult.stay(new PromptState(submitted.value()));
                    }
                    if (input instanceof ReactiveMenuInput.TextPromptCancelled cancelled && cancelled.key().equals("prompt-search")) {
                        return ReactiveMenuResult.stay(state);
                    }
                    return ReactiveMenuResult.stay(state);
                })
                .build();
    }

    private record PromptState(String query) {
    }
}
