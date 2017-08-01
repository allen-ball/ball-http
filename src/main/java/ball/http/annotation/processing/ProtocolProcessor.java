/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.AbstractAnnotationProcessor;
import ball.annotation.processing.For;
import ball.http.annotation.Protocol;
import java.nio.charset.Charset;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static ball.util.MapUtil.getByKeyToString;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * {@link Protocol} annotation {@link Processor}.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ Protocol.class })
public class ProtocolProcessor extends AbstractAnnotationProcessor {

    /**
     * Sole constructor.
     */
    public ProtocolProcessor() { super(); }

    @Override
    protected void process(RoundEnvironment env,
                           TypeElement annotation,
                           Element element) throws Exception {
        switch (element.getKind()) {
        case INTERFACE:
            break;

        default:
            print(ERROR,
                  element,
                  element.getKind() + " annotated with "
                  + AT + annotation.getSimpleName()
                  + " but is not an INTERFACE");
            break;
        }

        AnnotationMirror mirror = getAnnotationMirror(element, annotation);
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
    }
}
