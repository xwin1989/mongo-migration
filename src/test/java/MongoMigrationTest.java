
import com.neal.MongoMigration;
import org.bson.Document;
import org.junit.Test;

/**
 * @author Neal
 */
public class MongoMigrationTest {
    @Test
    public void testMigration() {
        var uri = "mongodb://localhost:27017/recipe"; //test mongoURI ?
        MongoMigration migration = new MongoMigration(uri);
        migration.scanPackagePath("com.neal.test.script");
        migration.migration();
    }

    @Test
    public void testExecute() {
        var uri = "mongodb://localhost:27017/admin"; //test mongoURI ?
        MongoMigration migration = new MongoMigration(uri);
        migration.execute(mongoDatabase -> {
            mongoDatabase.runCommand(new Document().append("setParameter", 1).append("notablescan", 1));
        });
    }
}
