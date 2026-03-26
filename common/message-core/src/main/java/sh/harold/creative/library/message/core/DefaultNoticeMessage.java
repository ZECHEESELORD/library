package sh.harold.creative.library.message.core;

import net.kyori.adventure.audience.Audience;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.MessageValue;
import sh.harold.creative.library.message.NoticeMessage;
import sh.harold.creative.library.message.NoticeType;
import sh.harold.creative.library.message.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultNoticeMessage implements NoticeMessage, CompiledInlineMessage {

    private final NoticeType type;
    private final String template;
    private final CompiledTemplate compiledTemplate;
    private final Map<String, MessageValue> bindings;
    private final MessageBlock hoverBlock;
    private final List<Tag> tags;

    public DefaultNoticeMessage(NoticeType type, String template, Map<String, MessageValue> bindings, MessageBlock hoverBlock, List<Tag> tags) {
        this.type = Objects.requireNonNull(type, "type");
        this.template = Objects.requireNonNull(template, "template");
        this.compiledTemplate = CompiledTemplate.parse(template);
        this.bindings = MessageBindings.copy(bindings);
        this.compiledTemplate.validate(this.bindings);
        this.hoverBlock = hoverBlock;
        this.tags = copyTags(tags);
    }

    @Override
    public NoticeType type() {
        return type;
    }

    @Override
    public String template() {
        return template;
    }

    @Override
    public Map<String, MessageValue> bindings() {
        return bindings;
    }

    @Override
    public Optional<MessageBlock> hoverBlock() {
        return Optional.ofNullable(hoverBlock);
    }

    @Override
    public List<Tag> tags() {
        return tags;
    }

    @Override
    public NoticeMessage tag(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tags.contains(tag)) {
            return this;
        }
        List<Tag> updated = new ArrayList<>(tags);
        updated.add(tag);
        return new DefaultNoticeMessage(type, template, bindings, hoverBlock, updated);
    }

    @Override
    public NoticeMessage with(String name, Object value) {
        return with(name, Message.value(value));
    }

    @Override
    public NoticeMessage with(String name, MessageValue value) {
        return new DefaultNoticeMessage(type, template, MessageBindings.with(bindings, name, value), hoverBlock, tags);
    }

    @Override
    public NoticeMessage hover(MessageBlock hover) {
        return new DefaultNoticeMessage(type, template, bindings, Objects.requireNonNull(hover, "hover"), tags);
    }

    @Override
    public void send(Audience audience) {
        Objects.requireNonNull(audience, "audience");
        audience.sendMessage(DefaultMessageRenderer.INSTANCE.renderInline(this, RenderTarget.CHAT));
    }

    @Override
    public void sendActionBar(Audience audience) {
        Objects.requireNonNull(audience, "audience");
        audience.sendActionBar(DefaultMessageRenderer.INSTANCE.renderInline(this, RenderTarget.ACTION_BAR));
    }

    @Override
    public CompiledTemplate compiledTemplate() {
        return compiledTemplate;
    }

    private static List<Tag> copyTags(List<Tag> tags) {
        Objects.requireNonNull(tags, "tags");
        ArrayList<Tag> copy = new ArrayList<>();
        for (Tag tag : tags) {
            Objects.requireNonNull(tag, "tag");
            if (!copy.contains(tag)) {
                copy.add(tag);
            }
        }
        return List.copyOf(copy);
    }
}
