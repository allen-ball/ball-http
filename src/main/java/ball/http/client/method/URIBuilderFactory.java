/*
 * $Id$
 *
 * Copyright 2016, 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.method;

import ball.http.annotation.URIHost;
import ball.http.annotation.URIPath;
import ball.http.annotation.URIPathParameter;
import ball.http.annotation.URIQueryParameter;
import ball.http.annotation.URIScheme;
import ball.http.annotation.URISpecification;
import ball.http.annotation.URIUserInfo;
import ball.util.Factory;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import org.apache.http.client.utils.URIBuilder;

import static ball.annotation.AnnotationUtil.getFieldsAnnotatedWith;
import static ball.annotation.AnnotationUtil.getMethodsAnnotatedWith;
import static ball.util.StringUtil.isNil;

/**
 * {@link URIBuilder} {@link Factory}: Provides means to convert
 * {@link AnnotatedHttpUriRequest} annotations into a configured
 * {@link URIBuilder}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class URIBuilderFactory extends Factory<URIBuilder> {
    private static final long serialVersionUID = -7164981349830843946L;

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
     * Method to get an {@link URIBuilder} configured from
     * {@link AnnotatedHttpUriRequest} annotations.
     *
     * @param   request         The {@link AnnotatedHttpUriRequest}
     *                          instance.
     *
     * @return  The {@link URIBuilder}.
     */
    public URIBuilder getInstance(AnnotatedHttpUriRequest request) {
        URIBuilder builder = null;

        try {
            URISpecification specification =
                request.getClass().getAnnotation(URISpecification.class);

            builder = getInstance(specification);

            Class<? extends AnnotatedHttpUriRequest> type = request.getClass();
            BeanInfo info = Introspector.getBeanInfo(type);

            for (Field field : getFieldsAnnotatedWith(type, URIScheme.class)) {
                URIScheme annotation = field.getAnnotation(URIScheme.class);
                Object value = field.get(request);

                if (value != null) {
                    builder.setScheme(value.toString());
                }
            }

            for (Method method :
                     getMethodsAnnotatedWith(type, URIScheme.class)) {
                URIScheme annotation = method.getAnnotation(URIScheme.class);
                Object value = method.invoke(request);

                if (value != null) {
                    builder.setScheme(value.toString());
                }
            }

            for (Field field :
                     getFieldsAnnotatedWith(type, URIUserInfo.class)) {
                URIUserInfo annotation =
                    field.getAnnotation(URIUserInfo.class);
                Object value = field.get(request);

                if (value != null) {
                    builder.setUserInfo(value.toString());
                }
            }

            for (Method method :
                     getMethodsAnnotatedWith(type, URIUserInfo.class)) {
                URIUserInfo annotation =
                    method.getAnnotation(URIUserInfo.class);
                Object value = method.invoke(request);

                if (value != null) {
                    builder.setUserInfo(value.toString());
                }
            }

            for (Field field : getFieldsAnnotatedWith(type, URIHost.class)) {
                URIHost annotation = field.getAnnotation(URIHost.class);
                Object value = field.get(request);

                if (value != null) {
                    builder.setHost(value.toString());
                }
            }

            for (Method method :
                     getMethodsAnnotatedWith(type, URIHost.class)) {
                URIHost annotation = method.getAnnotation(URIHost.class);
                Object value = method.invoke(request);

                if (value != null) {
                    builder.setHost(value.toString());
                }
            }

            for (Field field : getFieldsAnnotatedWith(type, URIPath.class)) {
                URIPath annotation = field.getAnnotation(URIPath.class);
                Object value = field.get(request);

                if (value != null) {
                    builder.setPath(value.toString());
                }
            }

            for (Method method :
                     getMethodsAnnotatedWith(type, URIPath.class)) {
                URIPath annotation = method.getAnnotation(URIPath.class);
                Object value = method.invoke(request);

                if (value != null) {
                    builder.setPath(value.toString());
                }
            }

            String path = builder.getPath();

            if (! isNil(path)) {
                for (Map.Entry<String,Object> entry :
                         NameValueMapFactory.getDefault()
                         .getInstance(request, URIPathParameter.class)
                         .entrySet()) {
                    path =
                        path.replaceAll("[{]" + entry.getKey() + "[}]",
                                        entry.getValue().toString());
                }

                builder.setPath(path);
            }

            for (Map.Entry<String,Object> entry :
                     NameValueMapFactory.getDefault()
                     .getInstance(request, URIQueryParameter.class)
                     .entrySet()) {
                builder.addParameter(entry.getKey(),
                                     entry.getValue().toString());
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        return builder;
    }

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
