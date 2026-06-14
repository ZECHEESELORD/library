package sh.harold.library.message.core;

import sh.harold.library.message.InlineMessage;

sealed interface CompiledInlineMessage extends InlineMessage permits DefaultNoticeMessage, DefaultTopicMessage {

    CompiledTemplate compiledTemplate();
}
