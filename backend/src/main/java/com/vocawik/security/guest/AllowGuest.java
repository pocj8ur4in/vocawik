package com.vocawik.security.guest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks for allowing guest authentication when no access token is present. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowGuest {}
