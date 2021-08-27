package com.neal.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Neal
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flyway {
    /**
     * Target mongo collection name
     * @return collection name
     */
    String collection();

    /**
     * execute strategy
     */
//    ReadPreference readPreference
}
