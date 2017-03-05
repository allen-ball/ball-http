/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.method;

import java.net.URI;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.utils.URIBuilder;

/**
 * Annotated {@link HttpPatch} base class.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public abstract class AnnotatedHttpPatch extends HttpPatch
                                         implements AnnotatedHttpUriRequest {

    /**
     * Sole constructor.
     */
    protected AnnotatedHttpPatch() { super(); }

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
}
