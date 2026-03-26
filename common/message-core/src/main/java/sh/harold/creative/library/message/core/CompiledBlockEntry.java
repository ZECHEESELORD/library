package sh.harold.creative.library.message.core;

import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.MessageValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

record CompiledBlockEntry(MessageBlock.Entry entry, CompiledTemplate template) {

    CompiledBlockEntry {
        Objects.requireNonNull(entry, "entry");
    }

    Map<String, MessageValue> bindings() {
        return switch (entry) {
            case MessageBlock.LineEntry line -> line.bindings();
            case MessageBlock.BulletEntry bullet -> bullet.bindings();
            default -> Collections.emptyMap();
        };
    }
}
