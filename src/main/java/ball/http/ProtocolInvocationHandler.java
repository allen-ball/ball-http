/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import ball.activation.ByteArrayDataSource;
import ball.http.URIBuilderFactory;
import ball.http.annotation.DELETE;
import ball.http.annotation.Entity;
import ball.http.annotation.GET;
import ball.http.annotation.HEAD;
import ball.http.annotation.Header;
import ball.http.annotation.Headers;
import ball.http.annotation.HostParameter;
import ball.http.annotation.HttpMessageType;
import ball.http.annotation.JAXB;
import ball.http.annotation.JSON;
import ball.http.annotation.OPTIONS;
import ball.http.annotation.PATCH;
import ball.http.annotation.POST;
import ball.http.annotation.PUT;
import ball.http.annotation.PathParameter;
import ball.http.annotation.PathParameters;
import ball.http.annotation.Protocol;
import ball.http.annotation.QueryParameter;
import ball.http.annotation.QueryParameters;
import ball.http.annotation.URIParameter;
import ball.http.annotation.URISpecification;
import ball.io.IOUtil;
import ball.util.ClassOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.apache.http.HttpEntity;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
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
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;

import static ball.util.StringUtil.isNil;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.APPLICATION_XML;

/**
 * Protocol {@link InvocationHandler} for {@link ProtocolClientBuilder}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolInvocationHandler implements InvocationHandler {
    private static final String APPLY = "apply";

    private static final Set<Class<? extends Annotation>> INTERFACE_ANNOTATIONS;
    private static final Set<Class<? extends Annotation>> PARAMETER_ANNOTATIONS;

    static {
        TreeSet<Class<? extends Annotation>> interfaceAnnotationTypes =
            new TreeSet<>(ClassOrder.NAME);
        TreeSet<Class<? extends Annotation>> parameterAnnotationTypes =
            new TreeSet<>(ClassOrder.NAME);

        for (Method method :
                 ProtocolInvocationHandler.class.getDeclaredMethods()) {
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

        INTERFACE_ANNOTATIONS =
            Collections.unmodifiableSet(interfaceAnnotationTypes);
        PARAMETER_ANNOTATIONS =
            Collections.unmodifiableSet(parameterAnnotationTypes);
    }

    private final HttpClient client;
    private final LinkedHashSet<Class<?>> protocols = new LinkedHashSet<>();
    private transient Charset charset = null;
    private transient JAXBContext jaxb = null;
    private transient Marshaller marshaller = null;
    private transient Unmarshaller unmarshaller = null;
    private transient ObjectMapper json = null;
    private transient HttpCoreContext context = new HttpCoreContext();
    private transient URIBuilder uri = null;
    private transient LinkedHashMap<String,String> pathMap = null;
    private transient LinkedHashMap<String,String> queryMap = null;
    private transient HttpMessage request = null;

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
     * Method to get a {@link Charset} for {@code this}
     * {@link ProtocolInvocationHandler}.  See {@link Protocol}.
     *
     * @return  An {@link Charset}.
     */
    public Charset getCharset() {
        synchronized(this) {
            if (charset == null) {
                String name =
                    (String) getProtocolAnnotationValueOf(Protocol.class,
                                                          "charset");

                charset = Charset.forName(name);
            }
        }

        return charset;
    }

    private Object getProtocolAnnotationValueOf(Class<? extends Annotation> type,
                                                String name) {
        Object object = null;

        try {
            Method method = Protocol.class.getMethod(name);

            if (object == null) {
                for (Class<?> protocol : protocols) {
                    Annotation annotation = protocol.getAnnotation(type);

                    if (annotation != null) {
                        object = method.invoke(annotation);

                        if (object != null) {
                            break;
                        }
                    }
                }
            }

            if (object == null) {
                object = method.getDefaultValue();
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        return object;
    }

    /**
     * Method to get an {@link JAXBContext} for {@code this}
     * {@link ProtocolInvocationHandler}.  Will search the protocol
     * interface fields for a configured {@link JAXBContext} and use if
     * found.  Otherwise, this method will return a new
     * {@link JAXBContext}.
     *
     * @return  An {@link JAXBContext}.
     */
    public JAXBContext getJAXBContext() {
        synchronized(this) {
            if (jaxb == null) {
                jaxb = find(JAXBContext.class);
            }

            if (jaxb == null) {
                try {
                    jaxb =
                        JAXBContext
                        .newInstance(protocols.toArray(new Class<?>[] { }));
                } catch (JAXBException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        return jaxb;
    }

    /**
     * Method to get an {@link Marshaller} for {@code this}
     * {@link ProtocolInvocationHandler}.  Will search the protocol
     * interface fields for a configured {@link Marshaller} and use if
     * found.  Otherwise, this method will return a new
     * {@link Marshaller} (see {@link #getJAXBContext()}).
     *
     * @return  An {@link Marshaller}.
     */
    public Marshaller getMarshaller() {
        synchronized(this) {
            if (marshaller == null) {
                marshaller = find(Marshaller.class);
            }

            if (marshaller == null) {
                try {
                    marshaller = getJAXBContext().createMarshaller();
                    marshaller.setProperty("jaxb.encoding",
                                           getCharset().name());
                    marshaller.setProperty("jaxb.formatted.output", true);
                } catch (JAXBException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        return marshaller;
    }

    /**
     * Method to get an {@link Unmarshaller} for {@code this}
     * {@link ProtocolInvocationHandler}.  Will search the protocol
     * interface fields for a configured {@link Unmarshaller} and use if
     * found.  Otherwise, this method will return a new
     * {@link Unmarshaller} (see {@link #getJAXBContext()}).
     *
     * @return  An {@link Unmarshaller}.
     */
    public Unmarshaller getUnmarshaller() {
        synchronized(this) {
            if (unmarshaller == null) {
                unmarshaller = find(Unmarshaller.class);
            }

            if (unmarshaller == null) {
                try {
                    unmarshaller = getJAXBContext().createUnmarshaller();
                } catch (JAXBException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        return unmarshaller;
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
            if (json == null) {
                json = find(ObjectMapper.class);
            }

            if (json == null) {
                json = new ObjectMapper();
            }
        }

        return json;
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
            pathMap = new LinkedHashMap<>();
            queryMap = new LinkedHashMap<>();
            request = null;
            /*
             * Process annotations.
             */
            process(method.getDeclaringClass().getAnnotations());
            process(method.getAnnotations());

            Annotation[][] annotations = method.getParameterAnnotations();
            Class<?>[] types = method.getParameterTypes();

            for (int i = 0; i < annotations.length; i += 1) {
                if (HttpRequest.class.isAssignableFrom(types[i])) {
                    request = (HttpRequest) argv[i];
                }

                process(annotations[i], types[i], argv[i]);
            }
            /*
             * Apply URI path and query parameters and build the URI.
             */
            for (Map.Entry<String,String> entry : pathMap.entrySet()) {
                uri.setPath(uri.getPath()
                            .replaceAll("[{]" + entry.getKey() + "[}]",
                                        entry.getValue()));
            }

            for (Map.Entry<String,String> entry : queryMap.entrySet()) {
                uri.addParameter(entry.getKey(), entry.getValue());
            }

            if (request instanceof HttpRequestBase) {
                ((HttpRequestBase) request).setURI(uri.build());
            }
            /*
             * Execute the request and return the result (unless the
             * protocol specifies that the request should be returned).
             */
            Class<?> returnType = method.getReturnType();

            if (returnType.isAssignableFrom(request.getClass())) {
                result = returnType.cast(request);
            } else {
                if (returnType.isAssignableFrom(HttpResponse.class)) {
                    result =
                        ((HttpClient) proxy)
                        .execute((HttpUriRequest) request, context);
                } else {
                    result =
                        ((HttpClient) proxy)
                        .execute((HttpUriRequest) request,
                                 new ResponseHandlerImpl(returnType), context);
                }
            }
        } else {
            result = method.invoke(client, argv);
        }

        return result;
    }

    private void process(Annotation[] annotations) throws Throwable {
        for (int i = 0; i < annotations.length; i += 1) {
            process(annotations[i]);
        }
    }

    private void process(Annotation annotation) throws Throwable {
        try {
            if (INTERFACE_ANNOTATIONS.contains(annotation.annotationType())) {
                getClass()
                    .getDeclaredMethod(APPLY, annotation.annotationType())
                    .invoke(this, annotation);
            }
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    private void process(Annotation[] annotations,
                         Class<?> type, Object argument) throws Throwable {
        for (int i = 0; i < annotations.length; i += 1) {
            process(annotations[i], type, argument);
        }
    }

    private void process(Annotation annotation,
                         Class<?> type, Object argument) throws Throwable {
        try {
            if (PARAMETER_ANNOTATIONS.contains(annotation.annotationType())) {
                getClass()
                    .getDeclaredMethod(APPLY,
                                       annotation.annotationType(), type)
                    .invoke(this, annotation, argument);
            }
        } catch (NoSuchMethodException exception) {
            if (! Object.class.equals(type)) {
                process(annotation, Object.class, argument);
            } else {
                throw new IllegalStateException(exception);
            }
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    /**
     * {@link URISpecification} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link URISpecification}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(URISpecification annotation) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(annotation);
    }

    /**
     * {@link DELETE} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link DELETE} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(DELETE annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * {@link HttpMessageType} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link HttpMessageType}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(HttpMessageType annotation) throws Throwable {
        request = annotation.value().getConstructor().newInstance();
    }

    /**
     * {@link GET} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link GET} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(GET annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * {@link HEAD} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link HEAD} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(HEAD annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * {@link OPTIONS} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link OPTIONS} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(OPTIONS annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * {@link PATCH} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link PATCH} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PATCH annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * {@link POST} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link POST} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(POST annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * {@link PUT} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link PUT} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PUT annotation) throws Throwable {
        request = annotation.type().getConstructor().newInstance();

        if (! isNil(annotation.value())) {
            uri.setPath(annotation.value());
        }
    }

    /**
     * {@link Header} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link Header} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Header annotation) throws Throwable {
        request.setHeader(annotation.name(), annotation.value());
    }

    /**
     * {@link Headers} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link Headers} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Headers annotation) throws Throwable {
        if (annotation.value() != null) {
            for (Header header : annotation.value()) {
                apply(header);
            }
        }
    }

    /**
     * {@link PathParameter} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link PathParameter}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParameter annotation) throws Throwable {
        pathMap.put(annotation.name(), annotation.value());
    }

    /**
     * {@link PathParameters} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link PathParameters}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParameters annotation) throws Throwable {
        if (annotation.value() != null) {
            for (PathParameter header : annotation.value()) {
                apply(header);
            }
        }
    }

    /**
     * {@link Entity} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link Entity} {@link Annotation}.
     * @param   argument        The {@link HttpEntity}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Entity annotation,
                         HttpEntity argument) throws Throwable {
        if (! isNil(annotation.value())) {
            ((AbstractHttpEntity) argument)
                .setContentType(ContentType
                                .parse(annotation.value())
                                .withCharset(getCharset())
                                .toString());
        }

        ((HttpEntityEnclosingRequestBase) request).setEntity(argument);
    }

    /**
     * {@link Entity} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link Entity} {@link Annotation}.
     * @param   argument        The {@link File} representing the
     *                          {@link HttpEntity}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Entity annotation, File argument) throws Throwable {
        apply(annotation, new FileEntity(argument));
    }

    /**
     * {@link Header} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link Header} {@link Annotation}.
     * @param   argument        The {@link String} representing the header
     *                          value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Header annotation, String argument) throws Throwable {
        String name = annotation.name();

        if (isNil(name)) {
            name = annotation.value();
        }

        request.setHeader(name, argument);
    }

    /**
     * {@link Header} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link Header} {@link Annotation}.
     * @param   argument        The {@link Object} representing the header
     *                          value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Header annotation, Object argument) throws Throwable {
        apply(annotation, String.valueOf(argument));
    }

    /**
     * {@link HostParameter} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link HostParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link String} representing the host
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(HostParameter annotation,
                         String argument) throws Throwable {
        uri.setHost(argument);
    }

    /**
     * {@link JAXB} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link JAXB} {@link Annotation}.
     * @param   argument        The {@link Object} to marshal.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(JAXB annotation, Object argument) throws Throwable {
        ((HttpEntityEnclosingRequestBase) request)
            .setEntity(new JAXBHttpEntity(argument));
    }

    /**
     * {@link JSON} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link JSON} {@link Annotation}.
     * @param   argument        The {@link Object} to map.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(JSON annotation, Object argument) throws Throwable {
        ((HttpEntityEnclosingRequestBase) request)
            .setEntity(new JSONHttpEntity(argument));
    }

    /**
     * {@link PathParameter} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link PathParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link String} representing the path
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParameter annotation,
                         String argument) throws Throwable {
        String name = annotation.name();

        if (isNil(name)) {
            name = annotation.value();
        }

        pathMap.put(name, argument);
    }

    /**
     * {@link PathParameter} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link PathParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the path
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParameter annotation,
                         Object argument) throws Throwable {
        apply(annotation, String.valueOf(argument));
    }

    /**
     * {@link QueryParameter} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link QueryParameter}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParameter annotation) throws Throwable {
        queryMap.put(annotation.name(), annotation.value());
    }

    /**
     * {@link QueryParameters} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link QueryParameters}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParameters annotation) throws Throwable {
        if (annotation.value() != null) {
            for (QueryParameter parameter : annotation.value()) {
                apply(parameter);
            }
        }
    }

    /**
     * {@link QueryParameter} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link QueryParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link String} representing the query
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParameter annotation,
                         String argument) throws Throwable {
        String name = annotation.name();

        if (isNil(name)) {
            name = annotation.value();
        }

        if (argument != null) {
            queryMap.put(name, argument);
        } else {
            queryMap.remove(name);
        }
    }

    /**
     * {@link QueryParameter} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link QueryParameter}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the query
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParameter annotation,
                         Object argument) throws Throwable {
        if (argument != null) {
            apply(annotation, String.valueOf(argument));
        }
    }

    /**
     * {@link URIParameter} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link URIParameter}
     *                          {@link Annotation}.
     * @param   argument        The request {@link URI}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(URIParameter annotation, URI argument) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(argument);
    }

    /**
     * {@link URIParameter} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link URIParameter}
     *                          {@link Annotation}.
     * @param   argument        The request URI as a {@link String}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(URIParameter annotation,
                         String argument) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(argument);
    }

    @Override
    public String toString() { return String.valueOf(client); }

    private class JAXBHttpEntity extends AbstractHttpEntity {
        private final Object object;

        public JAXBHttpEntity(Object object) {
            super();

            setChunked(false);
            setContentType(ContentType.APPLICATION_XML
                           .withCharset(ProtocolInvocationHandler.this
                                        .getCharset())
                           .toString());

            if (object != null) {
                this.object = object;
            } else {
                throw new NullPointerException("object");
            }
        }

        @Override
        public boolean isRepeatable() { return true; }

        @Override
        public long getContentLength() { return -1; }

        @Override
        public InputStream getContent() throws IOException,
                                               IllegalStateException {
            ByteArrayDataSource ds = new ByteArrayDataSource(null, null);
            OutputStream out = null;

            try {
                out = ds.getOutputStream();
                writeTo(out);
            } finally {
                IOUtil.close(out);
            }

            return ds.getInputStream();
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            try {
                ProtocolInvocationHandler.this.getMarshaller()
                    .marshal(object, out);
            } catch (JAXBException exception) {
                throw new IOException(exception);
            }
        }

        @Override
        public boolean isStreaming() { return false; }
    }

    private class JSONHttpEntity extends AbstractHttpEntity {
        private final Object object;

        public JSONHttpEntity(Object object) {
            super();

            setChunked(false);
            setContentType(ContentType.APPLICATION_JSON
                           .withCharset(ProtocolInvocationHandler.this
                                        .getCharset())
                           .toString());

            if (object != null) {
                this.object = object;
            } else {
                throw new NullPointerException("object");
            }
        }

        @Override
        public boolean isRepeatable() { return true; }

        @Override
        public long getContentLength() { return -1; }

        @Override
        public InputStream getContent() throws IOException,
                                               IllegalStateException {
            ByteArrayDataSource ds = new ByteArrayDataSource(null, null);
            OutputStream out = null;

            try {
                out = ds.getOutputStream();
                writeTo(out);
            } finally {
                IOUtil.close(out);
            }

            return ds.getInputStream();
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            ProtocolInvocationHandler.this.
                getObjectMapper().writeValue(out, object);
        }

        @Override
        public boolean isStreaming() { return false; }
    }

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
        public Object handleEntity(HttpEntity entity) throws ClientProtocolException,
                                                             IOException {
            Object object = null;
            ContentType contentType =
                ContentType.getLenientOrDefault(entity);

            if (sameMimeType(APPLICATION_JSON, contentType)) {
                InputStream in = null;

                try {
                    in = entity.getContent();
                    object =
                        ProtocolInvocationHandler.this.getObjectMapper()
                        .readValue(in, type);
                } finally {
                    IOUtil.close(in);
                }
            } else if (sameMimeType(APPLICATION_XML, contentType)) {
                InputStream in = null;

                try {
                    in = entity.getContent();
                    object =
                        ProtocolInvocationHandler.this.getJAXBContext()
                        .createUnmarshaller()
                        .unmarshal(in);
                } catch (JAXBException exception) {
                    throw new IOException(exception);
                } finally {
                    IOUtil.close(in);
                }
            } else {
                object = EntityUtils.toString(entity);
            }

            return object;
        }

        private boolean sameMimeType(ContentType left, ContentType right) {
            return left.getMimeType().equalsIgnoreCase(right.getMimeType());
        }

        @Override
        public String toString() { return super.toString(); }
    }
}
