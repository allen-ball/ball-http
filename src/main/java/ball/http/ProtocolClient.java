package ball.http;
/*-
 * ##########################################################################
 * Web API Client (HTTP) Utilities
 * %%
 * Copyright (C) 2016 - 2022 Allen D. Ball
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Proxy;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import lombok.ToString;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Abstract {@link ProtocolClient} base class.
 *
 * @param       <P>             The protocol type erasure.
 * <p>
 * This class provides:
 * <ol>
 *   <li value="1">
 *     {@link org.apache.hc.client5.http.classic.HttpClient}
 *     ({@link #client()})
 *   </li>
 *   <li value="2">
 *     {@link HttpContext} ({@link #context()})
 *   </li>
 *   <li value="3">
 *     A {@link Proxy} which implements the annotated protocol interface
 *   </li>
 *   <li value="4">
 *     {@link.this} implements {@link HttpRequestInterceptor}
 *     and {@link HttpResponseInterceptor} which are configured into
 *     {@link HttpClientBuilder}; subclasses can override
 *     {@link #process(HttpRequest,EntityDetails,HttpContext)} and
 *     {@link #process(HttpResponse,EntityDetails,HttpContext)}
 *   </li>
 * </ol>
 * <p>
 * See the {@link ProtocolRequestBuilder} for the supported protocol
 * interface {@link java.lang.annotation.Annotation}s and method parameter
 * types.
 * </p>
 * <p>
 * See {@link ProtocolRequestBuilder} and
 * {@link ProtocolInvocationHandler} for a description of how
 * {@link HttpRequest}s are generated and executed.
 * </p>
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
@ToString
public abstract class ProtocolClient<P> implements HttpRequestInterceptor, HttpResponseInterceptor {
    private final CloseableHttpClient client;
    private final HttpCoreContext context;
    private final Class<? extends P> protocol;
    private final Object proxy;

    /**
     * Field exposed for subclass initialization;
     * see {@link #getJAXBContext()}.
     */
    protected transient JAXBContext jaxb = null;

    /**
     * Field exposed for subclass initialization;
     * see {@link #getObjectMapper()}.
     */
    protected transient ObjectMapper mapper = null;

    private transient Marshaller marshaller = null;
    private transient Unmarshaller unmarshaller = null;

    /**
     * Constructor that creates {@link HttpClientBuilder}
     * and {@link HttpCoreContext}.
     *
     * @param   protocol        The protocol {@link Class}.
     */
    protected ProtocolClient(Class<? extends P> protocol) {
        this(HttpClientBuilder.create(), null, protocol);
    }

    /**
     * Constructor that allows the subclass to provide a configured
     * {@link HttpClientBuilder} and/or {@link HttpCoreContext}.
     *
     * @param   builder         A configured {@link HttpClientBuilder}.
     * @param   context         A {@link HttpCoreContext} (may be
     *                          {@code null}).
     * @param   protocol        The protocol {@link Class}.
     */
    protected ProtocolClient(HttpClientBuilder builder, HttpCoreContext context, Class<? extends P> protocol) {
        this.client =
            builder
            .addRequestInterceptorLast(this)
            .addResponseInterceptorLast(this)
            .build();
        this.context = (context != null) ? context : HttpCoreContext.create();
        this.protocol = requireNonNull(protocol, "protocol");
        this.proxy =
            Proxy.newProxyInstance(protocol.getClassLoader(),
                                   new Class<?>[] { protocol }, new ProtocolInvocationHandler(this));
    }

    /**
     * @return  {@link ProtocolClient} {@link CloseableHttpClient}
     */
    public CloseableHttpClient client() { return client; }

    /**
     * @return  {@link ProtocolClient} {@link HttpCoreContext}
     */
    public HttpCoreContext context() { return context; }

    /**
     * @return  {@link #protocol()} {@link Class}
     */
    public Class<? extends P> protocol() { return protocol; }

    /**
     * @return  {@link #protocol()} {@link Proxy}
     */
    public P proxy() { return protocol.cast(proxy); }

    /**
     * @return  {@link Proxy} {@link ProtocolInvocationHandler}
     */
    public ProtocolInvocationHandler handler() {
        return (ProtocolInvocationHandler) Proxy.getInvocationHandler(proxy());
    }
/*
import ball.http.annotation.Protocol;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

    private Object getDefaultedValueOf(AnnotatedElement element, Class<? extends Annotation> type, String name) {
        Object object = null;

        try {
            Method method = type.getMethod(name);

            if (object == null) {
                Annotation annotation = element.getAnnotation(type);

                if (annotation != null) {
                    object = method.invoke(annotation);
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
*/
    /**
     * @return  {@link #protocol()} configured {@link JAXBContext}
     */
    public JAXBContext getJAXBContext() {
        if (jaxb == null) {
            synchronized(this) {
                if (jaxb == null) {
                    try {
                        jaxb = JAXBContext.newInstance(new Class<?>[] { protocol() });
                    } catch (JAXBException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        }

        return jaxb;
    }

    /**
     * @return  {@link #protocol()} configured {@link Marshaller}
     */
    public Marshaller getMarshaller() {
        if (marshaller == null) {
            synchronized(this) {
                if (marshaller == null) {
                    try {
                        marshaller = getJAXBContext().createMarshaller();
                        marshaller.setProperty(Marshaller.JAXB_ENCODING, UTF_8);
                    } catch (JAXBException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        }

        return marshaller;
    }

    /**
     * @return  {@link #protocol()} configured {@link Unmarshaller}
     */
    public Unmarshaller getUnmarshaller() {
        if (unmarshaller == null) {
            synchronized(this) {
                if (unmarshaller == null) {
                    try {
                        unmarshaller = getJAXBContext().createUnmarshaller();
                    } catch (JAXBException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        }

        return unmarshaller;
    }

    /**
     * @return  {@link #protocol()} configured {@link ObjectMapper}.
     */
    public ObjectMapper getObjectMapper() {
        if (mapper == null) {
            synchronized(this) {
                if (mapper == null) {
                    mapper = new ObjectMapper();
                }
            }
        }

        return mapper;
    }

    @Override
    public void process(HttpRequest request, EntityDetails details, HttpContext context) throws IOException {
    }

    @Override
    public void process(HttpResponse response, EntityDetails details, HttpContext context) throws IOException {
    }
}
