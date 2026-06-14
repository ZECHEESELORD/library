package sh.harold.library.data;

public interface SharedDataProvider {

    SharedDataAccess access(String callerId);
}
