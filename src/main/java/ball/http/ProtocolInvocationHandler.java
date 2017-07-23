/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import ball.http.annotation.DELETE;
import ball.http.annotation.Entity;
import ball.http.annotation.GET;
import ball.http.annotation.HEAD;
import ball.http.annotation.Header;
import ball.http.annotation.OPTIONS;
import ball.http.annotation.PATCH;
import ball.http.annotation.POST;
import ball.http.annotation.PUT;
import ball.http.annotation.PathParameter;
import ball.http.annotation.QueryParameter;
import ball.http.annotation.URISpecification;
import ball.http.client.entity.JSONEntity;
import ball.http.client.method.URIBuilderFactory;
import ball.util.ClassOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;

import static ball.util.StringUtil.isNil;

/**
 * Protocol {@link InvocationHandler} for {@link ProtocolClientBuilder}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolInvocationHandler implements InvocationHandler {
    private static final ObjectMapper MAPPER =
        new ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .setDateFormat(new ISO8601DateFormat());

    private static final String APPLY = "apply";
    private static final String AS = "as";

    /**
     * {@link Set} of supported protocol method {@link Annotation} types.
     */
    public static final Set<Class<? extends Annotation>> SUPPORTED_ANNOTATION_TYPES;

    /**
     * {@link Set} of supported protocol method return types.
     */
    public static final Set<Class<?>> SUPPORTED_RETURN_TYPES;

    static {
        TreeSet<Class<? extends Annotation>> annotations =
            new TreeSet<>(ClassOrder.NAME);
        TreeSet<Class<?>> returnTypes = new TreeSet<>(ClassOrder.NAME);
        Class<?>[] AS_PARAMETERS = new Class<?>[] { HttpResponse.class };

        for (Method method : ProtocolInvocationHandler.class.getMethods()) {
            String name = method.getName();
            Class<?>[] types = method.getParameterTypes();
            /*
             * apply(Annotation, ...)
             */
            if (name.equals(APPLY)) {
                if (types.length > 0 && Annotation.class.isAssignableFrom(types[0])) {
                    annotations.add(types[0].asSubclass(Annotation.class));
                }
            }
            /*
             * asClassSimpleName(HttpResponse)
             */
            if (Arrays.equals(types, AS_PARAMETERS)) {
                Class<?> type = method.getReturnType();

                if (name.equals(AS + type.getSimpleName())) {
                    returnTypes.add(type);
                }
            }
        }

        SUPPORTED_ANNOTATION_TYPES = Collections.unmodifiableSet(annotations);
        SUPPORTED_RETURN_TYPES = Collections.unmodifiableSet(returnTypes);
    }

    private final HttpClient client;
    private final LinkedHashSet<Class<?>> protocols = new LinkedHashSet<>();
    private transient URIBuilder builder = null;
    private transient HttpUriRequest request = null;

    /**
     * Sole constructor.
     *
     * @param   client          The {@link HttpClient}.
     * @param   protocols       Annotated {@link Class}es (interfaces)
     *                          describing the service.
     */
    public ProtocolInvocationHandler(HttpClient client,
                                     Class<?>... protocols) {
        if (client != null) {
            this.client = client;
        } else {
            throw new NullPointerException("client");
        }

        Collections.addAll(this.protocols, protocols);
    }

    /**
     * Method to process an {@link URISpecification} {@link Annotation}.
     *
     * @param   annotation      The {@link URISpecification}
     *                          {@link Annotation}.
     */
    public void apply(URISpecification annotation) {
        builder = URIBuilderFactory.getDefault().getInstance(annotation);
    }

    /**
     * Method to process a {@link DELETE} {@link Annotation}.
     *
     * @param   annotation      The {@link DELETE} {@link Annotation}.
     */
    public void apply(DELETE annotation) {
        request = new HttpDelete();

        if (! isNil(annotation.value())) {
            builder.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link GET} {@link Annotation}.
     *
     * @param   annotation      The {@link GET} {@link Annotation}.
     */
    public void apply(GET annotation) {
        request = new HttpGet();

        if (! isNil(annotation.value())) {
            builder.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link HEAD} {@link Annotation}.
     *
     * @param   annotation      The {@link HEAD} {@link Annotation}.
     */
    public void apply(HEAD annotation) {
        request = new HttpHead();

        if (! isNil(annotation.value())) {
            builder.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link OPTIONS} {@link Annotation}.
     *
     * @param   annotation      The {@link OPTIONS} {@link Annotation}.
     */
    public void apply(OPTIONS annotation) {
        request = new HttpOptions();

        if (! isNil(annotation.value())) {
            builder.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link PATCH} {@link Annotation}.
     *
     * @param   annotation      The {@link PATCH} {@link Annotation}.
     */
    public void apply(PATCH annotation) {
        request = new HttpPatch();

        if (! isNil(annotation.value())) {
            builder.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link POST} {@link Annotation}.
     *
     * @param   annotation      The {@link POST} {@link Annotation}.
     */
    public void apply(POST annotation) {
        request = new HttpPost();

        if (! isNil(annotation.value())) {
            builder.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link PUT} {@link Annotation}.
     *
     * @param   annotation      The {@link PUT} {@link Annotation}.
     */
    public void apply(PUT annotation) {
        request = new HttpPut();

        if (! isNil(annotation.value())) {
            builder.setPath(annotation.value());
        }
    }

    /**
     * Method to process a {@link Entity} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link Entity} {@link Annotation}.
     * @param   argument        The {@link File} reepresenting the
     *                          {@link HttpEntity}.
     */
    public void apply(Entity annotation, File argument) {
        FileEntity entity = new FileEntity(argument);

        if (! isNil(annotation.value())) {
            entity.setContentType(ContentType.parse(annotation.value())
                                  .toString());
        }

        ((HttpEntityEnclosingRequestBase) request).setEntity(entity);
    }

    /**
     * Method to process a {@link Header} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link Header} {@link Annotation}.
     * @param   argument        The {@link String} reepresenting the header
     *                          value.
     */
    public void apply(Header annotation, String argument) {
        request.setHeader(annotation.value(), argument);
    }

    /**
     * Method to process a {@link JsonProperty} parameter {@link Annotation}.
     *
     * @param   annotation      The {@link JsonProperty} {@link Annotation}.
     * @param   argument        The {@link JsonProperty} value.
     */
    public void apply(JsonProperty annotation, Object argument) {
        JSONEntity entity =
            (JSONEntity)
            ((HttpEntityEnclosingRequestBase) request)
            .getEntity();

        if (entity == null) {
            entity = new JSONEntity(MAPPER, MAPPER.createObjectNode());
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
     * @param   argument        The {@link String} reepresenting the path
     *                          parameter value.
     */
    public void apply(PathParameter annotation, String argument) {
        String path = builder.getPath();

        if (! isNil(path)) {
            builder.setPath(path.replaceAll("[{]" + annotation.value() + "[}]",
                                            argument));
        }
    }

    /**
     * Method to process a {@link QueryParameter} parameter
     * {@link Annotation}.
     *
     * @param   annotation      The {@link QueryParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link String} reepresenting the query
     *                          parameter value.
     */
    public void apply(QueryParameter annotation, String argument) {
        builder.addParameter(annotation.value(), argument);
    }

    /**
     * (Trivial) method to convert a {@link HttpResponse} to a
     * {@link HttpResponse}.
     *
     * @param   response        The {@link HttpResponse}.
     *
     * @return  The {@link HttpResponse}.
     */
    public HttpResponse asHttpResponse(HttpResponse response) {
        return response;
    }

    /**
     * Method to convert a {@link HttpResponse} to a {@link HttpEntity}.
     *
     * @param   response        The {@link HttpResponse}.
     *
     * @return  The {@link HttpEntity} (may be {@code null}).
     *
     * @throws  Exception       If the underlying conversion fails for any
     *                          reason.
     */
    public HttpEntity asHttpEntity(HttpResponse response) throws Exception {
        return (response != null) ? response.getEntity() : null;
    }

    /**
     * Method to convert a {@link HttpResponse} to a {@link JsonNode}.
     *
     * @param   response        The {@link HttpResponse}.
     *
     * @return  The {@link JsonNode} (may be {@code null}).
     *
     * @throws  Exception       If the underlying conversion fails for any
     *                          reason.
     */
    public JsonNode asJsonNode(HttpResponse response) throws Exception {
        HttpEntity entity = asHttpEntity(response);

        return (entity != null) ? MAPPER.readTree(entity.getContent()) : null;
    }

    @Override
    public Object invoke(Object proxy,
                         Method method, Object[] arguments) throws Throwable {
        Object result = null;

        if (protocols.contains(method.getDeclaringClass())) {
            builder = URIBuilderFactory.getDefault().getInstance();
            request = null;

            apply(method.getDeclaringClass().getAnnotations());
            apply(method.getAnnotations());
            apply(method.getParameterAnnotations(),
                  method.getParameterTypes(), arguments);

            ((HttpRequestBase) request).setURI(builder.build());

            result =
                as(method.getReturnType(),
                   ((HttpClient) proxy).execute(request));
        } else {
            result = method.invoke(client, arguments);
        }

        return result;
    }

    private void apply(Annotation[] annotations) {
        for (int i = 0; i < annotations.length; i += 1) {
            apply(annotations[i]);
        }
    }

    private void apply(Annotation annotation) {
        apply(getApplyMethod(annotation.annotationType()), annotation);
    }

    private void apply(Annotation[][] annotations,
                       Class<?>[] types, Object[] arguments) throws Throwable {
        for (int i = 0; i < annotations.length; i += 1) {
            apply(annotations[i], types[i], arguments[i]);
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
        apply(getApplyMethod(annotation.annotationType(), type),
              annotation, argument);
    }

    private void apply(Method method, Annotation annotation) {
        try {
            if (method != null) {
                method.invoke(this, annotation);
            }
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
        }
    }

    private void apply(Method method,
                       Annotation annotation,
                       Object argument) throws Throwable {
        try {
            if (method != null) {
                method.invoke(this, annotation, argument);
            }
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    private Method getApplyMethod(Class<?>... types) {
        return getMethod(APPLY, types);
    }

    private Object as(Class<?> type, HttpResponse response) throws Throwable {
        Object object = null;

        try {
            Method method =
                getMethod(AS + type.getSimpleName(), HttpResponse.class);

            if (method != null) {
                object = method.invoke(response);
            }
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }

        return (object != null) ? type.cast(object) : response;
    }

    private Method getMethod(String name, Class<?>... types) {
        Method method = null;

        try {
            method = getClass().getMethod(name, types);
        } catch (NoSuchMethodException exception) {
            method = null;
        }

        return method;
    }

    @Override
    public String toString() { return String.valueOf(client); }
}
