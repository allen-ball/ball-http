/*
 * $Id$
 *
 * Copyright 2017, 2018 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import ball.activation.ByteArrayDataSource;
import ball.http.annotation.DELETE;
import ball.http.annotation.Entity;
import ball.http.annotation.GET;
import ball.http.annotation.HEAD;
import ball.http.annotation.Header;
import ball.http.annotation.Headers;
import ball.http.annotation.HostParam;
import ball.http.annotation.HttpMessageType;
import ball.http.annotation.JAXB;
import ball.http.annotation.JSON;
import ball.http.annotation.OPTIONS;
import ball.http.annotation.PATCH;
import ball.http.annotation.POST;
import ball.http.annotation.PUT;
import ball.http.annotation.PathParam;
import ball.http.annotation.PathParams;
import ball.http.annotation.QueryParam;
import ball.http.annotation.QueryParams;
import ball.http.annotation.URIParam;
import ball.http.annotation.URISpecification;
import ball.io.IOUtil;
import ball.util.ClassOrder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.bind.JAXBException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.message.BasicNameValuePair;

import static ball.util.StringUtil.NIL;
import static ball.util.StringUtil.isNil;
import static java.util.Objects.requireNonNull;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.APPLICATION_XML;

/**
 * <p>
 * {@link HttpRequest} builder for {@link ProtocolClient#protocol()}.  See
 * the {@code apply(Annotation,...)} methods for the supported protocol
 * interface and method parameter {@link Annotation}s.
 * </p>
 * <p>
 * Protocol API authors should consider designing protocol methods to throw
 * {@link org.apache.http.client.HttpResponseException},
 * {@link org.apache.http.client.ClientProtocolException}, and
 * {@link java.io.IOException}.
 * </p>
 * <p>
 * Supported interface and method annotations:
 *
 * {@include #INTERFACE_ANNOTATIONS}
 * </p>
 * <p>
 * Supported method parameter annotations:
 *
 * {@include #PARAMETER_ANNOTATIONS}
 * </p>
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolRequestBuilder {
    private static final String APPLY = "apply";

    /**
     * Supported interface and method annotations.
     */
    public static final Set<Class<? extends Annotation>> INTERFACE_ANNOTATIONS;

    /**
     * Supported method parameter annotations.
     */
    public static final Set<Class<? extends Annotation>> PARAMETER_ANNOTATIONS;

    static {
        TreeSet<Class<? extends Annotation>> interfaceAnnotationTypes =
            new TreeSet<>(ClassOrder.NAME);
        TreeSet<Class<? extends Annotation>> parameterAnnotationTypes =
            new TreeSet<>(ClassOrder.NAME);

        for (Method method :
                 ProtocolRequestBuilder.class.getDeclaredMethods()) {
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

    private final ProtocolClient<?> client;
    private transient URIBuilder uri = null;
    private transient List<NameValuePair> formNVPList = new ArrayList<>();
    private transient Map<String,String> pathMap = new LinkedHashMap<>();
    private transient Map<String,String> queryMap = new LinkedHashMap<>();
    private transient HttpMessage request = null;

    /**
     * Sole constructor.
     *
     * @param   client          The {@link ProtocolClient}.
     */
    protected ProtocolRequestBuilder(ProtocolClient<?> client) {
        this.client = requireNonNull(client, "client");
    }

    /**
     * Build a {@link HttpRequest} ({@link HttpMessage}) from the protocol
     * interface {@link Method}.
     *
     * @param   method          The interface {@link Method}.
     * @param   argv            The caller's arguments.
     *
     * @return  The {@link HttpMessage}.
     *
     * @throws  Throwable       If the call fails for any reason.
     */
    public HttpMessage build(Method method, Object[] argv) throws Throwable {
        HttpMessage message = null;

        if (method.getDeclaringClass().equals(client.protocol())) {
            synchronized (this) {
                uri = URIBuilderFactory.getDefault().getInstance();
                formNVPList.clear();
                pathMap.clear();
                queryMap.clear();
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
                 * Apply form parameters if specified.
                 */
                if (! formNVPList.isEmpty()) {
                    ((HttpEntityEnclosingRequestBase) request)
                        .setEntity(new UrlEncodedFormEntity(formNVPList,
                                                            client.getCharset()));
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

                message = request;
            }
        } else {
            throw new IllegalArgumentException(String.valueOf(method));
        }

        return message;
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

    private void appendURIPath(String string) {
        if (! isNil(string)) {
            String path = uri.getPath();

            if (isNil(path)) {
                path = "/";
            }

            if (! path.endsWith("/")) {
                path += "/";
            }

            uri.setPath(path + string.replaceAll("^[/]+", NIL));
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
     * {@link DELETE} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link DELETE} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(DELETE annotation) throws Throwable {
        apply(annotation.annotationType()
              .getAnnotation(HttpMessageType.class));

        appendURIPath(annotation.value());
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
        apply(annotation.annotationType()
              .getAnnotation(HttpMessageType.class));

        appendURIPath(annotation.value());
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
        apply(annotation.annotationType()
              .getAnnotation(HttpMessageType.class));

        appendURIPath(annotation.value());
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
        apply(annotation.annotationType()
              .getAnnotation(HttpMessageType.class));

        appendURIPath(annotation.value());
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
        apply(annotation.annotationType()
              .getAnnotation(HttpMessageType.class));

        appendURIPath(annotation.value());
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
        apply(annotation.annotationType()
              .getAnnotation(HttpMessageType.class));

        appendURIPath(annotation.value());
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
        apply(annotation.annotationType()
              .getAnnotation(HttpMessageType.class));

        appendURIPath(annotation.value());
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
     * {@link PathParam} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link PathParam} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParam annotation) throws Throwable {
        pathMap.put(annotation.name(), annotation.value());
    }

    /**
     * {@link PathParams} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link PathParams} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParams annotation) throws Throwable {
        if (annotation.value() != null) {
            for (PathParam header : annotation.value()) {
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
                                .withCharset(client.getCharset())
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
     * {@link HostParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link HostParam} {@link Annotation}.
     * @param   argument        The {@link String} representing the host
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(HostParam annotation,
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
     * {@link PathParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link PathParam} {@link Annotation}.
     * @param   argument        The {@link String} representing the path
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParam annotation,
                         String argument) throws Throwable {
        String name = annotation.name();

        if (isNil(name)) {
            name = annotation.value();
        }

        pathMap.put(name, argument);
    }

    /**
     * {@link PathParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link PathParam} {@link Annotation}.
     * @param   argument        The {@link Object} representing the path
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParam annotation,
                         Object argument) throws Throwable {
        apply(annotation, String.valueOf(argument));
    }

    /**
     * {@link QueryParam} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link QueryParam} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParam annotation) throws Throwable {
        queryMap.put(annotation.name(), annotation.value());
    }

    /**
     * {@link QueryParams} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link QueryParams} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParams annotation) throws Throwable {
        if (annotation.value() != null) {
            for (QueryParam parameter : annotation.value()) {
                apply(parameter);
            }
        }
    }

    /**
     * {@link QueryParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link QueryParam} {@link Annotation}.
     * @param   argument        The {@link String} representing the query
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParam annotation,
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
     * {@link QueryParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link QueryParam} {@link Annotation}.
     * @param   argument        The {@link Object} representing the query
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParam annotation,
                         Object argument) throws Throwable {
        if (argument != null) {
            apply(annotation, String.valueOf(argument));
        }
    }

    /**
     * {@link URIParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link URIParam} {@link Annotation}.
     * @param   argument        The request {@link URI}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(URIParam annotation, URI argument) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(argument);
    }

    /**
     * {@link URIParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link URIParam} {@link Annotation}.
     * @param   argument        The request URI as a {@link String}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(URIParam annotation,
                         String argument) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(argument);
    }

    /**
     * {@link javax.ws.rs.DELETE} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.DELETE}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.DELETE annotation) throws Throwable {
        request = new HttpDelete();
    }

    /**
     * {@link javax.ws.rs.GET} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.GET}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.GET annotation) throws Throwable {
        request = new HttpGet();
    }

    /**
     * {@link javax.ws.rs.HEAD} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.HEAD}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.HEAD annotation) throws Throwable {
        request = new HttpHead();
    }

    /**
     * {@link javax.ws.rs.OPTIONS} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.OPTIONS}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.OPTIONS annotation) throws Throwable {
        request = new HttpOptions();
    }

    /**
     * {@link javax.ws.rs.POST} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.POST}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.POST annotation) throws Throwable {
        request = new HttpPost();
    }

    /**
     * {@link javax.ws.rs.PUT} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.PUT}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.PUT annotation) throws Throwable {
        request = new HttpPut();
    }

    /**
     * {@link javax.ws.rs.Encoded} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.Encoded}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.Encoded annotation) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link javax.ws.rs.Path} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.Path}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.Path annotation) throws Throwable {
        appendURIPath(annotation.value());
    }

    /**
     * {@link javax.ws.rs.Consumes} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.Consumes}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.Consumes annotation) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link javax.ws.rs.Produces} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.Produces}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.Produces annotation) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link javax.ws.rs.CookieParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.CookieParam}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the cookie
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.CookieParam annotation,
                         Object argument) throws Throwable {
        if (argument != null) {
            throw new UnsupportedOperationException(annotation.toString());
        }
    }

    /**
     * {@link javax.ws.rs.FormParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.FormParam}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the form
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.FormParam annotation,
                         Object argument) throws Throwable {
        if (argument != null) {
            formNVPList.add(new BasicNameValuePair(annotation.value(),
                                                   String.valueOf(argument)));
        }
    }

    /**
     * {@link javax.ws.rs.HeaderParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.HeaderParam}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the header
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.HeaderParam annotation,
                         Object argument) throws Throwable {
        if (argument != null) {
            request.setHeader(annotation.value(), String.valueOf(argument));
        }
    }

    /**
     * {@link javax.ws.rs.MatrixParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.MatrixParam}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the matrix
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.MatrixParam annotation,
                         Object argument) throws Throwable {
        if (argument != null) {
            throw new UnsupportedOperationException(annotation.toString());
        }
    }

    /**
     * {@link javax.ws.rs.PathParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.PathParam}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the path
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.PathParam annotation,
                         Object argument) throws Throwable {
        if (argument != null) {
            pathMap.put(annotation.value(), String.valueOf(argument));
        } else {
            pathMap.remove(annotation.value());
        }
    }

    /**
     * {@link javax.ws.rs.QueryParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link javax.ws.rs.QueryParam}
     *                          {@link Annotation}.
     * @param   argument        The {@link Object} representing the query
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(javax.ws.rs.QueryParam annotation,
                         Object argument) throws Throwable {
        if (argument != null) {
            queryMap.put(annotation.value(), String.valueOf(argument));
        } else {
            queryMap.remove(annotation.value());
        }
    }

    @Override
    public String toString() { return super.toString(); }

    private abstract class HttpEntityImpl extends AbstractHttpEntity {
        protected final Object object;

        protected HttpEntityImpl(Object object) {
            super();

            this.object = requireNonNull(object, "object");

            setChunked(false);
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
        public boolean isStreaming() { return false; }
    }

    private class JAXBHttpEntity extends HttpEntityImpl {
        public JAXBHttpEntity(Object object) {
            super(object);

            setContentType(ContentType.APPLICATION_XML
                           .withCharset(client.getCharset())
                           .toString());
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            try {
                client.getMarshaller().marshal(object, out);
            } catch (JAXBException exception) {
                throw new IOException(exception);
            }
        }
    }

    private class JSONHttpEntity extends HttpEntityImpl {
        public JSONHttpEntity(Object object) {
            super(object);

            setContentType(ContentType.APPLICATION_JSON
                           .withCharset(client.getCharset())
                           .toString());
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            client.getObjectMapper().writeValue(out, object);
        }
    }
}
