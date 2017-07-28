/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import ball.activation.ByteArrayDataSource;
import ball.http.annotation.DELETE;
import ball.http.annotation.Entity;
import ball.http.annotation.GET;
import ball.http.annotation.HEAD;
import ball.http.annotation.Header;
import ball.http.annotation.HostParameter;
import ball.http.annotation.JSON;
import ball.http.annotation.JSONProperty;
import ball.http.annotation.OPTIONS;
import ball.http.annotation.PATCH;
import ball.http.annotation.POST;
import ball.http.annotation.PUT;
import ball.http.annotation.PathParameter;
import ball.http.annotation.QueryParameter;
import ball.http.annotation.URIParameter;
import ball.http.annotation.URISpecification;
import ball.http.client.URIBuilderFactory;
import ball.http.client.entity.JSONEntity;
import ball.io.IOUtil;
import ball.util.ClassOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.AbstractResponseHandler;

import static ball.util.StringUtil.isNil;

/**
 * Protocol {@link InvocationHandler} for {@link ProtocolClientBuilder}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolInvocationHandler implements InvocationHandler {
    private static final String APPLY = "apply";
    private static final String AS = "as";

    /**
     * {@link Set} of supported protocol interface and method
     * {@link Annotation} types.
     */
    public static final Set<Class<? extends Annotation>> SUPPORTED_INTERFACE_ANNOTATION_TYPES;

    /**
     * {@link Set} of supported protocol method parameter {@link Annotation}
     * types.
     */
    public static final Set<Class<? extends Annotation>> SUPPORTED_PARAMETER_ANNOTATION_TYPES;

    static {
        TreeSet<Class<? extends Annotation>> interfaceAnnotationTypes =
            new TreeSet<>(ClassOrder.NAME);
        TreeSet<Class<? extends Annotation>> parameterAnnotationTypes =
            new TreeSet<>(ClassOrder.NAME);

        for (Method method : ProtocolInvocationHandler.class.getMethods()) {
            String name = method.getName();
            Class<?>[] types = method.getParameterTypes();
            /*
             * apply(Annotation, ...)
             */
            if (name.equals(APPLY)) {
                if (types.length > 0) {
                    if (Annotation.class.isAssignableFrom(types[0])) {
                        Class<? extends Annotation> type =
                            types[0].asSubclass(Annotation.class);

                        switch (types.length) {
                        case 1:
                            interfaceAnnotationTypes.add(type);
                            break;

                        case 2:
                            parameterAnnotationTypes.add(type);
                            break;

                        default:
                            break;
                        }
                    }
                }
            }
        }

        SUPPORTED_INTERFACE_ANNOTATION_TYPES =
            Collections.unmodifiableSet(interfaceAnnotationTypes);
        SUPPORTED_PARAMETER_ANNOTATION_TYPES =
            Collections.unmodifiableSet(parameterAnnotationTypes);
    }

    private final HttpClient client;
    private final LinkedHashSet<Class<?>> protocols = new LinkedHashSet<>();
    private transient ObjectMapper mapper = null;
    private transient URIBuilder uri = null;
    private transient HttpRequest request = null;

    /**
     * Sole constructor.
     *
     * @param   client          The {@link HttpClient}.
     * @param   protocols       Annotated {@link Class}es (interfaces)
     *                          describing the service.
     */
    protected ProtocolInvocationHandler(HttpClient client,
                                        Class<?>... protocols) {
        if (client != null) {
            this.client = client;
        } else {
            throw new NullPointerException("client");
        }

        Collections.addAll(this.protocols, protocols);
    }

    /**
     * Method to get an {@link ObjectMapper} for {@code this}
     * {@link ProtocolInvocationHandler}.  Will search the protocol
     * interface fields for a configured {@link ObjectMapper} and use if
     * found.  Otherwise, this method will return a new
     * {@link ObjectMapper}.
     *
     * @return  An {@link ObjectMapper}.
     */
    public ObjectMapper getObjectMapper() {
        synchronized(this) {
            if (mapper == null) {
                mapper = find(ObjectMapper.class);
            }

            if (mapper == null) {
                mapper = new ObjectMapper();
            }
        }

        return mapper;
    }

    private <T> T find(Class<T> type) {
        T value = null;

        for (Class<?> protocol : protocols) {
            for (Field field : protocol.getFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    try {
                        value = type.cast(field.get(null));

                        if (value != null) {
                            break;
                        }
                    } catch (IllegalAccessException exception) {
                        continue;
                    }
                }
            }

            if (value != null) {
                break;
            }
        }

        return value;
    }

    @Override
    public Object invoke(Object proxy,
                         Method method, Object[] argv) throws Throwable {
        Object result = null;

        if (protocols.contains(method.getDeclaringClass())) {
            uri = URIBuilderFactory.getDefault().getInstance();
            request = null;

            apply(method.getDeclaringClass().getAnnotations());
            apply(method.getAnnotations());

            Annotation[][] annotations = method.getParameterAnnotations();
            Class<?>[] types = method.getParameterTypes();

            for (int i = 0; i < annotations.length; i += 1) {
                if (HttpRequest.class.isAssignableFrom(types[i])) {
                    request = (HttpRequest) argv[i];
                }

                apply(annotations[i], types[i], argv[i]);
            }

            if (request instanceof HttpRequestBase) {
                ((HttpRequestBase) request).setURI(uri.build());
            }

            if (request != null) {
                Class<?> returnType = method.getReturnType();

                if (! returnType.isAssignableFrom(request.getClass())) {
                    result =
                        ((HttpClient) proxy)
                        .execute((HttpUriRequest) request,
                                 new ResponseHandlerImpl(returnType));
                } else {
                    result = returnType.cast(request);
                }
            } else {
                result = null;
            }
        } else {
            result = method.invoke(client, argv);
        }

        return result;
    }

    private void apply(Annotation[] annotations) throws Throwable {
        for (int i = 0; i < annotations.length; i += 1) {
            apply(annotations[i]);
        }
    }

    private void apply(Annotation annotation) throws Throwable {
        try {
            if (SUPPORTED_INTERFACE_ANNOTATION_TYPES.contains(annotation.annotationType())) {
                getClass()
                    .getMethod(APPLY, annotation.annotationType())
                    .invoke(this, annotation);
            }
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    private void apply(Annotation[] annotations,
                       Class<?> type, Object argument) throws Throwable {
        for (int i = 0; i < annotations.length; i += 1) {
            apply(annotations[i], type, argument);
        }
    }

    private void apply(Annotation annotation,
                       Class<?> type, Object argument) throws Throwable {
        try {
            if (SUPPORTED_PARAMETER_ANNOTATION_TYPES.contains(annotation.annotationType())) {
                getClass()
                    .getMethod(APPLY, annotation.annotationType(), type)
                    .invoke(this, annotation, argument);
            }
        } catch (NoSuchMethodException exception) {
            if (! Object.class.equals(type)) {
                apply(annotation, Object.class, argument);
            } else {
                throw new IllegalStateException(exception);
            }
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    /**
     * Method to process an {@link URISpecification} {@link Annotation}.
     *
     * @param   annotation      The {@link URISpecification}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(URISpecification annotation) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(annotation);
    }

    /**
     * Method to process a {@link DELETE} {@link Annotation}.
     *
     * @param   annotation      The {@link DELETE} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(DELETE annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link GET} {@link Annotation}.
     *
     * @param   annotation      The {@link GET} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(GET annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link HEAD} {@link Annotation}.
     *
     * @param   annotation      The {@link HEAD} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(HEAD annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link OPTIONS} {@link Annotation}.
     *
     * @param   annotation      The {@link OPTIONS} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(OPTIONS annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link PATCH} {@link Annotation}.
     *
     * @param   annotation      The {@link PATCH} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(PATCH annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link POST} {@link Annotation}.
     *
     * @param   annotation      The {@link POST} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(POST annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link PUT} {@link Annotation}.
     *
     * @param   annotation      The {@link PUT} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(PUT annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link Entity} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link Entity} {@link Annotation}.
     * @param   argument        The {@link HttpEntity}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(Entity annotation,
                      HttpEntity argument) throws Throwable {
        if (! isNil(annotation.value())) {
            ((AbstractHttpEntity) argument)
                .setContentType(ContentType.parse(annotation.value())
                                .toString());
        }

        ((HttpEntityEnclosingRequestBase) request).setEntity(argument);
    }

    /**
     * Method to process a {@link Entity} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link Entity} {@link Annotation}.
     * @param   argument        The {@link File} representing the
     *                          {@link HttpEntity}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(Entity annotation, File argument) throws Throwable {
        apply(annotation, new FileEntity(argument));
    }

    /**
     * Method to process a {@link Header} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link Header} {@link Annotation}.
     * @param   argument        The {@link String} representing the header
     *                          value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(Header annotation, String argument) throws Throwable {
        request.setHeader(annotation.value(), argument);
    }

    /**
     * Method to process a {@link Header} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link Header} {@link Annotation}.
     * @param   argument        The {@link Object} representing the header
     *                          value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(Header annotation, Object argument) throws Throwable {
        apply(annotation, String.valueOf(argument));
    }

    /**
     * Method to process a {@link HostParameter} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link HostParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link String} representing the host
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(HostParameter annotation,
                      String argument) throws Throwable {
        uri.setHost(argument);
    }

    /**
     * Method to process a {@link JSON} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link JSON} {@link Annotation}.
     * @param   argument        The {@link JSON} value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(JSON annotation, Object argument) throws Throwable {
        ((HttpEntityEnclosingRequestBase) request)
            .setEntity(new JSONEntity(getObjectMapper(), argument));
    }

    /**
     * Method to process a {@link JSONProperty} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link JSONProperty} {@link Annotation}.
     * @param   argument        The {@link JSONProperty} value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(JSONProperty annotation,
                      Object argument) throws Throwable {
        JSONEntity entity =
            (JSONEntity)
            ((HttpEntityEnclosingRequestBase) request).getEntity();

        if (entity == null) {
            entity =
                new JSONEntity(getObjectMapper(),
                               getObjectMapper().createObjectNode());
            ((HttpEntityEnclosingRequestBase) request).setEntity(entity);
        }

        ObjectNode node = (ObjectNode) entity.getObject();

        if (argument == null) {
            node.putNull(annotation.value());
        } else if (argument instanceof BigDecimal) {
            node.put(annotation.value(), (BigDecimal) argument);
        } else if (argument instanceof Boolean) {
            node.put(annotation.value(), (Boolean) argument);
        } else if (argument instanceof byte[]) {
            node.put(annotation.value(), (byte[]) argument);
        } else if (argument instanceof Double) {
            node.put(annotation.value(), (Double) argument);
        } else if (argument instanceof Float) {
            node.put(annotation.value(), (Float) argument);
        } else if (argument instanceof Integer) {
            node.put(annotation.value(), (Integer) argument);
        } else if (argument instanceof Long) {
            node.put(annotation.value(), (Long) argument);
        } else if (argument instanceof Short) {
            node.put(annotation.value(), (Short) argument);
        } else if (argument instanceof String) {
            node.put(annotation.value(), (String) argument);
        } else {
            node.putPOJO(annotation.value(), argument);
        }
    }

    /**
     * Method to process a {@link PathParameter} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link PathParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link String} representing the path
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(PathParameter annotation,
                      String argument) throws Throwable {
        String path = uri.getPath();

        if (! isNil(path)) {
            uri.setPath(path.replaceAll("[{]" + annotation.value() + "[}]",
                                        argument));
        }
    }

    /**
     * Method to process a {@link PathParameter} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link PathParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the path
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(PathParameter annotation,
                      Object argument) throws Throwable {
        apply(annotation, String.valueOf(argument));
    }

    /**
     * Method to process a {@link QueryParameter} parameter
     * {@link Annotation}.
     *
     * @param   annotation      The {@link QueryParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link String} representing the query
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(QueryParameter annotation,
                      String argument) throws Throwable {
        if (argument != null) {
            uri.addParameter(annotation.value(), argument);
        }
    }

    /**
     * Method to process a {@link QueryParameter} parameter
     * {@link Annotation}.
     *
     * @param   annotation      The {@link QueryParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the query
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(QueryParameter annotation,
                      Object argument) throws Throwable {
        if (argument != null) {
            apply(annotation, String.valueOf(argument));
        }
    }

    /**
     * Method to process a {@link URIParameter} parameter
     * {@link Annotation}.
     *
     * @param   annotation      The {@link URIParameter}
     *                          {@link Annotation}.
     * @param   argument        The request {@link URI}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(URIParameter annotation, URI argument) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(argument);
    }

    /**
     * Method to process a {@link URIParameter} parameter
     * {@link Annotation}.
     *
     * @param   annotation      The {@link URIParameter}
     *                          {@link Annotation}.
     * @param   argument        The request URI as a {@link String}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    public void apply(URIParameter annotation,
                      String argument) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(argument);
    }

    @Override
    public String toString() { return String.valueOf(client); }

    private class ResponseHandlerImpl extends AbstractResponseHandler<Object> {
        private final Class<?> type;

        public ResponseHandlerImpl(Class<?> type) {
            super();

            if (type != null) {
                this.type = type;
            } else {
                throw new NullPointerException("type");
            }
        }

        @Override
        public Object handleResponse(HttpResponse response) throws HttpResponseException,
                                                                   IOException {
            Object object = null;

            if (HttpResponse.class.isAssignableFrom(type)) {
                object = response;
            } else if (HttpEntity.class.isAssignableFrom(type)) {
                object = response.getEntity();
            } else {
                object = super.handleResponse(response);
            }

            return object;
        }

        @Override
        public Object handleEntity(HttpEntity entity) throws IOException {
            Object object = null;

            if (entity != null) {
                InputStream in = null;

                try {
                    in = entity.getContent();
                    object =
                        ProtocolInvocationHandler.this.getObjectMapper()
                        .readValue(in, type);
                } finally {
                    IOUtil.close(in);
                }
            }

            return object;
        }

        @Override
        public String toString() { return String.valueOf(client); }
    }
}
