package sh.harold.creative.library.entity.capability;

import sh.harold.creative.library.entity.ItemDescriptor;

public interface ItemDisplayCapable extends DisplayCapable {

    ItemDescriptor item();

    void item(ItemDescriptor item);
}
