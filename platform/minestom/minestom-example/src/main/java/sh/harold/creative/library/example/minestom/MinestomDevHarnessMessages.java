package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.SlotBinding;
import sh.harold.creative.library.message.minestom.MinestomMessageSender;

final class MinestomDevHarnessMessages {

    private final MinestomMessageSender sender = new MinestomMessageSender();

    void info(Player player, String template, SlotBinding... slots) {
        sender.send(player, Message.info(template, slots));
    }

    void success(Player player, String template, SlotBinding... slots) {
        sender.send(player, Message.success(template, slots));
    }

    void error(Player player, String template, SlotBinding... slots) {
        sender.send(player, Message.error(template, slots));
    }

    void sendQuickStart(Player player) {
        info(
                player,
                "Use {menus}, {messages}, {sounds}, and {npcs} for the Minestom dev harness.",
                Message.slot("menus", command("/testmenus")),
                Message.slot("messages", command("/testmessages")),
                Message.slot("sounds", command("/testsoundfx")),
                Message.slot("npcs", command("/testnpcs"))
        );
    }

    void sendSummary(Player player) {
        sender.send(player, Message.block()
                .title("DEV HARNESS", 0x55FF55)
                .line("Use these commands to preview library surfaces.")
                .blank()
                .bullet(
                        "{command} tabs|list|profile|farming|museum|slot5|canvas",
                        Message.slot("command", command("/testmenus"))
                )
                .bullet(
                        "{command} all|notices|topics|clicks|block",
                        Message.slot("command", command("/testmessages"))
                )
                .bullet(
                        "{command} all|menu|npc|confirm|deny|levelup|discovery",
                        Message.slot("command", command("/testsoundfx"))
                )
                .bullet(
                        "{command} reset|clear",
                        Message.slot("command", command("/testnpcs"))
                )
                .build());
    }

    private static sh.harold.creative.library.message.MessageValue command(String literal) {
        return Message.value(literal).color(NamedTextColor.YELLOW);
    }
}
