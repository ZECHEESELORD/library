package sh.harold.library.entity.capability;

public interface PersistenceCapable {

    boolean persistent();

    void persistent(boolean persistent);
}
