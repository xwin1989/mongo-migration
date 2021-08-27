package com.neal;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.neal.annotation.Flyway;
import com.neal.annotation.Script;
import com.neal.exception.FlywayExecuteException;
import com.neal.service.FlywayService;
import com.neal.util.StopWatch;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.jsr310.LocalDateTimeCodec;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

/**
 * @author Neal
 */
public class MongoMigration {
    private final Logger logger = LoggerFactory.getLogger(MongoMigration.class);
    private final MongoClientSettings clientSettings;
    private final String dataBase;
    private final String runEnvironment;
    private String packagePath;


    public MongoMigration(String uri) {
        var connectionString = new ConnectionString(uri);
        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry timeCodecRegistry = CodecRegistries.fromCodecs(List.of(new LocalDateTimeCodec()));
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), timeCodecRegistry, pojoCodecRegistry);
        clientSettings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .codecRegistry(codecRegistry)
            .build();
        dataBase = connectionString.getDatabase();
        runEnvironment = System.getProperty("env", "dev");
        logger.info("current execute environment #{}", runEnvironment);
    }

    public MongoMigration scanPackagePath(String packagePath) {
        this.packagePath = packagePath;
        return this;
    }

    /**
     * Simple execute script to mongo
     *
     * @param consumer
     */
    public void execute(Consumer<MongoDatabase> consumer) {
        try (MongoClient mongoClient = MongoClients.create(clientSettings)) {
            MongoDatabase database = mongoClient.getDatabase(dataBase);
            consumer.accept(database);
        }
    }

    /**
     * do script migration
     */
    public void migration() {
        Objects.requireNonNull(packagePath);

        try (MongoClient mongoClient = MongoClients.create(clientSettings)) {
            MongoDatabase db = mongoClient.getDatabase(dataBase);
            FlywayService flywayService = new FlywayService(db);
            flywayService.ensureIndex();
            for (Class<?> clazz : flywayService.scanFlywayClass(packagePath)) {
                migration(db, flywayService, clazz);
            }
        } catch (Throwable e) {
            logger.error( e.getMessage(), e);
            throw new FlywayExecuteException("Flyway script invoked failed! Please check your script.", e);
        }
    }

    /**
     * Do migration
     */
    private void migration(MongoDatabase db, FlywayService flywayService, Class<?> clazz) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Flyway flyway = clazz.getAnnotation(Flyway.class);
        MongoCollection<Document> collection = db.getCollection(flyway.collection());
        Object instance = clazz.getDeclaredConstructor((Class<?>[]) null).newInstance();

        Map<String, Method> methodMap = Arrays.stream(clazz.getDeclaredMethods()).collect(Collectors.toMap(Method::getName, Function.identity()));
        Map<String, ? extends Script> scriptMap = flywayService.getClassScriptMap(clazz);
        List<Method> scriptMethods = methodMap.entrySet().stream().filter(it -> scriptMap.containsKey(it.getKey())).map(Map.Entry::getValue)
            .sorted(Comparator.comparingInt(it -> scriptMap.get(it.getName()).order())).collect(Collectors.toList());

        StopWatch stopWatch = new StopWatch();
        for (Method method : scriptMethods) {
            Script script = scriptMap.get(method.getName());
            if (script.runAt().length > 0 && !List.of(script.runAt()).contains(this.runEnvironment)) {
                logger.info("skip collect {}, script {}", flyway.collection(), method.getName());
                continue;
            }

            boolean needRunTest = !"none".equalsIgnoreCase(script.testMethod());
            if (needRunTest) {
                checkTestMethod(instance, methodMap.get(script.testMethod()));
            }
            String scriptId = generateId(flyway.collection(), script.ticket(), method.getName());
            if (script.runAlways() || !flywayService.isAlreadyRun(scriptId)) {
                stopWatch.elapsed();
                boolean isSuccess = false;
                try {
                    if (script.autoBackup()) {
                        logger.info("Auto backup collect#{} begin", flyway.collection());
                        backupCollection(flyway, script, collection);
                        logger.info("Auto backup collect#{} end", flyway.collection());
                    }
                    invokeMethod(method, instance, db, collection);
                    logger.info("Collect #{}, ticket #{}, Invoke class #{}, method #{}", flyway.collection(), script.ticket(), instance.getClass().getCanonicalName(), method.getName());
                    if (needRunTest) {
                        isSuccess = (Boolean) invokeMethod(methodMap.get(script.testMethod()), instance, db, collection);
                    } else {
                        isSuccess = true;
                    }
                    if (!isSuccess) {
                        throw new FlywayExecuteException(String.format("Flyway script id #%s, test #%s executed failed", scriptId, script.testMethod()));
                    }
                } finally {
                    flywayService.saveHistory(scriptId, script, flyway.collection(), stopWatch.elapsed(), isSuccess);
                }
            }
        }
    }

    /**
     * Check test method
     */
    private void checkTestMethod(Object instance, Method testMethod) {
        if (testMethod == null) {
            throw new FlywayExecuteException(String.format("Flyway class %s can't find test method!", instance.getClass().getName()));
        }
        if (!testMethod.getReturnType().equals(Boolean.class)) {
            throw new FlywayExecuteException(String.format("Flyway class %s test method %s only support Boolean return type!", instance.getClass().getName(), testMethod.getName()));
        }
    }

    /**
     * only support method with arguments, MongoDatabase & MongoCollection
     *
     * @param method
     * @param instance
     * @param db
     * @param collection
     * @return
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    private Object invokeMethod(Method method, Object instance, MongoDatabase db, MongoCollection<Document> collection) throws InvocationTargetException, IllegalAccessException {
        logger.info("Invoke class #{}, method #{}", instance.getClass().getName(), method.getName());
        if (method.getParameterTypes().length == 1) {
            Class<?> parameterType = method.getParameterTypes()[0];
            if (MongoDatabase.class.equals(parameterType)) {
                return method.invoke(instance, db);

            } else if (MongoCollection.class.equals(parameterType)) {
                return method.invoke(instance, collection);
            } else {
                throw new FlywayExecuteException(String.format("Flyway class %s don't support method %s!", instance.getClass().getName(), method.getName()));
            }
        } else {
            throw new FlywayExecuteException(String.format("Flyway class %s has wrong arguments!", instance.getClass().getName()));
        }
    }

    private void backupCollection(Flyway flyway, Script script, MongoCollection<Document> collection) {
        String backupCollectionName = String.format("%s_backup_%s_%s", flyway.collection(), script.ticket(), ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        collection.aggregate(List.of(Aggregates.out(backupCollectionName))).toCollection();
    }

    /**
     * Generate script id
     *
     * @param collection collect name
     * @param ticket
     * @param methodName
     * @return
     */
    private String generateId(String collection, String ticket, String methodName) {
        return String.format("%s_%s_%s", collection, ticket, methodName);
    }
}
