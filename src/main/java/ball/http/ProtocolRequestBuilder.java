package ball.http;
/*-
 * ##########################################################################
 * Web API Client (HTTP) Utilities
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2016 - 2020 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
import ball.activation.ByteArrayDataSource;
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
import java.util.TreeMap;
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
import javax.ws.rs.core.UriBuilder;
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
 * the {@code type(Annotation,Class)}, {@code method(Annotation,Method)},
 * {@code parameter(Annotation,Parameter,...)},
 * and {@code parameter(Parameter,...)} methods for the supported protocol
 * interface, method, and method parameter {@link Annotation}s and types.
 * </p>
 * <p>
 * Protocol API authors should consider designing protocol methods to throw
 * {@link org.apache.http.client.HttpResponseException},
 * {@link org.apache.http.client.ClientProtocolException}, and
 * {@link java.io.IOException}.
 * </p>
 * <p>
 * Supported type (interface) annotations:
 *
 * {@include #TYPE_ANNOTATIONS}
 * </p>
 * <p>
 * Supported method annotations:
 *
 * {@include #METHOD_ANNOTATIONS}
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
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolRequestBuilder {

    /**
     * Supported type (interface) annotations.
     */
    public static final Set<Class<? extends Annotation>> TYPE_ANNOTATIONS =
        Arrays.stream(ProtocolRequestBuilder.class.getDeclaredMethods())
        .filter(t -> t.getName().equals("type"))
        .filter(t -> t.getParameterCount() > 0)
        .filter(t -> Annotation.class.isAssignableFrom(t.getParameterTypes()[0]))
        .filter(t -> ClassUtils.isAssignable(new Class<?>[] {
                                                 t.getParameterTypes()[0],
                                                 Class.class
                                             },
                                             t.getParameterTypes()))
        .map(t -> t.getParameterTypes()[0].asSubclass(Annotation.class))
        .collect(Collectors.toSet());

    /**
     * Supported method annotations.
     */
    public static final Set<Class<? extends Annotation>> METHOD_ANNOTATIONS =
        Arrays.stream(ProtocolRequestBuilder.class.getDeclaredMethods())
        .filter(t -> t.getName().equals("method"))
        .filter(t -> t.getParameterCount() > 0)
        .filter(t -> Annotation.class.isAssignableFrom(t.getParameterTypes()[0]))
        .filter(t -> ClassUtils.isAssignable(new Class<?>[] {
                                                 t.getParameterTypes()[0],
                                                 Method.class
                                             },
                                             t.getParameterTypes()))
        .map(t -> t.getParameterTypes()[0].asSubclass(Annotation.class))
        .collect(Collectors.toSet());

    /**
     * Supported method parameter annotations.
     */
    public static final Set<Class<? extends Annotation>> PARAMETER_ANNOTATIONS =
        Arrays.stream(ProtocolRequestBuilder.class.getDeclaredMethods())
        .filter(t -> t.getName().equals("parameter"))
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
        .filter(t -> t.getName().equals("parameter"))
        .filter(t -> t.getParameterCount() > 0)
        .filter(t -> ClassUtils.isAssignable(new Class<?>[] { Parameter.class, null },
                                             t.getParameterTypes()))
        .map(t -> t.getParameterTypes()[1])
        .collect(Collectors.toSet());

    private final ProtocolClient<?> client;
    private transient HttpMessage request = null;
    private transient UriBuilder uri = UriBuilder.fromUri(EMPTY);
    private transient TreeMap<String,Object> templateValues = new TreeMap<>();
    private transient Object body = null;

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
        /*
         * Process annotations and arguments
         */
        process(method.getDeclaringClass(), method, argv);
        /*
         * URI
         */
        if (request instanceof HttpRequestBase) {
            ((HttpRequestBase) request)
                .setURI(uri.resolveTemplates(templateValues).build());
        }
        /*
         * Body
         */
        HttpEntity entity = null;

        if (body instanceof HttpEntity) {
            entity = (HttpEntity) body;
        } else if (body instanceof Form) {
            entity =
                new UrlEncodedFormEntity((Form) body, client.getCharset());
        } else if (body != null) {
            entity = new JSONHttpEntity(body);
        }

        if (entity != null) {
            ((HttpEntityEnclosingRequestBase) request).setEntity(entity);
        }

        return request;
    }

    private void process(Class<?> type,
                         Method method, Object... argv) throws Throwable {
        for (Annotation annotation : type.getAnnotations()) {
            try {
                if (TYPE_ANNOTATIONS.contains(annotation.annotationType())) {
                    invoke("type",
                           new Object[] { annotation, type },
                           annotation.annotationType(), Class.class);
                }
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException(String.valueOf(annotation.annotationType()),
                                                exception);
            } catch (IllegalAccessException exception) {
            } catch (InvocationTargetException exception) {
                throw exception.getTargetException();
            }
        }

        for (Annotation annotation : method.getAnnotations()) {
            try {
                if (METHOD_ANNOTATIONS.contains(annotation.annotationType())) {
                    invoke("method",
                           new Object[] { annotation, method },
                           annotation.annotationType(), Method.class);
                }
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException(String.valueOf(annotation.annotationType()),
                                                exception);
            } catch (IllegalAccessException exception) {
            } catch (InvocationTargetException exception) {
                throw exception.getTargetException();
            }
        }

        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i += 1) {
            process(parameters[i], argv[i]);
        }
    }

    private void process(Parameter parameter,
                         Object argument) throws Throwable {
        Annotation[] annotations = parameter.getAnnotations();

        if (annotations.length > 0) {
            for (int i = 0; i < annotations.length; i += 1) {
                process(annotations[i], parameter, argument);
            }
        } else {
            try {
                invoke("parameter",
                       new Object[] { parameter, argument },
                       Parameter.class, parameter.getType());
            } catch (NoSuchMethodException exception) {
            } catch (IllegalAccessException exception) {
            } catch (InvocationTargetException exception) {
                throw exception.getTargetException();
            }
        }
    }

    private void process(Annotation annotation,
                         Parameter parameter,
                         Object argument) throws Throwable {
        try {
            if (PARAMETER_ANNOTATIONS.contains(annotation.annotationType())) {
                invoke("parameter",
                       new Object[] { annotation, parameter, argument },
                       annotation.annotationType(), Parameter.class, parameter.getType());
            }
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(String.valueOf(annotation.annotationType()),
                                            exception);
        } catch (IllegalAccessException exception) {
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    private void invoke(String name, Object[] argv,
                        Class<?>... parameters) throws Throwable {
        MethodUtils.invokeMethod(this, true, name, argv, parameters);
    }

    /**
     * {@link ApplicationPath} type (interface) {@link Annotation}
     *
     * @param   annotation      The {@link ApplicationPath}
     *                          {@link Annotation}.
     * @param   type            The annotated {@link Class}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void type(ApplicationPath annotation,
                        Class<?> type) throws Throwable {
        uri = uri.uri(annotation.value());
    }

    /**
     * {@link ConstrainedTo} type (interface) {@link Annotation}
     *
     * @param   annotation      The {@link ConstrainedTo}
     *                          {@link Annotation}.
     * @param   type            The annotated {@link Class}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void type(ConstrainedTo annotation,
                        Class<?> type) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link Consumes} type (interface) {@link Annotation}
     *
     * @param   annotation      The {@link Consumes} {@link Annotation}.
     * @param   type            The annotated {@link Class}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void type(Consumes annotation, Class<?> type) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link Path} type (interface) {@link Annotation}
     *
     * @param   annotation      The {@link Path} {@link Annotation}.
     * @param   type            The annotated {@link Class}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void type(Path annotation, Class<?> type) throws Throwable {
        uri = uri.path(annotation.value());
    }

    /**
     * {@link Produces} type (interface) {@link Annotation}
     *
     * @param   annotation      The {@link Produces} {@link Annotation}.
     * @param   type            The annotated {@link Class}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void type(Produces annotation, Class<?> type) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link BeanParam} method {@link Annotation}
     *
     * @param   annotation      The {@link BeanParam}
     *                          {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(BeanParam annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link Consumes} method {@link Annotation}
     *
     * @param   annotation      The {@link Consumes} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(Consumes annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link CookieParam} method {@link Annotation}
     *
     * @param   annotation      The {@link CookieParam} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(CookieParam annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link DELETE} method {@link Annotation}
     *
     * @param   annotation      The {@link DELETE} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(DELETE annotation, Method method) throws Throwable {
        request = new HttpDelete();
    }

    /**
     * {@link FormParam} method {@link Annotation}
     *
     * @param   annotation      The {@link FormParam} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(FormParam annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link GET} method {@link Annotation}
     *
     * @param   annotation      The {@link GET} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(GET annotation, Method method) throws Throwable {
        request = new HttpGet();
    }

    /**
     * {@link HEAD} method {@link Annotation}
     *
     * @param   annotation      The {@link HEAD} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(HEAD annotation, Method method) throws Throwable {
        request = new HttpHead();
    }

    /**
     * {@link HeaderParam} method {@link Annotation}
     *
     * @param   annotation      The {@link HeaderParam} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(HeaderParam annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link MatrixParam} method {@link Annotation}
     *
     * @param   annotation      The {@link MatrixParam} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(MatrixParam annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link OPTIONS} method {@link Annotation}
     *
     * @param   annotation      The {@link OPTIONS} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(OPTIONS annotation, Method method) throws Throwable {
        request = new HttpOptions();
    }

    /**
     * {@link PATCH} method {@link Annotation}
     *
     * @param   annotation      The {@link PATCH} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(PATCH annotation, Method method) throws Throwable {
        request = new HttpPatch();
    }

    /**
     * {@link Path} method {@link Annotation}
     *
     * @param   annotation      The {@link Path} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(Path annotation, Method method) throws Throwable {
        uri = uri.path(annotation.value());
    }

    /**
     * {@link PathParam} method {@link Annotation}
     *
     * @param   annotation      The {@link PathParam} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(PathParam annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link QueryParam} method {@link Annotation}
     *
     * @param   annotation      The {@link QueryParam} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(QueryParam annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link POST} method {@link Annotation}
     *
     * @param   annotation      The {@link POST} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(POST annotation, Method method) throws Throwable {
        request = new HttpPost();
    }

    /**
     * {@link Produces} method {@link Annotation}
     *
     * @param   annotation      The {@link Produces} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(Produces annotation,
                          Method method) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
    }

    /**
     * {@link PUT} method {@link Annotation}
     *
     * @param   annotation      The {@link PUT} {@link Annotation}.
     * @param   method          The annotated {@link Method}.
     *
     * @throws  Throwable       If the {@link Annotation} cannot be
     *                          configured.
     */
    protected void method(PUT annotation, Method method) throws Throwable {
        request = new HttpPut();
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
    protected void parameter(BeanParam annotation,
                             Parameter parameter,
                             Object argument) throws Throwable {
        if (argument != null) {
            throw new UnsupportedOperationException(annotation.toString());
        }
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
    protected void parameter(CookieParam annotation,
                             Parameter parameter,
                             Object argument) throws Throwable {
        throw new UnsupportedOperationException(annotation.toString());
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
    protected void parameter(FormParam annotation,
                             Parameter parameter,
                             Object argument) throws Throwable {
        String name =
            isNotBlank(annotation.value())
                ? annotation.value()
                : parameter.getName();

        if (argument != null) {
            if (body == null) {
                body = new Form();
            }

            ((Form) body).add(name, String.valueOf(argument));
        }
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
    protected void parameter(HeaderParam annotation,
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
    protected void parameter(MatrixParam annotation,
                             Parameter parameter,
                             Object argument) throws Throwable {
        String name =
            isNotBlank(annotation.value())
                ? annotation.value()
                : parameter.getName();

        uri = uri.replaceMatrixParam(name, argument);
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
    protected void parameter(PathParam annotation,
                             Parameter parameter,
                             Object argument) throws Throwable {
        String name =
            isNotBlank(annotation.value())
                ? annotation.value()
                : parameter.getName();

        if (argument != null) {
            templateValues.put(name, String.valueOf(argument));
        } else {
            templateValues.remove(name);
        }
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
    protected void parameter(QueryParam annotation,
                             Parameter parameter,
                             Object argument) throws Throwable {
        String name =
            isNotBlank(annotation.value())
                ? annotation.value()
                : parameter.getName();

        uri = uri.replaceQueryParam(name, argument);
    }

    /**
     * {@link HttpMessage} method parameter
     *
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link HttpMessage}.
     *
     * @throws  Throwable       If the argument cannot be configured.
     */
    protected void parameter(Parameter parameter,
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
    protected void parameter(Parameter parameter,
                             HttpEntity argument) throws Throwable {
        body = argument;
    }

    /**
     * {@link URI} method parameter
     *
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link URI}.
     *
     * @throws  Throwable       If the argument cannot be configured.
     */
    protected void parameter(Parameter parameter,
                             URI argument) throws Throwable {
        uri = uri.uri(argument);
    }

    /**
     * {@link Object} method parameter
     *
     * @param   parameter       The {@link Method} {@link Parameter}.
     * @param   argument        The {@link Object}.
     *
     * @throws  Throwable       If the argument cannot be configured.
     */
    protected void parameter(Parameter parameter,
                             Object argument) throws Throwable {
        body = argument;
    }

    @Override
    public String toString() { return super.toString(); }

    private class Form extends ArrayList<NameValuePair> {
        private static final long serialVersionUID = -738222384949508109L;

        public Form() { super(); }

        public boolean add(String name, String value) {
            return add(new BasicNameValuePair(name, value));
        }
    }

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
