/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http;

import ball.io.IOUtil;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.bind.JAXBException;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.AbstractResponseHandler;
import org.apache.http.util.EntityUtils;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.APPLICATION_XML;

/**
 * {@link ProtocolClient} {@link org.apache.http.client.ResponseHandler}
 * implementation.  Makes use of {@link ProtocolClient#getUnmarshaller()}
 * and {@link ProtocolClient#getObjectMapper()} for de-serialization.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class ProtocolResponseHandler extends AbstractResponseHandler<Object> {
    private final ProtocolClient<?> client;
    private final Class<?> returnType;

    /**
     * Sole constructor.
     *
     * @param   client          The {@link ProtocolClient}.
     * @param   returnType      The {@link Class} of the target return
     *                          value.
     */
    protected ProtocolResponseHandler(ProtocolClient<?> client,
                                      Class<?> returnType) {
        super();

        if (client != null) {
            this.client = client;
        } else {
            throw new NullPointerException("client");
        }

        if (returnType != null) {
            this.returnType = returnType;
        } else {
            throw new NullPointerException("returnType");
        }
    }

    @Override
    public Object handleEntity(HttpEntity entity) throws ClientProtocolException,
                                                         IOException {
        Object object = null;
        ContentType contentType =
            ContentType.getLenientOrDefault(entity);

        if (sameMimeType(APPLICATION_JSON, contentType)) {
            InputStream in = null;

            try {
                in = entity.getContent();
                object = client.getObjectMapper().readValue(in, returnType);
            } finally {
                IOUtil.close(in);
            }
        } else if (sameMimeType(APPLICATION_XML, contentType)) {
            InputStream in = null;

            try {
                in = entity.getContent();
                object = client.getUnmarshaller().unmarshal(in);
            } catch (JAXBException exception) {
                throw new IOException(exception);
            } finally {
                IOUtil.close(in);
            }
        } else {
            object = EntityUtils.toString(entity);
        }

        return type.cast(object);
    }

    private boolean sameMimeType(ContentType left, ContentType right) {
        return left.getMimeType().equalsIgnoreCase(right.getMimeType());
    }

    @Override
    public String toString() { return super.toString(); }
}
