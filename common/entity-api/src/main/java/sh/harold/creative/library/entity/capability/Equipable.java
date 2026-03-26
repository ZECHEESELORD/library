package sh.harold.creative.library.entity.capability;

import sh.harold.creative.library.entity.EquipmentSlot;
import sh.harold.creative.library.entity.ItemDescriptor;

import java.util.Optional;

public interface Equipable {

    Optional<ItemDescriptor> equipment(EquipmentSlot slot);

    void equipment(EquipmentSlot slot, ItemDescriptor item);

    void clearEquipment(EquipmentSlot slot);
}
