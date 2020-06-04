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
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
/* import java.lang.reflect.TypeVariable; */
/* import java.lang.reflect.WildcardType; */
import java.util.Collection;
import java.util.Map;
import javax.xml.bind.JAXBException;
import lombok.ToString;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.AbstractResponseHandler;
import org.apache.http.util.EntityUtils;

import static java.util.Objects.requireNonNull;

/**
 * {@link ProtocolClient} {@link org.apache.http.client.ResponseHandler}
 * implementation.  Makes use of {@link ProtocolClient#getUnmarshaller()}
 * and {@link ProtocolClient#getObjectMapper()} for de-serialization.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@ToString
public class ProtocolResponseHandler extends AbstractResponseHandler<Object> {
    private final ProtocolClient<?> client;
    private final Method method;

    /**
     * Sole constructor.
     *
     * @param   client          The {@link ProtocolClient}.
     * @param   method          The protocol {@link Method}.
     */
    protected ProtocolResponseHandler(ProtocolClient<?> client,
                                      Method method) {
        super();

        this.client = requireNonNull(client, "client");
        this.method = requireNonNull(method, "method");
    }

    @Override
    public Object handleEntity(HttpEntity entity) throws ClientProtocolException,
                                                         IOException {
        Object object = null;

        try {
            String type =
                ContentType.getLenientOrDefault(entity).getMimeType()
                .replaceAll("[^\\p{Alnum}]", "_").toUpperCase();

            object =
                getClass()
                .getDeclaredMethod(type, HttpEntity.class)
                .invoke(this, entity);
        } catch (NoSuchMethodException exception) {
            object = EntityUtils.toString(entity);
        } catch (Exception exception) {
            if (exception instanceof ClientProtocolException) {
                throw (ClientProtocolException) exception;
            } else if (exception instanceof IOException) {
                throw (IOException) exception;
            } else {
                throw new ClientProtocolException(exception);
            }
        }

        return method.getReturnType().cast(object);
    }

    protected Object APPLICATION_JSON(HttpEntity entity) throws ClientProtocolException,
                                                                IOException {
        Object object = null;
        ObjectMapper om = client.getObjectMapper();
        TypeFactory factory = om.getTypeFactory();
        JavaType type =
            getJavaTypeFrom(factory, method.getGenericReturnType());

        try (InputStream in = entity.getContent()) {
            object = om.readValue(in, type);
        }

        return object;
    }

    private JavaType getJavaTypeFrom(TypeFactory factory, Type type) {
        JavaType java = null;

        if (type instanceof GenericArrayType) {
            java = getJavaTypeFrom(factory, (GenericArrayType) type);
        } else if (type instanceof ParameterizedType) {
            java = getJavaTypeFrom(factory, (ParameterizedType) type);
/*
        } else if (type instanceof TypeVariable) {
        } else if (type instanceof WildcardType) {
*/
        }

        return (java != null) ? java : factory.constructType(type);
    }

    private JavaType getJavaTypeFrom(TypeFactory factory,
                                     GenericArrayType type) {
        Type element = type.getGenericComponentType();

        return factory.constructArrayType(getJavaTypeFrom(factory, element));
    }

    private JavaType getJavaTypeFrom(TypeFactory factory,
                                     ParameterizedType type) {
        JavaType java = null;
        Class<?> raw = (Class<?>) type.getRawType();
        Type[] arguments = type.getActualTypeArguments();

        if (Collection.class.isAssignableFrom(raw)) {
            java =
                factory
                .constructCollectionType(raw.asSubclass(Collection.class),
                                         getJavaTypeFrom(factory, arguments[0]));
        } else if (Map.class.isAssignableFrom(raw)) {
            java =
                factory
                .constructMapType(raw.asSubclass(Map.class),
                                  getJavaTypeFrom(factory, arguments[0]),
                                  getJavaTypeFrom(factory, arguments[1]));
        }

        return (java != null) ? java : factory.constructType(type);
    }

    protected Object APPLICATION_XML(HttpEntity entity) throws ClientProtocolException,
                                                               IOException {
        Object object = null;

        try (InputStream in = entity.getContent()) {
            object = client.getUnmarshaller().unmarshal(in);
        } catch (JAXBException exception) {
            throw new IOException(exception);
        }

        return object;
    }
}
