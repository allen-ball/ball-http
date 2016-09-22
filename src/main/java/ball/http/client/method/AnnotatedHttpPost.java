/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.method;

import ball.http.client.entity.JSONEntity;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

/**
 * Annotated {@link HttpPost} base class.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@JsonAutoDetect(creatorVisibility=NONE, fieldVisibility=NONE,
                getterVisibility=NONE, isGetterVisibility=NONE,
                setterVisibility=NONE)
public abstract class AnnotatedHttpPost extends HttpPost
                                        implements AnnotatedHttpUriRequest {
    private ObjectMapper mapper = null;
    private Object object = null;

    /**
     * Sole constructor.
     */
    protected AnnotatedHttpPost() {
        super();

        setEntityObject(this);
    }

    /**
     * Method to get the {@link ObjectMapper} to serialize the
     * {@link HttpEntity}.
     *
     * @return  The {@link ObjectMapper}.
     */
    protected ObjectMapper getObjectMapper() { return mapper; }

    /**
     * Method to set the {@link ObjectMapper} to serialize the
     * {@link HttpEntity}.
     *
     * @param   mapper          The {@link ObjectMapper}.
     */
    protected void setObjectMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Method to get the {@link Object} that represesnts the
     * {@link HttpEntity}.
     *
     * @return  The {@link Object}.
     */
    protected Object getEntityObject() { return object; }

    /**
     * Method to set the {@link Object} that represesnts the
     * {@link HttpEntity}.
     *
     * @param   object          The {@link Object}.
     */
    protected void setEntityObject(Object object) { this.object = object; }

    @Override
    public URIBuilder getURIBuilder() {
        return AnnotatedHttpUriRequest.IMPL.getURIBuilder(this);
    }

    @Override
    public URI getURI() { return AnnotatedHttpUriRequest.IMPL.getURI(this); }

    @Override
    public Header[] getAllHeaders() {
        AnnotatedHttpUriRequest.IMPL.setAnnotatedHeaders(this);

        return super.getAllHeaders();
    }

    @Override
    public HttpEntity getEntity() {
        HttpEntity entity = super.getEntity();

        if (entity == null) {
            entity = new JSONEntity(getObjectMapper(), getEntityObject());
        }

        return entity;
    }
}
