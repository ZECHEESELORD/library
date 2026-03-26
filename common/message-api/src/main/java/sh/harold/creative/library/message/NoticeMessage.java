package sh.harold.creative.library.message;

import java.util.List;

public interface NoticeMessage extends InlineMessage {

    NoticeType type();

    List<Tag> tags();

    NoticeMessage tag(Tag tag);

    @Override
    NoticeMessage with(String name, Object value);

    @Override
    NoticeMessage with(String name, MessageValue value);

    @Override
    NoticeMessage hover(MessageBlock hover);
}
