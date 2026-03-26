package sh.harold.creative.library.example.velocity;

import com.google.inject.Inject;
import net.kyori.adventure.text.format.NamedTextColor;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;
import sh.harold.creative.library.data.memory.InMemoryDataApi;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.Tag;

@Plugin(
    id = "velocity-example",
    name = "velocity-example",
    version = "0.1.0-SNAPSHOT"
)
public final class VelocityExamplePlugin {

    @Inject
    public VelocityExamplePlugin(Logger logger) {
        new InMemoryDataApi();
        Message.debug(
                "velocity-example loaded for {host}.",
                Message.slot("host", "Velocity")
        ).tag(Tag.DAEMON).hover(
                Message.block()
                        .title("+ VELOCITY EXAMPLE", NamedTextColor.GOLD)
                        .line("Shared content objects are ready.")
                        .build()
        );
        logger.info("velocity-example scaffold loaded");
    }
}
