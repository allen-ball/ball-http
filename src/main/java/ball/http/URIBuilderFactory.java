/*
 * $Id$
 *
 * Copyright 2016, 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import ball.http.annotation.URISpecification;
import ball.util.Factory;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import org.apache.http.client.utils.URIBuilder;

import static ball.util.StringUtil.isNil;

/**
 * {@link URIBuilder} {@link Factory}: Provides means to convert
 * {@link URISpecification} annotations into a configured
 * {@link URIBuilder}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class URIBuilderFactory extends Factory<URIBuilder> {
    private static final long serialVersionUID = -191033425683772932L;

    private static final URIBuilderFactory DEFAULT = new URIBuilderFactory();

    /**
     * {@link URIBuilderFactory} factory method.
     *
     * @return  The default {@link URIBuilderFactory}.
     */
    public static URIBuilderFactory getDefault() { return DEFAULT; }

    /**
     * Sole constructor.
     *
     * @see #getDefault()
     */
    protected URIBuilderFactory() { super(URIBuilder.class); }

    /**
     * Method to get an {@link URIBuilder} configured from an
     * {@link URISpecification} annotation.
     *
     * @param   specification   The {@link URISpecification} (may be
     *                          {@code null}).
     *
     * @return  The {@link URIBuilder}.
     */
    public URIBuilder getInstance(URISpecification specification) {
        URIBuilder builder = null;

        try {
            if (specification != null && (! isNil(specification.value()))) {
                builder = new URIBuilder(specification.value());
            } else {
                builder = new URIBuilder();
            }

            if (specification != null) {
                if (! isNil(specification.charset())) {
                    builder.setCharset(Charset.forName(specification.charset()));
                }

                if (! isNil(specification.scheme())) {
                    builder.setScheme(specification.scheme());
                }

                if (! isNil(specification.userInfo())) {
                    builder.setUserInfo(specification.userInfo());
                }

                if (! isNil(specification.host())) {
                    builder.setHost(specification.host());
                }

                if (specification.port() > 0) {
                    builder.setPort(specification.port());
                }

                if (! isNil(specification.path())) {
                    builder.setPath(specification.path());
                }
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        return builder;
    }

    /**
     * Method to get an {@link URIBuilder} configured from an {@link URI}.
     *
     * @param   uri             The {@link URI}.
     *
     * @return  The {@link URIBuilder}.
     */
    public URIBuilder getInstance(URI uri) {
        URIBuilder builder = null;

        try {
            builder = new URIBuilder(uri);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        return builder;
    }

    /**
     * Method to get an {@link URIBuilder} configured from a {@link String}.
     *
     * @param   string          The URI {@link String}.
     *
     * @return  The {@link URIBuilder}.
     */
    public URIBuilder getInstance(String string) {
        URIBuilder builder = null;

        try {
            builder = getInstance(new URI(string));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        return builder;
    }
}
