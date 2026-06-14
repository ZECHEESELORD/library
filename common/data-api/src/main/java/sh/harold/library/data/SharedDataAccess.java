package sh.harold.library.data;

import java.util.Optional;

public interface SharedDataAccess {

    DataNamespace defaultNamespace();

    Optional<DataNamespace> namespace(String name);
}
