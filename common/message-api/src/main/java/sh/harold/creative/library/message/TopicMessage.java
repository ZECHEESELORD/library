package sh.harold.creative.library.message;

public interface TopicMessage extends InlineMessage {

    Topic topic();

    @Override
    TopicMessage with(String name, Object value);

    @Override
    TopicMessage with(String name, MessageValue value);

    @Override
    TopicMessage hover(MessageBlock hover);
}
