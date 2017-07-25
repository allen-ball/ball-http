/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.For;
import ball.http.annotation.URIParameter;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * {@link URIParameter} parameter {@link java.lang.annotation.Annotation}
 * {@link Processor}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ URIParameter.class })
public class URIParameterProcessor
             extends ProtocolMethodParameterAnnotationProcessor {

    /**
     * Sole constructor.
     */
    public URIParameterProcessor() { super(); }

    @Override
    public void process(RoundEnvironment roundEnv,
                        TypeElement annotation,
                        Element element) throws Exception {
        super.process(roundEnv, annotation, element);
    }
}
