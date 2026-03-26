package sh.harold.creative.library.example.minestom;

import net.minestom.server.MinecraftServer;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.harold.creative.library.data.memory.InMemoryDataApi;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.Topics;
import sh.harold.creative.library.menu.core.StandardMenuService;

public final class MinestomExampleBootstrap {

    private MinestomExampleBootstrap() {
    }

    public static void main(String[] args) {
        MinecraftServer.init();
        new InMemoryDataApi();
        new StandardMenuService();
        Message.topic(
                Topics.SOUL,
                "minestom-example loaded for {host}.",
                Message.slot("host", "Minestom")
        ).hover(
                Message.block()
                        .title("+ MINESTOM EXAMPLE", NamedTextColor.GOLD)
                        .line("Shared content objects are ready.")
                        .build()
        );
    }
}
