package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;
import sh.harold.creative.library.menu.MenuTraceSnapshot;
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
                "Use {menus}, {messages}, {sounds}, {camera}, {overlays}, {npcs}, and {primitives} for the Minestom dev harness.",
                Message.slot("menus", command("/testmenus")),
                Message.slot("messages", command("/testmessages")),
                Message.slot("sounds", command("/testsoundfx")),
                Message.slot("camera", command("/testcamera")),
                Message.slot("overlays", command("/testoverlays")),
                Message.slot("npcs", command("/testnpcs")),
                Message.slot("primitives", command("/testprimitives help"))
        );
    }

    void sendSummary(Player player) {
        sender.send(player, Message.block()
                .title("DEV HARNESS", 0x55FF55)
                .line("Use these commands to preview library surfaces.")
                .blank()
                .bullet(
                        "{command} tabs|list|reactive|snake|lockdrag|lockclick|profile|farming|museum|slot5|canvas",
                        Message.slot("command", command("/testmenus"))
                )
                .bullet(
                        "{command} trace off|all|lockdrag|lockclick|status",
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
                        "{command} all|recoil|rumble|concussion|stagger|cinematic|stop",
                        Message.slot("command", command("/testcamera"))
                )
                .bullet(
                        "{command} demo|clear",
                        Message.slot("command", command("/testoverlays"))
                )
                .bullet(
                        "{command} reset|clear",
                        Message.slot("command", command("/testnpcs"))
                )
                .bullet(
                        "{command} help",
                        Message.slot("command", command("/testprimitives"))
                )
                .build());
    }

    void sendMenuTraceUpdated(Player player, String status) {
        success(
                player,
                "Menu tracing is now {status}. Logs print to the server console.",
                Message.slot("status", Message.value(status).color(NamedTextColor.YELLOW))
        );
    }

    void sendMenuTraceStatus(Player player, MenuTraceSnapshot snapshot) {
        info(
                player,
                "Menu tracing is {status}. Use {command} trace off|all|lockdrag|lockclick.",
                Message.slot("status", Message.value(traceStatus(snapshot)).color(NamedTextColor.YELLOW)),
                Message.slot("command", command("/testmenus"))
        );
    }

    sh.harold.creative.library.message.MessageValue command(String literal) {
        return Message.value(literal).color(NamedTextColor.YELLOW);
    }

    private static String traceStatus(MenuTraceSnapshot snapshot) {
        if (!snapshot.enabled()) {
            return "off";
        }
        if (snapshot.allMenus()) {
            return "all";
        }
        if (snapshot.menuTitles().stream().anyMatch(title -> title.equalsIgnoreCase(MinestomMenuExampleMenus.LOCK_DRAG_TITLE))) {
            return "lockdrag";
        }
        if (snapshot.menuTitles().stream().anyMatch(title -> title.equalsIgnoreCase(MinestomMenuExampleMenus.LOCK_CLICK_TITLE))) {
            return "lockclick";
        }
        return String.join(",", snapshot.menuTitles());
    }
}
