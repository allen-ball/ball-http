/*
 * $Id$
 *
 * Copyright 2017 - 2019 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.apache.commons.lang3.StringUtils.EMPTY;

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
    String name() default EMPTY;
    String value() default EMPTY;
}
