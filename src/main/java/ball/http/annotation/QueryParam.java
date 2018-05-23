/*
 * $Id$
 *
 * Copyright 2017, 2018 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static ball.util.StringUtil.NIL;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * {@link java.lang.annotation.Annotation} to specify a "static" query
 * parameter on an interface or method or to specify an interface method
 * parameter as an URI query parameter.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@Documented
@Retention(RUNTIME)
@Target({ TYPE, METHOD, PARAMETER })
public @interface QueryParam {
    String name() default NIL;
    String value() default NIL;
}
