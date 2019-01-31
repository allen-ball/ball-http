/*
 * $Id$
 *
 * Copyright 2017 - 2019 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import ball.util.ClassOrder;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import static java.util.Objects.requireNonNull;

/**
 * Protocol {@link InvocationHandler} for {@link ProtocolClient}.  The
 * {@link #invoke(Object,Method,Object[])} translates the interface
 * {@link Method} invocation into an {@link HttpMessage} through a
 * {@link ProtocolRequestBuilder}.  If the {@link Method#getReturnType()} is
 * assignable from the generated {@link HttpMessage}, then it is simply
 * returned; otherwise, the request is executed through
 * {@link ProtocolClient#client()} with the
 * {@link ProtocolClient#context()}.  If the {@link Method#getReturnType()}
 * is {@link HttpResponse} then the response is returned with no further
 * processing (and the caller is responsible for consuming any entities).
 * Otherwise, a {@link ProtocolResponseHandler} is provided to the call.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolInvocationHandler implements InvocationHandler {
    private final ProtocolClient<?> client;
    private final ProtocolRequestBuilder builder;

    /**
     * Sole constructor.
     *
     * @param   client          The {@link ProtocolClient}.
     */
    protected ProtocolInvocationHandler(ProtocolClient<?> client) {
        this.client = requireNonNull(client, "client");
        this.builder = new ProtocolRequestBuilder(client);
    }

    @Override
    public Object invoke(Object proxy,
                         Method method, Object[] argv) throws Throwable {
        Object result = null;

        if (method.isDefault()) {
            Class<?> declarer = method.getDeclaringClass();

            result =
                MethodHandles.lookup()
                .in(declarer)
                .unreflectSpecial(method, declarer)
                .bindTo(proxy)
                .invokeWithArguments(argv);
        } else {
            Class<?> returnType = method.getReturnType();
            HttpMessage request = builder.build(method, argv);

            if (returnType.isAssignableFrom(request.getClass())) {
                result = returnType.cast(request);
            } else {
                if (returnType.isAssignableFrom(HttpResponse.class)) {
                    result =
                        client.client()
                        .execute((HttpUriRequest) request, client.context());
                } else {
                    result =
                        client.client()
                        .execute((HttpUriRequest) request,
                                 new ProtocolResponseHandler(client, method),
                                 client.context());
                }
            }
        }

        return result;
    }

    @Override
    public String toString() { return super.toString(); }
}
