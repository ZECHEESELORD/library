package sh.harold.creative.library.data.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import sh.harold.creative.library.data.core.StoreBackedDataApi;

import java.util.concurrent.Executor;

public final class MongoDataApi extends StoreBackedDataApi {

    public MongoDataApi(String connectionString, String databaseName) {
        super(MongoDocumentStore.owned(MongoClients.create(connectionString), databaseName, null));
    }

    public MongoDataApi(String connectionString, String databaseName, Executor executor) {
        super(MongoDocumentStore.owned(MongoClients.create(connectionString), databaseName, executor));
    }

    public MongoDataApi(MongoClient client, String databaseName) {
        super(new MongoDocumentStore(client, databaseName));
    }

    public MongoDataApi(MongoClient client, String databaseName, Executor executor) {
        super(new MongoDocumentStore(client, databaseName, executor));
    }
}
