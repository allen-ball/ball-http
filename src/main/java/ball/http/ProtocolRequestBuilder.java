/*
 * $Id$
 *
 * Copyright 2017 - 2019 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import ball.activation.ByteArrayDataSource;
import ball.http.annotation.HostParam;
import ball.http.annotation.JSON;
import ball.http.annotation.URIParam;
import ball.http.annotation.URISpecification;
import ball.util.ClassOrder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.BeanParam;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
/* import javax.ws.rs.DefaultValue; */
import javax.ws.rs.DELETE;
/* import javax.ws.rs.Encoded; */
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
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
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
 * <p>
 * Supported method parameter types:
 *
 * {@include #PARAMETER_TYPES}
 * </p>
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolRequestBuilder {
    private static final String APPLY = "apply";

    /**
     * Supported interface and method annotations.
     */
    public static final Set<Class<? extends Annotation>> INTERFACE_ANNOTATIONS =
        Arrays.stream(ProtocolRequestBuilder.class.getDeclaredMethods())
        .filter(t -> t.getName().equals(APPLY))
        .filter(t -> t.getParameterCount() > 0)
        .filter(t -> Annotation.class.isAssignableFrom(t.getParameterTypes()[0]))
        .filter(t -> ClassUtils.isAssignable(new Class<?>[] { t.getParameterTypes()[0] },
                                             t.getParameterTypes()))
        .map(t -> t.getParameterTypes()[0].asSubclass(Annotation.class))
        .collect(Collectors.toSet());

    /**
     * Supported method parameter annotations.
     */
    public static final Set<Class<? extends Annotation>> PARAMETER_ANNOTATIONS =
        Arrays.stream(ProtocolRequestBuilder.class.getDeclaredMethods())
        .filter(t -> t.getName().equals(APPLY))
        .filter(t -> t.getParameterCount() > 0)
        .filter(t -> Annotation.class.isAssignableFrom(t.getParameterTypes()[0]))
        .filter(t -> ClassUtils.isAssignable(new Class<?>[] { t.getParameterTypes()[0], Parameter.class, null },
                                             t.getParameterTypes()))
        .map(t -> t.getParameterTypes()[0].asSubclass(Annotation.class))
        .collect(Collectors.toSet());

    /**
     * Supported method parameter types.
     */
    public static final Set<Class<?>> PARAMETER_TYPES =
        Arrays.stream(ProtocolRequestBuilder.class.getDeclaredMethods())
        .filter(t -> t.getName().equals(APPLY))
        .filter(t -> t.getParameterCount() > 0)
        .filter(t -> ClassUtils.isAssignable(new Class<?>[] { Parameter.class, null },
                                             t.getParameterTypes()))
        .map(t -> t.getParameterTypes()[1])
        .collect(Collectors.toSet());

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

                Parameter[] parameters = method.getParameters();

                for (int i = 0; i < parameters.length; i += 1) {
                    Class<?> type = parameters[i].getType();

                    if (HttpRequest.class.isAssignableFrom(type)) {
                        request = (HttpRequest) argv[i];
                    }

                    process(parameters[i], argv[i]);
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
                MethodUtils.invokeMethod(this, true,
                                         APPLY,
                                         new Object[] { annotation },
                                         new Class<?>[] { annotation.annotationType() });
            }
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    private void process(Parameter parameter,
                         Object argument) throws Throwable {
        try {
            MethodUtils.invokeMethod(this, true,
                                     APPLY,
                                     new Object[] { parameter, argument },
                                     new Class<?>[] { Parameter.class, parameter.getType() });
        } catch (NoSuchMethodException exception) {
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }

        Annotation[] annotations = parameter.getAnnotations();

        for (int i = 0; i < annotations.length; i += 1) {
            process(annotations[i], parameter, argument);
        }
    }

    private void process(Annotation annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        try {
            if (PARAMETER_ANNOTATIONS.contains(annotation.annotationType())) {
                MethodUtils.invokeMethod(this, true,
                                         APPLY,
                                         new Object[] {
                                             annotation,
                                             parameter,
                                             argument
                                         },
                                         new Class<?>[] {
                                             annotation.annotationType(),
                                             Parameter.class,
                                             (argument != null) ? argument.getClass() : parameter.getType()
                                         });
            }
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    private void appendURIPath(String string) {
        if (isNotBlank(string)) {
            String path = uri.getPath();

            if (isBlank(path)) {
                path = "/";
            }

            if (! path.endsWith("/")) {
                path += "/";
            }

            uri.setPath(path + string.replaceAll("^[/]+", EMPTY));
        }
    }

    /**
     * {@link HttpMessage} method parameter
     *
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link HttpMessage}.
     *
     * @throws  Throwable       If the argument cannot be configured.
     */
    protected void apply(Parameter parameter,
                         HttpMessage argument) throws Throwable {
        request = argument;
    }

    /**
     * {@link HttpEntity} method parameter
     *
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link HttpEntity}.
     *
     * @throws  Throwable       If the argument cannot be configured.
     */
    protected void apply(Parameter parameter,
                         HttpEntity argument) throws Throwable {
        ((HttpEntityEnclosingRequestBase) request).setEntity(argument);
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
     * {@link HostParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link HostParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link String} representing the host
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(HostParam annotation,
                         Parameter parameter,
                         String argument) throws Throwable {
        uri.setHost(argument);
    }

    /**
     * {@link JSON} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link JSON} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object} to map.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(JSON annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        ((HttpEntityEnclosingRequestBase) request)
            .setEntity(new JSONHttpEntity(argument));
    }

    /**
     * {@link URIParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link URIParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The request {@link URI}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(URIParam annotation,
                         Parameter parameter,
                         URI argument) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(argument);
    }

    /**
     * {@link URIParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link URIParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The request URI as a {@link String}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(URIParam annotation,
                         Parameter parameter,
                         String argument) throws Throwable {
        uri = URIBuilderFactory.getDefault().getInstance(argument);
    }

    /**
     * {@link ApplicationPath} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link ApplicationPath}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(ApplicationPath annotation) throws Throwable {
        String value = annotation.value();

        if (! value.endsWith("/")) {
            value += "/";
        }

        uri = URIBuilderFactory.getDefault().getInstance(value);
    }

    /**
     * {@link BeanParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link BeanParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object} representing the cookie
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(BeanParam annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        if (argument != null) {
            throw new UnsupportedOperationException(annotation.toString());
        }
    }

    /**
     * {@link ConstrainedTo} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link ConstrainedTo}
     *                          {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(ConstrainedTo annotation) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link CookieParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link CookieParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object} representing the cookie
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(CookieParam annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link Consumes} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link Consumes} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Consumes annotation) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
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
        request = new HttpDelete();
    }

    /**
     * {@link FormParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link FormParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object} representing the form
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(FormParam annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        String name =
            isNotBlank(annotation.value())
                ? annotation.value()
                : parameter.getName();

        if (argument != null) {
            formNVPList.add(new BasicNameValuePair(name,
                                                   String.valueOf(argument)));
        }
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
        request = new HttpGet();
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
        request = new HttpHead();
    }

    /**
     * {@link HeaderParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link HeaderParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object} representing the header
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(HeaderParam annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        String name =
            isNotBlank(annotation.value())
                ? annotation.value()
                : parameter.getName();

        if (argument != null) {
            request.setHeader(name, String.valueOf(argument));
        }
    }

    /**
     * {@link MatrixParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link MatrixParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object} representing the matrix
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(MatrixParam annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
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
        request = new HttpOptions();
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
        request = new HttpPatch();
    }

    /**
     * {@link Path} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link Path} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Path annotation) throws Throwable {
        appendURIPath(annotation.value());
    }

    /**
     * {@link PathParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link PathParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object} representing the path
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(PathParam annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        String name =
            isNotBlank(annotation.value())
                ? annotation.value()
                : parameter.getName();

        if (argument != null) {
            pathMap.put(name, String.valueOf(argument));
        } else {
            pathMap.remove(name);
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
        request = new HttpPost();
    }

    /**
     * {@link Produces} interface/method {@link Annotation}
     *
     * @param   annotation      The {@link Produces} {@link Annotation}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(Produces annotation) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
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
        request = new HttpPut();
    }

    /**
     * {@link QueryParam} method parameter {@link Annotation}
     *
     * @param   annotation      The {@link QueryParam} {@link Annotation}.
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object} representing the query
     *                          parameter value.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void apply(QueryParam annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        String name =
            isNotBlank(annotation.value())
                ? annotation.value()
                : parameter.getName();

        if (argument != null) {
            queryMap.put(name, String.valueOf(argument));
        } else {
            queryMap.remove(name);
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

            try (OutputStream out = ds.getOutputStream()) {
                writeTo(out);
            }

            return ds.getInputStream();
        }

        @Override
        public boolean isStreaming() { return false; }
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
