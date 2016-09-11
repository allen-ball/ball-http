/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.method;

import ball.http.annotation.URIQueryParameter;
import ball.util.Factory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static ball.annotation.AnnotationUtil.getFieldsAnnotatedWith;
import static ball.annotation.AnnotationUtil.getMethodsAnnotatedWith;
import static ball.util.BeanUtil.getPropertyName;
import static ball.util.StringUtil.isNil;

/**
 * {@link Map} {@link Factory}: Provides means to convert
 * {@link AnnotatedHttpUriRequest} annotations into a name/value pair
 * {@link Map}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class NameValueMapFactory extends Factory<NameValueMapFactory.Impl> {
    private static final long serialVersionUID = -8131718406176157358L;

    private static final NameValueMapFactory DEFAULT =
        new NameValueMapFactory();

    /**
     * {@link NameValueMapFactory} factory method.
     *
     * @return  The default {@link NameValueMapFactory}.
     */
    public static NameValueMapFactory getDefault() { return DEFAULT; }

    /**
     * Sole constructor.
     *
     * @see #getDefault()
     */
    protected NameValueMapFactory() { super(NameValueMapFactory.Impl.class); }

    /**
     * Method to get a name/bvalue {@link Map} configured from
     * {@link AnnotatedHttpUriRequest} annotations.
     *
     * @param   request         The {@link AnnotatedHttpUriRequest}
     *                          instance.
     * @param   annotation      The {@link Annotation} {@link Class}.
     *
     * @return  The {@link Map}.
     */
    public Map<String,Object> getInstance(AnnotatedHttpUriRequest request,
                                          Class<? extends Annotation> annotation) {
        Impl map = new Impl();

        try {
            Class<? extends AnnotatedHttpUriRequest> type = request.getClass();

            for (Field field :
                     getFieldsAnnotatedWith(type, URIQueryParameter.class)) {
                Object value = field.get(request);

                if (value != null) {
                    map.put(field.getName(), value);
                }
            }

            for (Method method : getMethodsAnnotatedWith(type, annotation)) {
                Object value = method.invoke(request);

                if (value != null) {
                    map.put(getName(method, method.getAnnotation(annotation)),
                            value);
                }
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        return map;
    }

    private String getName(Method method, Annotation annotation) {
        String name = null;

        try {
            Class<? extends Annotation> type = annotation.annotationType();
            Object value =
                type.getMethod("name").invoke(annotation, (Object[]) null);

            name = (value != null) ? value.toString() : null;
        } catch (Exception exception) {
        }

        if (isNil(name)) {
            name = getPropertyName(method);

            if (name == null) {
                throw new IllegalStateException("Could not get property name for "
                                                + method);
            }
        }

        return name;
    }

    /**
     * {@link Factory} {@link Map} implementation.
     */
    protected static class Impl extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID = -7662104082865571424L;

        /**
         * Sole constructor.
         */
        protected Impl() { super(); }
    }
}
