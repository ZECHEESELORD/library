package sh.harold.library.entity.capability;

import sh.harold.library.entity.BlockDescriptor;

public interface BlockDisplayCapable extends DisplayCapable {

    BlockDescriptor block();

    void block(BlockDescriptor block);
}
