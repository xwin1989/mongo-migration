package com.neal.domain;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * @author Neal
 */
public class FlywayScriptHistory {
    @BsonId
    public String id;

    @BsonProperty("collection")
    public String collection;

    @BsonProperty("ticket")
    public String ticket;

    @BsonProperty("description")
    public String description;

    @BsonProperty("is_success")
    public Boolean isSuccess;

    @BsonProperty("elapsed_time")
    public Long elapsedTime;

    @BsonProperty("created_time")
    public LocalDateTime createdTime;
}
