/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.client.method;

import ball.http.annotation.HttpMessageHeader;
import java.net.URI;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;

/**
 * Annotated {@link HttpUriRequest} marker interface.  Subclasses may be
 * annotated with {@link ball.http.annotation.URISpecification} to specify
 * an {@link URI} template and subclass fields and methods may be annotated
 * with {@link ball.http.annotation.URIScheme},
 * {@link ball.http.annotation.URIHost},
 * {@link ball.http.annotation.URIUserInfo},
 * {@link ball.http.annotation.URIPath},
 * {@link ball.http.annotation.URIPathParameter}, and
 * {@link ball.http.annotation.URIQueryParameter}
 * to specify runtime parameters to the {@link URI}.  The
 * {@link ball.http.annotation.HttpMessageHeader} annotation may be used to
 * specify {@link org.apache.http.HttpMessage}
 * {@link org.apache.http.Header}s at
 * runtime.  A default implementation for
 * {@link org.apache.http.HttpEntityEnclosingRequest#getEntity()}
 * based on Jackson annotations is provided.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public interface AnnotatedHttpUriRequest extends HttpUriRequest {

    /**
     * Method to get an {@link URIBuilder} configured from
     * {@link AnnotatedHttpUriRequest} annotations.
     *
     * @return  The {@link URIBuilder}.
     *
     * @see AnnotatedHttpUriRequest.Impl#getURIBuilder(AnnotatedHttpUriRequest)
     */
    public URIBuilder getURIBuilder();

    /**
     * {@inheritDoc}
     *
     * @see AnnotatedHttpUriRequest.Impl#getURI(AnnotatedHttpUriRequest)
     */
    @Override
    public URI getURI();

    /**
     * {@inheritDoc}
     *
     * @see AnnotatedHttpUriRequest.Impl#getAllHeaders(AnnotatedHttpUriRequest)
     */
    @Override
    public Header[] getAllHeaders();

    /**
     * {@link Impl} instance to help implement {@link #getURI()} and
     * {@link #getAllHeaders()} methods.
     */
    public static final Impl IMPL = new Impl();

    /**
     * Class to help implement {@link #getURIBuilder()}, {@link #getURI()}, and
     * {@link #getAllHeaders()} methods.
     */
    public class Impl {
        private Impl() { }

        /**
         * Method to help implement
         * {@link AnnotatedHttpUriRequest#getURIBuilder()}.
         *
         * @param       request         The {@link AnnotatedHttpUriRequest}.
         *
         * @return      The {@link URIBuilder}.
         */
        public URIBuilder getURIBuilder(AnnotatedHttpUriRequest request) {
            return URIBuilderFactory.getDefault().getInstance(request);
        }

        /**
         * Method to help implement
         * {@link AnnotatedHttpUriRequest#getURI()}.
         *
         * @param       request         The {@link AnnotatedHttpUriRequest}.
         *
         * @return      The {@link URI}.
         */
        public URI getURI(AnnotatedHttpUriRequest request) {
            URI uri = null;

            try {
                uri = getURIBuilder(request).build();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }

            return uri;
        }

        /**
         * Method to help implement
         * {@link AnnotatedHttpUriRequest#getAllHeaders()}.
         *
         * @param       request         The {@link AnnotatedHttpUriRequest}.
         */
        public void setAnnotatedHeaders(AnnotatedHttpUriRequest request) {
            try {
                for (Map.Entry<String,Object> entry :
                         NameValueMapFactory.getDefault()
                         .getInstance(request, HttpMessageHeader.class)
                         .entrySet()) {
                    request.setHeader(entry.getKey(),
                                      entry.getValue().toString());
                }
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public String toString() { return getClass().getName(); }
    }
}
