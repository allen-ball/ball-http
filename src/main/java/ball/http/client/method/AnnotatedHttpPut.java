/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.method;

import java.net.URI;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;

/**
 * Annotated {@link HttpPut} base class.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public abstract class AnnotatedHttpPut extends HttpPut
                                       implements AnnotatedHttpUriRequest {

    /**
     * Sole constructor.
     */
    protected AnnotatedHttpPut() { super(); }

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
