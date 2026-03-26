package sh.harold.creative.library.entity.capability;

public interface PersistenceCapable {

    boolean persistent();

    void persistent(boolean persistent);
}
