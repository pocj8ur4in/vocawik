package com.vocawik.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Annotation to apply rate limiting on controller methods. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Maximum number of requests allowed within the time window. */
    int requests() default 10;

    /** Time window in seconds. */
    int seconds() default 60;
}
