package sh.harold.creative.library.entity.capability;

import sh.harold.creative.library.entity.ManagedEntity;

import java.util.List;
import java.util.UUID;

public interface PassengerCapable {

    List<UUID> passengers();

    boolean addPassenger(ManagedEntity entity);

    boolean removePassenger(ManagedEntity entity);
}
