package sh.harold.creative.library.data;

public interface SharedDataProvider {

    SharedDataAccess access(String callerId);
}
