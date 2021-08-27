package com.neal.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.neal.annotation.Flyway;
import com.neal.annotation.Script;
import com.neal.domain.FlywayScriptHistory;
import com.neal.util.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Neal
 */
public class FlywayService {
    private final Logger logger = LoggerFactory.getLogger(FlywayService.class);
    private final MongoCollection<FlywayScriptHistory> collection;

    public FlywayService(MongoDatabase database) {
        this(database, "flyway_script_histories");
    }

    /**
     * initial flyway
     *
     * @param database       mongo data base
     * @param collectionName target history collection name
     */
    public FlywayService(MongoDatabase database, String collectionName) {
        this.collection = database.getCollection(collectionName, FlywayScriptHistory.class);
    }

    public List<Class<?>> scanFlywayClass(String packagePath) {
        try {
            return ClassUtils.getClassesWithAnnotation(packagePath, Flyway.class);
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
        return List.of();
    }

    /**
     * Get script annotation from class
     *
     * @param clazz input class
     * @return class annotation
     */
    public Map<String, ? extends Script> getClassScriptMap(Class<?> clazz) {
        Map<String, Script> scriptMap = new HashMap<>();
        for (Method method : clazz.getDeclaredMethods()) {
            Annotation annotation = method.getAnnotation(Script.class);
            if (annotation == null) {
                continue;
            }
            Script script = (Script) annotation;
            scriptMap.put(method.getName(), script);
        }
        return scriptMap;
    }

    /**
     * reindex
     */
    public void ensureIndex() {
        collection.createIndex(Indexes.ascending("collection"));
    }

    /**
     * check already run
     *
     * @param id
     * @return yes or not.
     */
    public boolean isAlreadyRun(String id) {
        return collection.countDocuments(Filters.and(Filters.eq("_id", id), Filters.eq("is_success", Boolean.TRUE))) > 0;
    }

    public void saveHistory(String id, Script script, String collectionName, long elapsedTime, boolean isSuccess) {
        FlywayScriptHistory flywayScriptHistory = new FlywayScriptHistory();
        flywayScriptHistory.id = id;
        flywayScriptHistory.collection = collectionName;
        flywayScriptHistory.ticket = script.ticket();
        flywayScriptHistory.description = script.description();
        flywayScriptHistory.isSuccess = isSuccess;
        flywayScriptHistory.elapsedTime = elapsedTime;
        flywayScriptHistory.createdTime = LocalDateTime.now();
        collection.replaceOne(Filters.eq("_id", id), flywayScriptHistory, new ReplaceOptions().upsert(true));
    }
}
