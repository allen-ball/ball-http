/*
 * $Id$
 *
 * Copyright 2017 - 2019 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.apache.http.client.methods.HttpPut;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * {@link java.lang.annotation.Annotation} to an interface method as an
 * {@code HTTP PUT} message.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@Documented
@Retention(RUNTIME)
@Target({ METHOD })
@HttpMessageType(HttpPut.class)
public @interface PUT {
    String value() default EMPTY;
}
