package sh.harold.library.entity.capability;

import sh.harold.library.entity.ManagedEntity;

import java.util.List;
import java.util.UUID;

public interface PassengerCapable {

    List<UUID> passengers();

    boolean addPassenger(ManagedEntity entity);

    boolean removePassenger(ManagedEntity entity);
}
