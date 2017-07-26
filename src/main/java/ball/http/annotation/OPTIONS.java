/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.apache.http.client.methods.HttpOptions;

import static ball.util.StringUtil.NIL;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * {@link java.lang.annotation.Annotation} to an interface method as an
 * {@code HTTP OPTIONS} message.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@Documented
@Retention(RUNTIME)
@Target({ METHOD })
public @interface OPTIONS {
    String value() default NIL;
    Class<? extends HttpOptions> type() default HttpOptions.class;
}
