package web;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Provides;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.UnknownHostException;
import java.util.Set;


public class MongoModule extends AbstractModule {

    @Override
    protected void configure() {
        bindConstant().annotatedWith(DatabaseName.class).to("imdb");
    }

    @Provides
    public DB provideDatabase(@DatabaseName String databaseName) throws UnknownHostException {
        // Connect to local machine.
        MongoClient mongo = new MongoClient("127.0.0.1");
        // Select database `imdb`.
        DB db = mongo.getDB(databaseName);
        // Enable Full Text Search.
        enableTextSearch(mongo);
        // Print out all collections.
        printCollections(db);

        return db;
    }

    /**
     * Output all Collections known to the database.
     */
    private void printCollections(DB db) {
        Set<String> colls = db.getCollectionNames();
        System.out.println("Connected to MongoDB\nCollections in imdb db: " + colls.size());
        for (String s : colls) {
            System.out.println("- " + s);
        }
    }

    /**
     * Enables Full Text Search.
     */
    private void enableTextSearch(MongoClient mongo) {
        DB admin = mongo.getDB("admin");
        DBObject cmd = new BasicDBObject();
        cmd.put("setParameter", 1);
        cmd.put("textSearchEnabled", true);
        admin.command(cmd);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @BindingAnnotation
    public @interface DatabaseName {}
}
