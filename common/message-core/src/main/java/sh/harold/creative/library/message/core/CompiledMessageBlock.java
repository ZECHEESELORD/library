package sh.harold.creative.library.message.core;

import sh.harold.creative.library.message.MessageBlock;

sealed interface CompiledMessageBlock extends MessageBlock permits DefaultMessageBlock {

    java.util.List<CompiledBlockEntry> compiledEntries();
}
