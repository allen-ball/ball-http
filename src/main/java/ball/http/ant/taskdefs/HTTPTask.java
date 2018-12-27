/*
 * $Id$
 *
 * Copyright 2016 - 2018 Allen D. Ball.  All rights reserved.
 */
package ball.http.ant.taskdefs;

import ball.activation.ReaderWriterDataSource;
import ball.io.IOUtil;
import ball.swing.table.MapTableModel;
import ball.util.BeanMap;
import ball.util.MapUtil;
import ball.util.PropertiesImpl;
import ball.util.ant.taskdefs.AbstractClasspathTask;
import ball.util.ant.taskdefs.AntTask;
import ball.util.ant.types.StringAttributeType;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.tools.ant.BuildException;

import static ball.activation.ReaderWriterDataSource.CONTENT_TYPE;
import static ball.util.StringUtil.NIL;
import static ball.util.StringUtil.isNil;
import static org.apache.tools.ant.Project.toBoolean;

/**
 * Abstract {@link.uri http://ant.apache.org/ Ant} base
 * {@link org.apache.tools.ant.Task} for web API client tasks.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public abstract class HTTPTask extends AbstractClasspathTask
                               implements HttpRequestInterceptor,
                                          HttpResponseInterceptor {
    private static final String DOT = ".";

    private boolean buffer = false;
    private final HttpClientBuilder builder =
        HttpClientBuilder.create()
        .addInterceptorLast((HttpRequestInterceptor) this)
        .addInterceptorLast((HttpResponseInterceptor) this);

    /**
     * Sole constructor.
     */
    protected HTTPTask() { super(); }

    public boolean getBuffer() { return buffer; }
    public void setBuffer(boolean buffer) { this.buffer = buffer; }
    public void setBuffer(String string) { setBuffer(toBoolean(string)); }

    @Override
    public void init() throws BuildException {
        super.init();

        PropertiesImpl properties = new PropertiesImpl();

        MapUtil.copy(getProject().getProperties(), properties);
        properties.configure(this);
    }

    /**
     * Method to allow subclasses to configure the
     * {@link HttpClientBuilder}.
     *
     * @return  The {@link HttpClientBuilder}.
     */
    protected HttpClientBuilder builder() { return builder; }

    @Override
    public void process(HttpRequest request,
                        HttpContext context) throws IOException {
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity =
                ((HttpEntityEnclosingRequest) request).getEntity();

            if (entity != null) {
                if (! entity.isRepeatable()) {
                    if (getBuffer()) {
                        ((HttpEntityEnclosingRequest) request)
                            .setEntity(new BufferedHttpEntity(entity));
                    }
                }
            }
        }

        log(NIL);
        log(context);
        log(NIL);
        log(request);
    }

    @Override
    public void process(HttpResponse response,
                        HttpContext context) throws IOException {
        HttpEntity entity = response.getEntity();

        if (entity != null) {
            if (! entity.isRepeatable()) {
                if (getBuffer()) {
                    response.setEntity(new BufferedHttpEntity(entity));
                }
            }
        }

        log(NIL);
        log(context);
        log(NIL);
        log(response);
    }

    /**
     * See {@link #log(String)}.
     *
     * @param   context         The {@link HttpContext} to log.
     */
    protected void log(HttpContext context) {
        log(new MapTableModel(new BeanMap(context)));
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

        if (entity == null) {
            if (message instanceof HttpEntityEnclosingRequest) {
                entity = ((HttpEntityEnclosingRequest) message).getEntity();
            }
        }

        if (entity == null) {
            if (message instanceof HttpResponse) {
                entity = ((HttpResponse) message).getEntity();
            }
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
            if (entity.isRepeatable()) {
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
            } else {
                log(String.valueOf(entity));
            }
        }
    }

    /**
     * Abstract {@link.uri http://ant.apache.org/ Ant} base
     * {@link org.apache.tools.ant.Task} for DELETE, GET, POST, and PUT
     * operations.
     *
     * {@bean.info}
     */
    protected static abstract class Request extends HTTPTask {
        private PropertiesImpl properties = null;
        private URIBuilder builder = new URIBuilder();
        private final List<NameValuePairImpl> headers = new ArrayList<>();
        private String content = null;

        protected Request() { super(); }

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
                getPrefixedProperties(method + DOT,
                                      getProject().getProperties());

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

        private PropertiesImpl getPrefixedProperties(String prefix,
                                                     Map<?,?> map) {
            PropertiesImpl properties = new PropertiesImpl();

            for (Map.Entry<?,?> entry : map.entrySet()) {
                Object key = entry.getKey();
                String string = (key != null) ? key.toString() : null;

                if ((! isNil(string)) && string.startsWith(prefix)) {
                    properties.put(string.substring(prefix.length()),
                                   entry.getValue());
                }
            }

            return properties;
        }

        /**
         * Method to construct the {@link HTTPTask}-specific
         * {@link HttpUriRequest}.
         *
         * @return      The {@link HttpUriRequest}.
         */
        protected abstract HttpUriRequest request();

        /**
         * Method to configure the {@link HTTPTask} {@link HttpUriRequest}.
         * See {@link #execute()} and {@link #request()}.
         *
         * @param       request         The {@link HttpUriRequest}.
         *
         * @throws      Exception       If an exception is encountered.
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

            try (CloseableHttpClient client = builder().build()) {
                HttpUriRequest request = request();

                configure(request);

                HttpResponse response = client.execute(request);
            } catch (BuildException exception) {
                throw exception;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw new BuildException(throwable);
            }
        }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to DELETE.
     *
     * {@bean.info}
     */
    @AntTask("http-delete")
    public static class Delete extends Request {

        /**
         * Sole constructor.
         */
        public Delete() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpDelete(); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to GET.
     *
     * {@bean.info}
     */
    @AntTask("http-get")
    public static class Get extends Request {

        /**
         * Sole constructor.
         */
        public Get() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpGet(); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to HEAD.
     *
     * {@bean.info}
     */
    @AntTask("http-head")
    public static class Head extends Request {

        /**
         * Sole constructor.
         */
        public Head() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpHead(); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to OPTIONS.
     *
     * {@bean.info}
     */
    @AntTask("http-options")
    public static class Options extends Request {

        /**
         * Sole constructor.
         */
        public Options() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpOptions(); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to PATCH.
     *
     * {@bean.info}
     */
    @AntTask("http-patch")
    public static class Patch extends Request {

        /**
         * Sole constructor.
         */
        public Patch() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpPatch(); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to POST.
     *
     * {@bean.info}
     */
    @AntTask("http-post")
    public static class Post extends Request {

        /**
         * Sole constructor.
         */
        public Post() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpPost(); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to PUT.
     *
     * {@bean.info}
     */
    @AntTask("http-put")
    public static class Put extends Request {

        /**
         * Sole constructor.
         */
        public Put() { super(); }

        @Override
        protected HttpUriRequest request() { return new HttpPut(); }
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
}
