/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.client;

import ball.io.IOUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.AbstractResponseHandler;

/**
 * JSON {@link org.apache.http.HttpResponseHandler}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class JSONResponseHandler<T> extends AbstractResponseHandler<T> {
    protected static final ObjectMapper MAPPER =
        new ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true);

    /**
     * Sole constructor.
     */
    public JSONResponseHandler() { super(); }

    @Override
    public T handleEntity(HttpEntity entity) throws IOException {
        T object = null;

        if (entity != null) {
            InputStream in = null;

            try {
                in = entity.getContent();
                object = MAPPER.readValue(in, new TypeReference<T>() { });
            } finally {
                IOUtil.close(in);
            }
        }

        return object;
    }

    @Override
    public String toString() { return super.toString(); }
}
