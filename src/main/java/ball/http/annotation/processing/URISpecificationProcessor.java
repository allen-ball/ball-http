/*
 * $Id$
 *
 * Copyright 2016 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.AbstractAnnotationProcessor;
import ball.annotation.processing.For;
import ball.http.annotation.URISpecification;
import ball.http.client.method.AnnotatedHttpUriRequest;
import java.nio.charset.Charset;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.apache.http.client.utils.URIBuilder;

import static ball.util.MapUtil.getByKeyToString;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * {@link URISpecification} annotation {@link Processor}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ URISpecification.class })
public class URISpecificationProcessor extends AbstractAnnotationProcessor {
    private TypeElement supertype = null;

    /**
     * Sole constructor.
     */
    public URISpecificationProcessor() { super(); }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        try {
            supertype = getTypeElementFor(AnnotatedHttpUriRequest.class);
        } catch (Exception exception) {
            print(ERROR, null, exception);
        }
    }

    @Override
    protected void process(RoundEnvironment env,
                           TypeElement annotation,
                           Element element) throws Exception {
        switch (element.getKind()) {
        case CLASS:
            if (isAssignable(element.asType(), supertype.asType())) {
                AnnotationMirror mirror =
                    getAnnotationMirror(element, annotation);
                AnnotationValue value =
                    getByKeyToString(mirror.getElementValues(), "value()");

                if (value != null) {
                    try {
                        new URIBuilder((String) value.getValue());
                    } catch (Exception exception) {
                        print(ERROR,
                              element,
                              element.getKind() + " annotated with "
                              + AT + annotation.getSimpleName()
                              + " but cannot convert `" + value.toString()
                              + "' to " + URIBuilder.class.getName());
                    }
                }

                AnnotationValue charset =
                    getByKeyToString(mirror.getElementValues(), "charset()");

                if (charset != null) {
                    try {
                        Charset.forName((String) charset.getValue());
                    } catch (Exception exception) {
                        print(ERROR,
                              element,
                              element.getKind() + " annotated with "
                              + AT + annotation.getSimpleName()
                              + " but cannot convert `" + charset.toString()
                              + "' to " + Charset.class.getName());
                    }
                }

                AnnotationValue scheme =
                    getByKeyToString(mirror.getElementValues(), "scheme()");
                AnnotationValue userInfo =
                    getByKeyToString(mirror.getElementValues(), "userInfo()");
                AnnotationValue host =
                    getByKeyToString(mirror.getElementValues(), "host()");
                AnnotationValue port =
                    getByKeyToString(mirror.getElementValues(), "port()");

                if (port != null) {
                    try {
                        if (((Integer) port.getValue()) < 0) {
                            throw new IllegalArgumentException();
                        }
                    } catch (Exception exception) {
                        print(ERROR,
                              element,
                              element.getKind() + " annotated with "
                              + AT + annotation.getSimpleName()
                              + " but cannot convert `" + port.toString()
                              + "' to a positve " + Integer.class.getName());
                    }
                }

                AnnotationValue path =
                    getByKeyToString(mirror.getElementValues(), "path()");
            } else {
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but is not a subclass of "
                      + supertype.getQualifiedName());
            }
            break;

        default:
            break;
        }
    }
}
