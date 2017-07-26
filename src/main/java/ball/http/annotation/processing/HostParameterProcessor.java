/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.For;
import ball.http.annotation.HostParameter;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * {@link HostParameter} parameter {@link java.lang.annotation.Annotation}
 * {@link Processor}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ HostParameter.class })
public class HostParameterProcessor
             extends ProtocolMethodParameterAnnotationProcessor {

    /**
     * Sole constructor.
     */
    public HostParameterProcessor() { super(); }

    @Override
    public void process(RoundEnvironment roundEnv,
                        TypeElement annotation,
                        Element element) throws Exception {
        super.process(roundEnv, annotation, element);
    }
}
