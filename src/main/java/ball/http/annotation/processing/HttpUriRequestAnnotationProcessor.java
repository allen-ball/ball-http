/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.AbstractAnnotationProcessor;
import ball.annotation.processing.For;
import ball.http.annotation.HttpMessageHeader;
import ball.http.annotation.URIHost;
import ball.http.annotation.URIPath;
import ball.http.annotation.URIPathParameter;
import ball.http.annotation.URIQueryParameter;
import ball.http.annotation.URIScheme;
import ball.http.annotation.URIUserInfo;
import ball.http.client.method.AnnotatedHttpUriRequest;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static ball.util.MapUtil.getByKeyToString;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Abstract base class for {@link AnnotatedHttpUriRequest} field and method
 * annotations {@link Processor}s.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public abstract class HttpUriRequestAnnotationProcessor
                      extends AbstractAnnotationProcessor {
    protected TypeElement supertype = null;

    /**
     * Sole constructor.
     */
    protected HttpUriRequestAnnotationProcessor() { super(); }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        try {
            supertype = getTypeElementFor(AnnotatedHttpUriRequest.class);
        } catch (Exception exception) {
            print(ERROR, null, exception);
        }
    }

    /**
     * {@link AnnotatedHttpUriRequest} field and method annotations
     * {@link Processor}.
     */
    @ServiceProviderFor({ Processor.class })
    @For({ URIScheme.class, URIHost.class, URIUserInfo.class, URIPath.class })
    public static class Member extends HttpUriRequestAnnotationProcessor {

        /**
         * Sole constructor.
         */
        public Member() { super(); }

        @Override
        protected void process(RoundEnvironment env,
                               TypeElement annotation,
                               Element element) throws Exception {
            TypeElement type = (TypeElement) element.getEnclosingElement();

            if (! isAssignable(type.asType(), supertype.asType())) {
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but is not declared in a subclass of "
                      + supertype.getQualifiedName());
            }
        }
    }

    /**
     * {@link AnnotatedHttpUriRequest} field and method annotations
     * {@link Processor} where the name may be specified.
     */
    @ServiceProviderFor({ Processor.class })
    @For({
            URIPathParameter.class, URIQueryParameter.class,
                HttpMessageHeader.class
        })
    public static class Named extends Member {

        /**
         * Sole constructor.
         */
        public Named() { super(); }

        @Override
        protected void process(RoundEnvironment env,
                               TypeElement annotation,
                               Element element) throws Exception {
            super.process(env, annotation, element);

            AnnotationMirror mirror =
                getAnnotationMirror(element, annotation);
            AnnotationValue name =
                getByKeyToString(mirror.getElementValues(), "name()");

            switch (element.getKind()) {
            case FIELD:
                break;

            case METHOD:
                if (name == null) {
                    if (getPropertyName((ExecutableElement) element) == null) {
                        print(ERROR,
                              element,
                              element.getKind() + " annotated with "
                              + AT + annotation.getSimpleName()
                              + " but cannot determine property name");
                    }
                }
                break;

            default:
                break;
            }
        }
    }
}
