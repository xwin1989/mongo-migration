# mongo-migration

The current migration function is not suitable for the current frequency of script execution,so we need enhance it.

## Latest Version

| version | description
|  ----  | ----  |
| 2.2.0| throw exception if test execute failed.|
| 2.1.0 | add auto backup function|
| 2.0.0 | # remove id from @Script <br> # add new Script ID generate logic <br> If you upgrade from old version, please check you script ensure them can re-cycle execute, or you can clean your old script.
| 1.1.0 | add logger for scrip execute|
| 1.0.5 | add run environment check|
| 1.0.4 | add sort to script, use to control execute sorted.|

## How to use

### 1. initial build.gradle
```java
configure(subprojects.findAll { it.name.endsWith('-mongo-migration') }) {
    apply from: file("${rootDir}/gradle/app.gradle")
    dependencies {
        implementation "core.framework:core-ng-mongo:${coreNGVersion}"
        implementation "com.wonder:core-ext-mongo-migration:${coreExtMongoMigrationVersion}"
    }
    tasks.register('mongoMigrate') {
        dependsOn run
    }
}
```
### 2. initial Main.class

If you wan to execute command to MongoDB, you can use that function execute,  like that:
```java
new MongoMigration("localhost:27017"")  
  .execute(database -> database.runCommand(new Document().append("setParameter", 1).append("notablescan", 1)));
```
### 3.enable script scan

Specify the package to be scanned, use function scanPackagePath, like that:
```java
new MongoMigration(uri).scanPackagePath("app.recipe.script").migration();
```
### 4. Main demo
```java
public class Main {
    public static void main(String[] args) {
        var properties = new Properties();
        properties.load("sys.properties");
        String uri = properties.get("sys.mongo.uri").orElseThrow();
        String adminURI = properties.get("sys.mongo.adminURI").orElseThrow();
        new MongoMigration(adminURI).execute(database -> database.runCommand(new Document().append("setParameter", 1).append("notablescan", 1)));
        new MongoMigration(uri).scanPackagePath("app.recipe.script").migration();
    }
}
```
### 5.Define Script

#### 5.1 Use @Flyway annotation

Place the annotation on the class of the script that needs to be executed. framework will auto specify current  collection to execute script. like that:
```java
@Flyway(collection = "item_customizations")
public class ItemCustomizationScript {}
```
#### 5.2 Use @Script annotation

Place the annotation on the method, provide argument like that:

| Field| type| Descript|
| -- | -- | -- |
| ticket| !String|Issue number|
| description | !String | Issue description|
| testMethod | !String | Target test method ( must in current class ),<br> If you don’t need test you script, you can input none|
| runAlways | Boolean | Execute every times, default is false. |
| order | int | Execute oder. default is -1. if order is will be random|
| runAt | String[] | specify runnable environment,default is all environment.("dev", "uat", "staging", "prod")|

Since 2.0.0 already deprecated id field, system will auto generate script target ID  and ID format is $collection_$ticket_$methodName.

### 5.3 Run At

Because the existing program execution is not aware of which environment, we need to adjust the jenkins publish script, add -Penv=${env} to stage block.

If you don’t need use this function , you can skip this.
```java
stage('db-migration') {
    sh "./gradlew -p ${parent} -Penv=${env} flywayMigrate"
}
```
#### 5.4 Script demo

After the script is executed, the test method will be forced to ensure that the entire script results are as expected.
```java
@Flyway(collection = "items")
public class ItemScript {

    @Script(ticket = "0000", description = "0000", testMethod = "none", runAlways = true)
    public void initIndex(MongoCollection<Document> collection) {
        collection.createIndex(ascending("search_name"));
        collection.createIndex(ascending("unit_conversions.to_quantity"));
    }

    @Script(ticket = "MD-242", description = "Galley Warning - Usage Unit Unique", testMethod = "testInitBatchNumber", order = 1)
    public void initBatchNumber(MongoCollection<Document> collection) {
        collection.updateMany(Filters.and(Filters.ne("_id", null), Filters.eq("batch_number_group_code", null)),
            Updates.set("batch_number_group_code", "test2"));
    }

    public Boolean testInitBatchNumber(MongoCollection<Document> collection) {
        return collection.countDocuments(Filters.and(Filters.ne("_id", null), Filters.ne("batch_number_group_code", "test2"))) >= 0;
    }

    @Script(ticket = "MD-787", description = "One option value support multiple mapping items", testMethod = "none", runAlways = true, order = 2, runAt = {"uat", "prod"})
    public void addIndexItemNumber(MongoCollection<Document> collection) {
        collection.createIndex(ascending("options.option_values.items.item_number"));
    }
}
```

#### 5.5 Execute Script Cross Collection

Sometimes we also need execute script through multiple collections, at migration framework, you can use MongoDatabase parameter replace MongoCollection, like that:
```java
@Script(ticket = "MD-990", description = "a cross collection script", testMethod = "none", order = 3)
public void executeCrossScript(MongoDatabase database) {
    database.getCollection("items").createIndex(ascending("update_time"));
}
```
### 6.Enable auto backup

Sometimes we need backup collection when we run a new update script, now you can enable autoBackup on @Script annotation. default new collection name format is that   $collection_name_backup_$tickect_$date(yyyyMMddHHmmss) 

demo like that:
```java
@Script(ticket = "MD-799", description = "Init collect", testMethod = "none", order = 4, autoBackup = true)
public void initCollect(MongoCollection<Document> collection) {
    collection.createIndex(ascending("created_time"));
}
```
### 7.Store history in DB

Record execution will store into specify MongoDB, default table name: flyway_script_histories

you can see store structural like that:
```json
{
    "_id": "items_MD-242_initBatchNumber",
    "collection": "items",
    "created_time": ISODate("2021-04-29T07:00:44.109Z"),
    "description": "Items page: Search by 'no restaurant', search results are not fully displayed on the list",
    "elapsed_time": NumberLong("131131842"),
    "id": "update_restaurant_ids",
    "is_success": true,
    "ticket": "MD-1116"
}
```

