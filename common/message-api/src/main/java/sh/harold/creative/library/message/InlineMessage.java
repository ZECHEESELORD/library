package sh.harold.creative.library.message;

import net.kyori.adventure.audience.Audience;

import java.util.Map;
import java.util.Optional;

public interface InlineMessage {

    String template();

    Map<String, MessageValue> bindings();

    Optional<MessageBlock> hoverBlock();

    InlineMessage with(String name, Object value);

    InlineMessage with(String name, MessageValue value);

    InlineMessage hover(MessageBlock hover);

    void send(Audience audience);

    void sendActionBar(Audience audience);
}
