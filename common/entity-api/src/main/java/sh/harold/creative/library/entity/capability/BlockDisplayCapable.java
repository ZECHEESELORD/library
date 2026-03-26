package sh.harold.creative.library.entity.capability;

import sh.harold.creative.library.entity.BlockDescriptor;

public interface BlockDisplayCapable extends DisplayCapable {

    BlockDescriptor block();

    void block(BlockDescriptor block);
}
