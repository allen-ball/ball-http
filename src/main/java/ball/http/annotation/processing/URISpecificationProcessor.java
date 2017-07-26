/*
 * $Id$
 *
 * Copyright 2016, 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.AbstractAnnotationProcessor;
import ball.annotation.processing.For;
import ball.http.annotation.URISpecification;
import java.nio.charset.Charset;
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

    /**
     * Sole constructor.
     */
    public URISpecificationProcessor() { super(); }

    @Override
    protected void process(RoundEnvironment env,
                           TypeElement annotation,
                           Element element) throws Exception {
        AnnotationMirror mirror = getAnnotationMirror(element, annotation);
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

        switch (element.getKind()) {
        case INTERFACE:
            break;

        case METHOD:
            switch (element.getEnclosingElement().getKind()) {
            case INTERFACE:
                break;

            default:
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but is not a " + element.getKind()
                      + " of an INTERFACE");
                break;
            }
            break;

        default:
            print(ERROR,
                  element,
                  element.getKind() + " annotated with "
                  + AT + annotation.getSimpleName()
                  + " but is not an INTERFACE or INTERFACE METHOD");
            break;
        }
    }
}
