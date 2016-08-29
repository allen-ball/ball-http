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
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
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
    private static final String DOT = ".";

    private PropertiesImpl properties = null;
    private URIBuilder builder = new URIBuilder();
    private final List<NameValuePairImpl> headers = new ArrayList<>();
    private String content = null;

    /**
     * Sole constructor.
     */
    protected HTTPTask() { super(); }

    public void setURI(String string) throws URISyntaxException {
        builder = new URIBuilder(string);
    }

    public void setCharset(String string) {
        builder.setCharset(Charset.forName(string));
    }

    public void setFragment(String string) { builder.setFragment(string); }
    public void setHost(String string) { builder.setHost(string); }
    public void setPath(String string) { builder.setPath(string); }
    public void setPort(Integer integer) { builder.setPort(integer); }
    public void setQuery(String string) { builder.setCustomQuery(string); }
    public void setScheme(String string) { builder.setScheme(string); }
    public void setUserInfo(String string) { builder.setUserInfo(string); }

    public void addConfiguredParameter(NameValuePairImpl parameter) {
        builder.addParameter(parameter.getName(), parameter.getValue());
    }

    public void addConfiguredHeader(NameValuePairImpl header) {
        headers.add(header);
    }

    public void addText(String text) {
        setContent((isNil(getContent()) ? NIL : getContent()) + text);
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    @Override
    public void init() throws BuildException {
        super.init();

        String method = getClass().getSimpleName().toUpperCase();

        properties =
            getPrefixedProperties(method + DOT, getProject().getProperties());

        try {
            if (properties.containsKey("uri")) {
                builder = new URIBuilder(properties.getProperty("uri"));
            }

            properties.configure(builder);

            for (Map.Entry<?,?> entry :
                     getPrefixedProperties("parameter" + DOT, properties)
                     .entrySet()) {
                builder.addParameter(entry.getKey().toString(),
                                     entry.getValue().toString());
            }
        } catch (BuildException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            throw new BuildException(throwable);
        }
    }

    private PropertiesImpl getPrefixedProperties(String prefix, Map<?,?> map) {
        PropertiesImpl properties = new PropertiesImpl();

        MapUtil.copy(new EntryKeyPrefixedWith(prefix, map), properties);

        return properties;
    }

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
        ((HttpRequestBase) request).setURI(builder.build());

        addHeaders(request,
                   getPrefixedProperties("header" + DOT, properties)
                   .entrySet());
        addHeaders(request, headers);

        if (! isNil(getContent())) {
            setEntity(request, getContent());
        } else if (! isNil(properties.getProperty("content"))) {
            setEntity(request, properties.getProperty("content"));
        }
    }

    private void addHeaders(HttpRequest request,
                            Iterable<? extends Map.Entry<?,?>> iterable) {
        for (Map.Entry<?,?> entry : iterable) {
            request.addHeader(entry.getKey().toString(),
                              entry.getValue().toString());
        }
    }

    private void setEntity(HttpUriRequest request,
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

        log(getContentType(message), getHttpEntity(message));
    }

    private String getContentType(HttpMessage message) {
        return (message.containsHeader(CONTENT_TYPE)
                    ? message.getFirstHeader(CONTENT_TYPE).getValue()
                    : null);
    }

    private HttpEntity getHttpEntity(HttpMessage message) {
        HttpEntity entity = null;

        if (message instanceof HttpEntityEnclosingRequest) {
            entity = ((HttpEntityEnclosingRequest) message).getEntity();
        } else if (message instanceof HttpResponse) {
            entity = ((HttpResponse) message).getEntity();
        }

        return entity;
    }

    /**
     * See {@link #log(String)}.
     *
     * @param   type            The entity {@code Content-Type} (if
     *                          specified).
     * @param   entity          The {@link HttpEntity} to log.
     */
    protected void log(String type, HttpEntity entity) {
        if (entity != null) {
            OutputStream out = null;

            try {
                ReaderWriterDataSource ds =
                    new ReaderWriterDataSource(null, type);

                out = ds.getOutputStream();
                entity.writeTo(out);
                out.close();

                String string = ds.toString();

                if (! isNil(string)) {
                    log(NIL);
                    log(string);
                }
            } catch (IOException exception) {
            } finally {
                IOUtil.close(out);
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

    /**
     * {@link StringAttributeType} implementation that includes
     * {@link NameValuePair}.
     */
    public static class NameValuePairImpl extends StringAttributeType
                                          implements NameValuePair {
        /**
         * Sole constructor.
         */
        public NameValuePairImpl() { super(); }
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
