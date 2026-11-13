package dev.dryaides.minuit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public type that is minuit's own host or port glue, public only because another minuit package or a
 * loader names it. It is not API: it may change or go in any release, and a mod should not use it.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Internal {
}
