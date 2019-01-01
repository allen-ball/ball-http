/*
 * $Id$
 *
 * Copyright 2016 - 2019 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * {@link java.lang.annotation.Annotation} to provide an
 * {@link java.net.URI} specification.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@Documented
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface URISpecification {
    String value() default EMPTY;
    String charset() default "UTF-8";
    String scheme() default EMPTY;
    String userInfo() default EMPTY;
    String host() default EMPTY;
    int port() default 0;
    String path() default EMPTY;
}
