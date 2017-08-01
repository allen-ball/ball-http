/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;

/**
 * <p>
 * Annotated service protocol builder.  The {@link #build(Class)} method
 * creates a {@link Proxy} that implements both the argument protocol
 * interface and the super-interfaces of {@link CloseableHttpClient}.
 * </p><p>
 * The {@link java.lang.reflect.InvocationHandler} implementation is
 * {@link ProtocolInvocationHandler} and supported
 * {@link java.lang.annotation.Annotation}s are documented in the
 * {@code apply(Annotation,...)} methods.
 * </p><p>
 * If the interface method return type is any subclass of
 * {@link org.apache.http.HttpMessage} and assignable from the constructed
 * request then the constructed request is simply returned.  Otherwise, the
 * request is executed.
 * </p><p>
 * If the interface method return type is
 * {@link org.apache.http.HttpResponse} the request is executed and the
 * response is returned.  The caller is responsible for consuming the
 * entity.  Otherwise, the {@link ProtocolInvocationHandler} installs a
 * {@link org.apache.http.client.ResponseHandler} that will parse the
 * entities.  See {@link ProtocolInvocationHandler#getUnmarshaller()}
 * and {@link ProtocolInvocationHandler#getObjectMapper()}.
 * </p><p>
 * Protocol API authors should consider designing protocol methods to throw
 * {@link org.apache.http.client.HttpResponseException},
 * {@link org.apache.http.client.ClientProtocolException}, and
 * {@link java.io.IOException}.
 * </p>
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolClientBuilder {
    private final HttpClientBuilder builder;

    /**
     * No-argument constructor.
     */
    public ProtocolClientBuilder() { this(HttpClientBuilder.create()); }

    /**
     * Constructor to provide {@link HttpClientBuilder}.
     *
     * @param   builder         A configured {@link HttpClientBuilder}.
     */
    public ProtocolClientBuilder(HttpClientBuilder builder) {
        this.builder = builder;
    }

    /**
     * Method to create a service instance.  The service instance is a
     * {@link Proxy} that implements both the {@code protocol} interface and
     * the interfaces {@link CloseableHttpClient} implements.
     *
     * @param   protocol        Annotated {@link Class} (interface)
     *                          describing the service.
     *
     * @param   <T>             The protocol type ({@link Class}).
     *
     * @return  The service instance.
     */
    public <T> T build(Class<? extends T> protocol) {
        CloseableHttpClient client = builder.build();
        ArrayList<Class<?>> list = new ArrayList<>();

        Collections.addAll(list, CloseableHttpClient.class.getInterfaces());
        Collections.addAll(list, protocol);

        ProtocolInvocationHandler handler =
            new ProtocolInvocationHandler(client, protocol);
        Object proxy =
            Proxy.newProxyInstance(protocol.getClassLoader(),
                                   list.toArray(new Class<?>[] { }),
                                   handler);

        return protocol.cast(proxy);
    }

    @Override
    public String toString() { return super.toString(); }
}
