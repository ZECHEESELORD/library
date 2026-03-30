package sh.harold.creative.library.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import sh.harold.creative.library.ui.value.UiValue;
import sh.harold.creative.library.message.core.DefaultMessageBlockBuilder;
import sh.harold.creative.library.message.core.DefaultNoticeMessage;
import sh.harold.creative.library.message.core.DefaultTopicMessage;
import sh.harold.creative.library.message.core.MessageBindings;

import java.util.Map;
import java.util.Objects;

public final class Message {

    private Message() {
    }

    public static NoticeMessage info(String template, SlotBinding... slots) {
        return new DefaultNoticeMessage(NoticeType.INFO, template, bindings(slots), null, java.util.List.of());
    }

    public static NoticeMessage success(String template, SlotBinding... slots) {
        return new DefaultNoticeMessage(NoticeType.SUCCESS, template, bindings(slots), null, java.util.List.of());
    }

    public static NoticeMessage error(String template, SlotBinding... slots) {
        return new DefaultNoticeMessage(NoticeType.ERROR, template, bindings(slots), null, java.util.List.of());
    }

    public static NoticeMessage debug(String template, SlotBinding... slots) {
        return new DefaultNoticeMessage(NoticeType.DEBUG, template, bindings(slots), null, java.util.List.of());
    }

    public static TopicMessage topic(Topic topic, String template, SlotBinding... slots) {
        return new DefaultTopicMessage(topic, template, bindings(slots), null);
    }

    public static MessageBlockBuilder block() {
        return new DefaultMessageBlockBuilder();
    }

    public static SlotBinding slot(String name, Object value) {
        return new SlotBinding(name, value(value));
    }

    public static SlotBinding slot(String name, MessageValue value) {
        return new SlotBinding(name, Objects.requireNonNull(value, "value"));
    }

    public static MessageValue value(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof MessageValue messageValue) {
            return messageValue;
        }
        if (value instanceof UiValue uiValue) {
            return MessageValue.of(uiValue);
        }
        if (value instanceof Component component) {
            return value(component);
        }
        if (value instanceof ComponentLike componentLike) {
            return value(componentLike.asComponent());
        }
        return MessageValue.of(Component.text(String.valueOf(value)));
    }

    public static MessageValue value(Component value) {
        return MessageValue.of(Objects.requireNonNull(value, "value"));
    }

    private static Map<String, MessageValue> bindings(SlotBinding... slots) {
        return MessageBindings.fromSlots(slots);
    }
}
