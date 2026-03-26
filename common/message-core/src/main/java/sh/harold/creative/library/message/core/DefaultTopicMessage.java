package sh.harold.creative.library.message.core;

import net.kyori.adventure.audience.Audience;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.MessageValue;
import sh.harold.creative.library.message.Topic;
import sh.harold.creative.library.message.TopicMessage;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultTopicMessage implements TopicMessage, CompiledInlineMessage {

    private final Topic topic;
    private final String template;
    private final CompiledTemplate compiledTemplate;
    private final Map<String, MessageValue> bindings;
    private final MessageBlock hoverBlock;

    public DefaultTopicMessage(Topic topic, String template, Map<String, MessageValue> bindings, MessageBlock hoverBlock) {
        this.topic = Objects.requireNonNull(topic, "topic");
        this.template = Objects.requireNonNull(template, "template");
        this.compiledTemplate = CompiledTemplate.parse(template);
        this.bindings = MessageBindings.copy(bindings);
        this.compiledTemplate.validate(this.bindings);
        this.hoverBlock = hoverBlock;
    }

    @Override
    public Topic topic() {
        return topic;
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
    public TopicMessage with(String name, Object value) {
        return with(name, Message.value(value));
    }

    @Override
    public TopicMessage with(String name, MessageValue value) {
        return new DefaultTopicMessage(topic, template, MessageBindings.with(bindings, name, value), hoverBlock);
    }

    @Override
    public TopicMessage hover(MessageBlock hover) {
        return new DefaultTopicMessage(topic, template, bindings, Objects.requireNonNull(hover, "hover"));
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
}
