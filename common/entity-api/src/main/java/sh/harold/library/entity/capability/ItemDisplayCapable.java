package sh.harold.library.entity.capability;

import sh.harold.library.entity.ItemDescriptor;

public interface ItemDisplayCapable extends DisplayCapable {

    ItemDescriptor item();

    void item(ItemDescriptor item);
}
