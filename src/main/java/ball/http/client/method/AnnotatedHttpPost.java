/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.method;

import ball.http.client.entity.JSONEntity;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.net.URI;
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

    /**
     * Sole constructor.
     */
    protected AnnotatedHttpPost() { super(); }

    @Override
    public URIBuilder getURIBuilder() {
        return AnnotatedHttpUriRequest.IMPL.getURIBuilder(this);
    }

    @Override
    public URI getURI() { return AnnotatedHttpUriRequest.IMPL.getURI(this); }

    @Override
    public HttpEntity getEntity() {
        HttpEntity entity = super.getEntity();

        if (entity == null) {
            entity = new JSONEntity(this);
        }

        return entity;
    }
}
