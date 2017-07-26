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
 * Annotated service protocol builder.  The {@link #build(Class)} method
 * creates a {@link Proxy} that implements both the argument protocol
 * interface and the super-interfaces of {@link CloseableHttpClient}.  The
 * {@link java.lang.reflect.InvocationHandler} implementation is
 * {@link ProtocolInvocationHandler} and supported
 * {@link java.lang.annotation.Annotation}s are documented in the
 * {@code apply(Annotation,...)} methods.  If the interface method return
 * type {@link Class#isAssignableFrom(Class)} (subclass of
 * {@link org.apache.http.HttpRequest}) then the constructed request is
 * simply returned.  Otherwise, the request is executed.  Supported protocol
 * return types are documented in the
 * {@code asSimpleClassName(HttpResponse)} methods (e.g.,
 * {@link ProtocolInvocationHandler#asHttpEntity(HttpResponse)}).
 *
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
