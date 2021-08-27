package com.neal.test.script;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.neal.annotation.Flyway;
import com.neal.annotation.Script;
import org.bson.Document;

import static com.mongodb.client.model.Indexes.ascending;

/**
 * @author Neal
 */
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

    @Script(ticket = "MD-990", description = "a cross collection script", testMethod = "none", order = 3)
    public void executeCrossScript(MongoDatabase database) {
        database.getCollection("items").createIndex(ascending("update_time"));
    }

    @Script(ticket = "MD-799", description = "Init collect", testMethod = "none", order = 4, autoBackup = true)
    public void initCollect(MongoCollection<Document> collection) {
        collection.createIndex(ascending("created_time"));
    }

//    @Script(ticket = "MD-666", description = "Failed Script", testMethod = "quickFailed", order = 1)
//    public void executeScript2(MongoCollection<Document> collection) {
//        collection.createIndex(ascending("updated_time"));
//    }
//
//    public Boolean quickFailed(MongoCollection<Document> collection) {
//        throw new RuntimeException("quick failed test");
//    }
}
