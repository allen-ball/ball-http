package ball.http.ant.taskdefs;
/*-
 * ##########################################################################
 * Web API Client (HTTP) Utilities
 * %%
 * Copyright (C) 2016 - 2022 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
import ball.activation.ReaderWriterDataSource;
import ball.swing.table.MapTableModel;
import ball.util.PropertiesImpl;
import ball.util.ant.taskdefs.AnnotatedAntTask;
import ball.util.ant.taskdefs.AntTask;
import ball.util.ant.taskdefs.ClasspathDelegateAntTask;
import ball.util.ant.taskdefs.ConfigurableAntTask;
import ball.util.ant.types.StringAttributeType;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.commons.beanutils.BeanMap;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.ClasspathUtils;

import static ball.activation.ReaderWriterDataSource.CONTENT_TYPE;
import static lombok.AccessLevel.PROTECTED;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Abstract {@link.uri http://ant.apache.org/ Ant} base {@link Task} for web
 * API client tasks.
 *
 * {@ant.task}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
@NoArgsConstructor(access = PROTECTED)
public abstract class HTTPTask extends Task implements AnnotatedAntTask, ClasspathDelegateAntTask, ConfigurableAntTask,
                                                       HttpRequestInterceptor, HttpResponseInterceptor {
    private static final String DOT = ".";

    private final HttpClientBuilder builder =
        HttpClientBuilder.create()
        .addRequestInterceptorLast(this)
        .addResponseInterceptorLast(this);

    @Getter @Setter @Accessors(chain = true, fluent = true)
    private ClasspathUtils.Delegate delegate = null;
    @Getter @Setter
    private boolean buffer = false;

    @Override
    public void init() throws BuildException {
        super.init();
        ClasspathDelegateAntTask.super.init();
        ConfigurableAntTask.super.init();
    }

    @Override
    public void execute() throws BuildException {
        super.execute();
        AnnotatedAntTask.super.execute();
    }

    /**
     * Method to allow subclasses to configure the
     * {@link HttpClientBuilder}.
     *
     * @return  The {@link HttpClientBuilder}.
     */
    protected HttpClientBuilder builder() { return builder; }

    @Override
    public void process(HttpRequest request, EntityDetails details, HttpContext context) throws IOException {
        if (request instanceof HttpEntityContainer) {
            HttpEntity entity = ((HttpEntityContainer) request).getEntity();

            if (entity != null) {
                if (! entity.isRepeatable()) {
                    if (isBuffer()) {
                        ((HttpEntityContainer) request).setEntity(new BufferedHttpEntity(entity));
                    }
                }
            }
        }

        log();
        log(context);
        log();
        log(request);
    }

    @Override
    public void process(HttpResponse response, EntityDetails details, HttpContext context) throws IOException {
        if (response instanceof HttpEntityContainer) {
            HttpEntity entity = ((HttpEntityContainer) response).getEntity();

            if (entity != null) {
                if (! entity.isRepeatable()) {
                    if (isBuffer()) {
                        ((HttpEntityContainer) response).setEntity(new BufferedHttpEntity(entity));
                    }
                }
            }
        }

        log();
        log(context);
        log();
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
        /*
         * if (message instanceof HttpRequest) {
         *     log(String.valueOf(((HttpRequest) message).getRequestLine()));
         * }
         *
         * if (message instanceof HttpResponse) {
         *     log(String.valueOf(((HttpResponse) message).getStatusLine()));
         * }
         */
        for (Header header : message.getHeaders()) {
            log(String.valueOf(header));
        }

        log(getContentType(message), getHttpEntity(message));
    }

    private String getContentType(HttpMessage message) {
        return (message.containsHeader(CONTENT_TYPE) ? message.getFirstHeader(CONTENT_TYPE).getValue() : null);
    }

    private HttpEntity getHttpEntity(HttpMessage message) {
        HttpEntity entity = null;

        if (entity == null) {
            if (message instanceof HttpEntityContainer) {
                entity = ((HttpEntityContainer) message).getEntity();
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
                ReaderWriterDataSource ds = new ReaderWriterDataSource(null, type);

                try (OutputStream out = ds.getOutputStream()) {
                    entity.writeTo(out);
                    out.flush();

                    String string = ds.toString();

                    if (! isEmpty(string)) {
                        log();
                        log(string);
                    }
                } catch (IOException exception) {
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
     * {@ant.task}
     */
    @NoArgsConstructor(access = PROTECTED)
    protected static abstract class Request extends HTTPTask {
        private PropertiesImpl properties = null;
        private URIBuilder builder = new URIBuilder();
        private final List<NameValuePairImpl> headers = new ArrayList<>();

        @Getter @Setter
        private String content = null;

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
            setContent((isEmpty(getContent()) ? EMPTY : getContent()) + text);
        }

        @Override
        public void init() throws BuildException {
            super.init();

            String method = getClass().getSimpleName().toUpperCase();

            properties = getPrefixedProperties(method + DOT, getProject().getProperties());

            try {
                if (properties.containsKey("uri")) {
                    builder = new URIBuilder(properties.getProperty("uri"));
                }

                properties.configure(builder);

                for (Map.Entry<?,?> entry : getPrefixedProperties("parameter" + DOT, properties).entrySet()) {
                    builder.addParameter(entry.getKey().toString(), entry.getValue().toString());
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

            for (Map.Entry<?,?> entry : map.entrySet()) {
                Object key = entry.getKey();
                String string = (key != null) ? key.toString() : null;

                if ((! isEmpty(string)) && string.startsWith(prefix)) {
                    properties.put(string.substring(prefix.length()), entry.getValue());
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
            ((HttpUriRequestBase) request).setUri(builder.build());

            addHeaders(request, getPrefixedProperties("header" + DOT, properties).entrySet());
            addHeaders(request, headers);

            if (! isEmpty(getContent())) {
                setEntity(request, getContent());
            } else if (! isEmpty(properties.getProperty("content"))) {
                setEntity(request, properties.getProperty("content"));
            }
        }

        private void addHeaders(HttpRequest request, Iterable<? extends Map.Entry<?,?>> iterable) {
            for (Map.Entry<?,?> entry : iterable) {
                request.addHeader(entry.getKey().toString(), entry.getValue().toString());
            }
        }

        private void setEntity(HttpUriRequest request, String content) throws Exception {
            if (! isEmpty(content)) {
                ((HttpEntityContainer) request).setEntity(new StringEntity(content));
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
     * {@ant.task}
     */
    @AntTask("http-delete")
    @NoArgsConstructor @ToString
    public static class Delete extends Request {
        @Override
        protected HttpUriRequest request() { return new HttpDelete(EMPTY); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to GET.
     *
     * {@ant.task}
     */
    @AntTask("http-get")
    @NoArgsConstructor @ToString
    public static class Get extends Request {
        @Override
        protected HttpUriRequest request() { return new HttpGet(EMPTY); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to HEAD.
     *
     * {@ant.task}
     */
    @AntTask("http-head")
    @NoArgsConstructor @ToString
    public static class Head extends Request {
        @Override
        protected HttpUriRequest request() { return new HttpHead(EMPTY); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to OPTIONS.
     *
     * {@ant.task}
     */
    @AntTask("http-options")
    @NoArgsConstructor @ToString
    public static class Options extends Request {
        @Override
        protected HttpUriRequest request() { return new HttpOptions(EMPTY); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to PATCH.
     *
     * {@ant.task}
     */
    @AntTask("http-patch")
    @NoArgsConstructor @ToString
    public static class Patch extends Request {
        @Override
        protected HttpUriRequest request() { return new HttpPatch(EMPTY); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to POST.
     *
     * {@ant.task}
     */
    @AntTask("http-post")
    @NoArgsConstructor @ToString
    public static class Post extends Request {
        @Override
        protected HttpUriRequest request() { return new HttpPost(EMPTY); }
    }

    /**
     * {@link.uri http://ant.apache.org/ Ant}
     * {@link org.apache.tools.ant.Task} to PUT.
     *
     * {@ant.task}
     */
    @AntTask("http-put")
    @NoArgsConstructor @ToString
    public static class Put extends Request {
        @Override
        protected HttpUriRequest request() { return new HttpPut(EMPTY); }
    }

    /**
     * {@link StringAttributeType} implementation that includes
     * {@link NameValuePair}.
     */
    @NoArgsConstructor @ToString
    public static class NameValuePairImpl extends StringAttributeType implements NameValuePair {
    }
}
