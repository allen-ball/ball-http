/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.AbstractAnnotationProcessor;
import ball.annotation.processing.For;
import ball.http.annotation.Header;
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
 * {@link Header} parameter {@link java.lang.annotation.Annotation}
 * {@link Processor}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ Header.class })
public class HeaderProcessor extends AbstractAnnotationProcessor {

    /**
     * Sole constructor.
     */
    public HeaderProcessor() { super(); }

    @Override
    public void process(RoundEnvironment roundEnv,
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

        case PARAMETER:
            switch (element.getEnclosingElement().getEnclosingElement().getKind()) {
            case INTERFACE:
                break;

            default:
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but is not a " + element.getKind()
                      + " of an INTERFACE METHOD");
                break;
            }
            break;

        default:
            print(ERROR,
                  element,
                  element.getKind() + " annotated with "
                  + AT + annotation.getSimpleName()
                  + " but is not an INTERFACE, INTERFACE METHOD, or INTERFACE METHOD PARAMETER");
            break;
        }

        AnnotationMirror mirror = getAnnotationMirror(element, annotation);
        AnnotationValue name =
            getByKeyToString(mirror.getElementValues(), "name()");
        AnnotationValue value =
            getByKeyToString(mirror.getElementValues(), "value()");

        switch (element.getKind()) {
        case INTERFACE:
        case METHOD:
            if (! isSpecified(name)) {
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but no name() specified");
            }

            if (! isSpecified(value)) {
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but no value() specified");
            }
            break;

        case PARAMETER:
            if (isSpecified(name) && isSpecified(value)) {
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but both name() and value() specified on "
                      + element.getKind());
            }

            if (! (isSpecified(name) || isSpecified(value))) {
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but neither name() or value() specified");
            }
            break;

        default:
            break;
        }
    }

    private boolean isSpecified(AnnotationValue value) {
        return (value != null && (! isNil(value.toString())));
    }
}
