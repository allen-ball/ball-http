/*
 * $Id$
 *
 * Copyright 2017, 2018 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.AbstractAnnotationProcessor;
import ball.annotation.processing.For;
import ball.http.annotation.Headers;
import ball.http.annotation.PathParams;
import ball.http.annotation.QueryParams;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static ball.util.MapUtil.getByKeyToString;
import static ball.util.StringUtil.isNil;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Name-value annotation {@link Processor}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ Headers.class, PathParams.class, QueryParams.class })
public class ProtocolNameValueArrayAnnotationProcessor
             extends AbstractAnnotationProcessor {

    /**
     * Sole constructor.
     */
    public ProtocolNameValueArrayAnnotationProcessor() { super(); }

    @Override
    protected void process(RoundEnvironment env,
                           TypeElement annotation,
                           Element element) throws Exception {
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

        AnnotationMirror mirror = getAnnotationMirror(element, annotation);
        AnnotationValue value =
            getByKeyToString(mirror.getElementValues(), "value()");

        for (Object object : (List<?>) value.getValue()) {
            AnnotationMirror nvp =
                (AnnotationMirror) ((AnnotationValue) object).getValue();
            AnnotationValue name =
                getByKeyToString(nvp.getElementValues(), "name()");

            if (! isSpecified(name)) {
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + nvp.getAnnotationType().asElement().getSimpleName()
                      + " but no name() specified");
            }
        }
    }

    private boolean isSpecified(AnnotationValue value) {
        return (value != null && (! isNil((String) value.getValue())));
    }
}
