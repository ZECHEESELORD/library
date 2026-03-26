package sh.harold.creative.library.example.paper;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.creative.library.data.memory.InMemoryDataApi;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.Tag;
import sh.harold.creative.library.menu.core.StandardMenuService;

public final class PaperExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        new InMemoryDataApi();
        new StandardMenuService();
        Message.info(
                "paper-example loaded for {host}.",
                Message.slot("host", "Paper")
        ).tag(Tag.DAEMON).hover(
                Message.block()
                        .title("+ PAPER EXAMPLE", NamedTextColor.GOLD)
                        .line("Shared content objects are ready.")
                        .build()
        );
        getLogger().info("paper-example scaffold loaded");
    }
}
