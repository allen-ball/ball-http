/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.ant.taskdefs;

import ball.activation.ReaderWriterDataSource;
import ball.io.IOUtil;
import ball.util.ant.taskdefs.AbstractClasspathTask;
import ball.util.ant.taskdefs.AntTask;
import ball.util.ant.types.StringAttributeType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.tools.ant.BuildException;

import static ball.util.StringUtil.NIL;
import static ball.util.StringUtil.isNil;
import static ball.activation.ReaderWriterDataSource.CONTENT_TYPE;

/**
 * Abstract {@link.uri http://ant.apache.org/ Ant} base
 * {@link org.apache.tools.ant.Task} for GET and POST operations.
 *
 * {@bean-info}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public abstract class HTTPTask extends AbstractClasspathTask {
    private static final String DOT = ".";

    private URI uri = null;
    private final ArrayList<StringAttributeType> parameters =
        new ArrayList<>();
    private final ArrayList<StringAttributeType> headers = new ArrayList<>();
    private String content = null;

    /**
     * Sole constructor.
     */
    protected HTTPTask() { super(); }

    public URI getURI() { return uri; }
    public void setURI(String string) { this.uri = URI.create(string); }

    public void addConfiguredParameter(StringAttributeType parameter) {
        parameters.add(parameter);
    }

    public void addConfiguredHeader(StringAttributeType header) {
        headers.add(header);
    }

    public void addText(String text) {
        setContent((isNil(getContent()) ? NIL : getContent()) + text);
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    @Override
    public void execute() throws BuildException {
        super.execute();

        HttpClient client = null;

        try {
            client = HttpClientBuilder.create().build();

            HttpUriRequest request = request();
            String method = request.getMethod();

            ((HttpRequestBase) request).setURI(getURI());

            if (! parameters.isEmpty()) {
                URI uri = request.getURI();

                if (uri != null) {
                    String query = uri.getQuery();

                    for (StringAttributeType parameter : parameters) {
                        String string =
                            parameter.getName() + "=" + parameter.getValue();

                        if (isNil(query)) {
                            query = string;
                        } else {
                            query += "&" + string;
                        }
                    }

                    if (! isNil(query)) {
                        uri =
                            new URI(uri.getScheme(), uri.getAuthority(),
                                    uri.getPath(), query, uri.getFragment());
                    }

                    ((HttpRequestBase) request).setURI(uri);
                }
            }

            for (StringAttributeType header : headers) {
                request.addHeader(header.getName(), header.getValue());
            }

            if (! isNil(getContent())) {
                ((HttpEntityEnclosingRequest) request)
                    .setEntity(new StringEntity(getContent()));
            }

            log(request);

            HttpResponse response = client.execute(request);

            log(NIL);
            log(response);
        } catch (BuildException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            throw new BuildException(throwable);
        } finally {
            IOUtil.close(client);
        }
    }

    /**
     * Method to construct the {@link HTTPTask}-specific
     * {@link HttpUriRequest}.
     *
     * @return  The {@link HttpUriRequest}.
     */
    protected abstract HttpUriRequest request();

    protected void log(HttpMessage message) {
        if (message instanceof HttpRequest) {
            log(String.valueOf(((HttpRequest) message).getRequestLine()));
        }

        if (message instanceof HttpResponse) {
            log(String.valueOf(((HttpResponse) message).getStatusLine()));
        }

        for (Header header : message.getAllHeaders()) {
            log(String.valueOf(header));
        }

        if (message instanceof HttpEntityEnclosingRequest) {
            InputStream in = null;

            try {
                HttpEntity entity =
                    ((HttpEntityEnclosingRequest) message).getEntity();

                if (entity != null) {
                    String type =
                        message.containsHeader(CONTENT_TYPE)
                            ? message.getFirstHeader(CONTENT_TYPE).getValue()
                            : null;
                    ReaderWriterDataSource ds =
                        new ReaderWriterDataSource(null, type);

                    in = entity.getContent();
                    IOUtil.copy(in, ds);

                    String string = ds.toString();

                    if (! isNil(string)) {
                        log(NIL);
                        log(string);
                    }
                }
            } catch (IOException exception) {
            } finally {
                IOUtil.close(in);
            }
        }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to GET.
     *
     * {@bean-info}
     */
    @AntTask("http-get")
    public static class Get extends HTTPTask {

        /**
         * Sole constructor.
         */
        public Get() { super(); }

        @Override
        protected HttpUriRequest request() {
            HttpGet request = new HttpGet();

            return request;
        }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to POST.
     *
     * {@bean-info}
     */
    @AntTask("http-post")
    public static class Post extends HTTPTask {

        /**
         * Sole constructor.
         */
        public Post() { super(); }

        @Override
        protected HttpUriRequest request() {
            HttpPost request = new HttpPost();

            return request;
        }
    }
}
