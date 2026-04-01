package sh.harold.creative.library.example.paper;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.MessageValue;
import sh.harold.creative.library.message.SlotBinding;
import sh.harold.creative.library.message.paper.PaperMessageSender;

final class PaperExampleMessages {

    private final PaperMessageSender sender = new PaperMessageSender();

    void info(CommandSender target, String template, SlotBinding... slots) {
        sender.send(target, Message.info(template, slots));
    }

    void success(CommandSender target, String template, SlotBinding... slots) {
        sender.send(target, Message.success(template, slots));
    }

    void error(CommandSender target, String template, SlotBinding... slots) {
        sender.send(target, Message.error(template, slots));
    }

    void send(CommandSender target, MessageBlock block) {
        sender.send(target, block);
    }

    MessageValue command(String literal) {
        return Message.value(literal).color(NamedTextColor.YELLOW);
    }
}
