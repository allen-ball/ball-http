package ball.http;
/*-
 * ##########################################################################
 * Web API Client (HTTP) Utilities
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2016 - 2021 Allen D. Ball
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
import ball.lang.reflect.DefaultInterfaceMethodInvocationHandler;
import java.lang.reflect.Method;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

/**
 * Protocol {@link java.lang.reflect.InvocationHandler} for
 * {@link ProtocolClient}.  The {@link #invoke(Object,Method,Object[])}
 * translates the interface {@link Method} invocation into an
 * {@link HttpMessage} through a {@link ProtocolRequestBuilder}.  If the
 * {@link Method#getReturnType()} is assignable from the generated
 * {@link HttpMessage}, then it is simply returned; otherwise, the request
 * is executed through {@link ProtocolClient#client()} with the
 * {@link ProtocolClient#context()}.  If the {@link Method#getReturnType()}
 * is {@link HttpResponse} then the response is returned with no further
 * processing (and the caller is responsible for consuming any entities).
 * Otherwise, a {@link ProtocolResponseHandler} is provided to the call.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@RequiredArgsConstructor @ToString
public class ProtocolInvocationHandler implements DefaultInterfaceMethodInvocationHandler {
    @NonNull private final ProtocolClient<?> client;

    @Override
    public Object invoke(Object proxy,
                         Method method, Object[] argv) throws Throwable {
        Object result = null;
        Class<?> declarer = method.getDeclaringClass();

        if (method.isDefault()) {
            result =
                DefaultInterfaceMethodInvocationHandler.super
                .invoke(proxy, method, argv);
        } else if (declarer.equals(Object.class)) {
            result = method.invoke(proxy, argv);
        } else {
            Class<?> returnType = method.getReturnType();
            HttpMessage request =
                new ProtocolRequestBuilder(client).build(method, argv);

            if (returnType.isAssignableFrom(request.getClass())) {
                result = returnType.cast(request);
            } else if (returnType.isAssignableFrom(HttpResponse.class)) {
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

        return result;
    }
}
