/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.ant.taskdefs;

import ball.activation.ReaderWriterDataSource;
import ball.io.IOUtil;
import ball.util.AbstractPredicate;
import ball.util.FilteredIterator;
import ball.util.MapUtil;
import ball.util.PropertiesImpl;
import ball.util.ant.taskdefs.AbstractClasspathTask;
import ball.util.ant.taskdefs.AntTask;
import ball.util.ant.types.StringAttributeType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

import static ball.activation.ReaderWriterDataSource.CONTENT_TYPE;
import static ball.util.StringUtil.NIL;
import static ball.util.StringUtil.isNil;

/**
 * Abstract {@link.uri http://ant.apache.org/ Ant} base
 * {@link org.apache.tools.ant.Task} for GET and POST operations.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public abstract class HTTPTask extends AbstractClasspathTask {
    private static final String AMPERSAND = "&";
    private static final String DOT = ".";
    private static final String EQUALS = "=";

    private URI uri = null;
    private String query = null;
    private final List<StringAttributeType> parameters = new ArrayList<>();
    private final List<StringAttributeType> headers = new ArrayList<>();
    private String content = null;

    /**
     * Sole constructor.
     */
    protected HTTPTask() { super(); }

    public URI getURI() { return uri; }
    public void setURI(String string) { this.uri = URI.create(string); }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

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

    /**
     * Method to construct the {@link HTTPTask}-specific
     * {@link HttpUriRequest}.
     *
     * @return  The {@link HttpUriRequest}.
     */
    protected abstract HttpUriRequest request();

    /**
     * Method to configure the {@link HTTPTask} {@link HttpUriRequest}.
     * See {@link #execute()} and {@link #request()}.
     *
     * @param   request         The {@link HttpUriRequest}.
     *
     * @throws  Exception       If an exception is encountered.
     */
    protected void configure(HttpUriRequest request) throws Exception {
        String method = request.getMethod().toUpperCase();
        Properties properties =
            getPrefixedProperties(method + DOT, getProject().getProperties());

        if (getURI() != null) {
            setRequestURI(request, getURI());
        } else if (properties.containsKey("uri")) {
            setRequestURI(request, URI.create(properties.getProperty("uri")));
        }

        if (! isNil(getQuery())) {
            addToRequestURIQuery(request, getQuery());
        } else if (properties.containsKey("query")) {
            addToRequestURIQuery(request, properties.getProperty("query"));
        }

        if (! parameters.isEmpty()) {
            addToRequestURIQuery(request, asQuery(parameters));
        } else {
            Properties parameters =
                getPrefixedProperties("parameter" + DOT, properties);

            addToRequestURIQuery(request, asQuery(parameters.entrySet()));
        }

        if (! headers.isEmpty()) {
            addToRequestHeaders(request, headers);
        } else {
            Properties headers =
                getPrefixedProperties("header" + DOT, properties);

            addToRequestHeaders(request, headers.entrySet());
        }

        if (! isNil(getContent())) {
            setRequestEntity(request, getContent());
        } else if (properties.containsKey("content")) {
            setRequestEntity(request, properties.getProperty("content"));
        }
    }

    private Properties getPrefixedProperties(String prefix, Map<?,?> map) {
        return MapUtil.copy(new EntryKeyPrefixedWith(prefix, map),
                            new PropertiesImpl());
    }

    private void setRequestURI(HttpUriRequest request, URI uri) {
        ((HttpRequestBase) request).setURI(uri);
    }

    private void addToRequestURIQuery(HttpUriRequest request,
                                      String query) throws Exception {
        URI uri = request.getURI();

        if (uri != null && (! isNil(query))) {
            StringBuilder buffer = new StringBuilder();

            if (! isNil(uri.getQuery())) {
                buffer.append(uri.getQuery());
            }

            if (! isNil(buffer)) {
                buffer.append(AMPERSAND);
            }

            buffer.append(query);

            setRequestURI(request,
                          new URI(uri.getScheme(), uri.getAuthority(),
                                  uri.getPath(),
                                  buffer.toString(), uri.getFragment()));
        }
    }

    private String asQuery(Iterable<? extends Map.Entry<?,?>> iterable) {
        StringBuilder buffer = new StringBuilder();

        for (Map.Entry<?,?> entry : iterable) {
            if (! isNil(buffer)) {
                buffer.append(AMPERSAND);
            }

            buffer
                .append(entry.getKey())
                .append(EQUALS)
                .append(entry.getValue());
        }

        return buffer.toString();
    }

    private void addToRequestHeaders(HttpRequest request,
                                     Iterable<? extends Map.Entry<?,?>> iterable) {
        for (Map.Entry<?,?> entry : iterable) {
            request.addHeader(entry.getKey().toString(),
                              entry.getValue().toString());
        }
    }

    private void setRequestEntity(HttpUriRequest request,
                                  String content) throws Exception {
        if (! isNil(content)) {
            ((HttpEntityEnclosingRequest) request)
                .setEntity(new StringEntity(content));
        }
    }

    @Override
    public void execute() throws BuildException {
        super.execute();

        HttpClient client = null;

        try {
            client = HttpClientBuilder.create().build();

            HttpUriRequest request = request();

            configure(request);

            log(request);
            log(NIL);

            HttpResponse response = client.execute(request);

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
     * See {@link #log(String)}.
     *
     * @param   message         The {@link HttpMessage} to log.
     */
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
     * {@bean.info}
     */
    @AntTask("http-get")
    public static class Get extends HTTPTask {

        /**
         * Sole constructor.
         */
        public Get() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpGet(); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to POST.
     *
     * {@bean.info}
     */
    @AntTask("http-post")
    public static class Post extends HTTPTask {

        /**
         * Sole constructor.
         */
        public Post() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpPost(); }
    }

    private class EntryKeyPrefixedWith
                  extends FilteredIterator<Map.Entry<?,?>> {
        public EntryKeyPrefixedWith(String prefix, Map<?,?> map) {
            this(prefix, map.entrySet());
        }

        public EntryKeyPrefixedWith(String prefix,
                                    Iterable<? extends Map.Entry<?,?>> iterable) {
            super(new KeyStartsWith(prefix), iterable);
        }

        @Override
        public KeyStartsWith getPredicate() {
            return (KeyStartsWith) super.getPredicate();
        }

        @Override
        public Map.Entry<?,?> next() {
            Map.Entry<?,?> entry = super.next();
            Object key =
                entry.getKey().toString()
                .substring(getPredicate().getPrefix().length());

            return new AbstractMap.SimpleEntry<>(key, entry.getValue());
        }
    }

    private class KeyStartsWith extends AbstractPredicate<Map.Entry<?,?>> {
        private final String prefix;

        public KeyStartsWith(String prefix) {
            super();

            this.prefix = prefix;
        }

        public String getPrefix() { return prefix; }

        @Override
        public boolean apply(Map.Entry<?,?> entry) {
            return entry.getKey().toString().startsWith(prefix);
        }
    }
}
