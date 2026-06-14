package sh.harold.library.entity.capability;

import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.ItemDescriptor;

import java.util.Optional;

public interface Equipable {

    Optional<ItemDescriptor> equipment(EquipmentSlot slot);

    void equipment(EquipmentSlot slot, ItemDescriptor item);

    void clearEquipment(EquipmentSlot slot);
}
