package sh.harold.library.message.core;

import sh.harold.library.message.MessageBlock;

sealed interface CompiledMessageBlock extends MessageBlock permits DefaultMessageBlock {

    java.util.List<CompiledBlockEntry> compiledEntries();
}
