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
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * {@link java.lang.annotation.Annotation} to provide multiple
 * {@link QueryParam} specifications.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@Documented
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface QueryParams {
    QueryParam[] value();
}
