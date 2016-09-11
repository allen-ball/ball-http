/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.method;

import java.net.URI;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;

/**
 * Annotated {@link HttpGet} base class.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public abstract class AnnotatedHttpGet extends HttpGet
                                       implements AnnotatedHttpUriRequest {

    /**
     * Sole constructor.
     */
    protected AnnotatedHttpGet() { super(); }

    @Override
    public URIBuilder getURIBuilder() {
        return AnnotatedHttpUriRequest.IMPL.getURIBuilder(this);
    }

    @Override
    public URI getURI() { return AnnotatedHttpUriRequest.IMPL.getURI(this); }
}
