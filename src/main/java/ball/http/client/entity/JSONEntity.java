/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.entity;

import ball.activation.ByteArrayDataSource;
import ball.io.IOUtil;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;

/**
 * JSON {@link org.apache.http.HttpEntity} implementation.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public class JSONEntity extends AbstractHttpEntity {
    private static final ContentType APPLICATION_JSON =
        ContentType.APPLICATION_JSON
        .withCharset(JsonEncoding.UTF8.getJavaName());

    private static final ObjectMapper MAPPER =
        new ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true);

    private final Object object;

    /**
     * Sole constructor.
     *
     * @param   object          The {@link Object} to serialize.
     */
    public JSONEntity(Object object) {
        super();

        if (object != null) {
            this.object = object;
        } else {
            throw new NullPointerException("object");
        }

        setChunked(false);
        setContentType(APPLICATION_JSON.toString());
    }

    @Override
    public boolean isRepeatable() { return true; }

    @Override
    public long getContentLength() { return -1; }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
        ByteArrayDataSource ds = new ByteArrayDataSource(null, null);
        OutputStream out = null;

        try {
            out = ds.getOutputStream();
            writeTo(out);
        } finally {
            IOUtil.close(out);
        }

        return ds.getInputStream();
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        MAPPER.writeValue(out, object);
    }

    @Override
    public boolean isStreaming() { return false; }
}
