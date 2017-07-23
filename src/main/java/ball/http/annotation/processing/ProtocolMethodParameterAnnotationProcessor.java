/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.processing.AbstractAnnotationProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Abstract HTTP Message parameter {@link java.lang.annotation.Annotation}
 * {@link javax.annotation.processing.Processor}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
public abstract class ProtocolMethodParameterAnnotationProcessor
                      extends AbstractAnnotationProcessor {

    /**
     * Sole constructor.
     */
    protected ProtocolMethodParameterAnnotationProcessor() { super(); }

    @Override
    public void process(RoundEnvironment roundEnv,
                        TypeElement annotation,
                        Element element) throws Exception {
        switch (element.getKind()) {
        case PARAMETER:
            ExecutableElement method =
                (ExecutableElement) element.getEnclosingElement();
            TypeElement type = (TypeElement) method.getEnclosingElement();
            break;

        default:
            print(ERROR,
                  element,
                  element.getKind() + " annotated with "
                  + AT + annotation.getSimpleName()
                  + " but is not a PARAMETER");
            break;
        }
    }
}
