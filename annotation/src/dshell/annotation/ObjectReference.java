package dshell.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * marker annotation for ASTDumper.
 * exclude annotated field from json string
 * @author skgchxngsxyz-opensuse
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ObjectReference {
}
