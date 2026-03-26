package sh.harold.creative.library.message.core;

import sh.harold.creative.library.message.InlineMessage;

sealed interface CompiledInlineMessage extends InlineMessage permits DefaultNoticeMessage, DefaultTopicMessage {

    CompiledTemplate compiledTemplate();
}
