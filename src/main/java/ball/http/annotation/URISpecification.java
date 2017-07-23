/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static ball.util.StringUtil.NIL;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
    String value() default NIL;
    String charset() default "UTF-8";
    String scheme() default NIL;
    String userInfo() default NIL;
    String host() default NIL;
    int port() default 0;
    String path() default NIL;
}
