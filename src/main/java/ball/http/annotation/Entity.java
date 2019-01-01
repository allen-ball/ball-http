/*
 * $Id$
 *
 * Copyright 2017 - 2019 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * {@link java.lang.annotation.Annotation} to mark an interface method
 * parameter as an
 * {@link org.apache.http.client.methods.HttpEntityEnclosingRequestBase}
 * {@link org.apache.http.HttpEntity}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@Documented
@Retention(RUNTIME)
@Target({ PARAMETER })
public @interface Entity {
    String value() default EMPTY;
}
