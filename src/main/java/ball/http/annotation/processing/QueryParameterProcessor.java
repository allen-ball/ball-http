/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.For;
import ball.http.annotation.QueryParameter;
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
 * {@link QueryParameter} parameter {@link java.lang.annotation.Annotation}
 * {@link Processor}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ QueryParameter.class })
public class QueryParameterProcessor
             extends ProtocolMethodParameterAnnotationProcessor {

    /**
     * Sole constructor.
     */
    public QueryParameterProcessor() { super(); }

    @Override
    public void process(RoundEnvironment roundEnv,
                        TypeElement annotation,
                        Element element) throws Exception {
        super.process(roundEnv, annotation, element);

        AnnotationMirror mirror = getAnnotationMirror(element, annotation);
        AnnotationValue value =
            getByKeyToString(mirror.getElementValues(), "value()");

        if (value != null || isNil(value.toString())) {
            print(ERROR,
                  element,
                  element.getKind() + " annotated with "
                  + AT + annotation.getSimpleName()
                  + " but no value() specified");
        }
    }
}
