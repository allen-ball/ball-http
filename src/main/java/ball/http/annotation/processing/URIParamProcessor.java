/*
 * $Id$
 *
 * Copyright 2017, 2018 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.For;
import ball.http.annotation.URIParam;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * {@link URIParam} parameter {@link java.lang.annotation.Annotation}
 * {@link Processor}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ URIParam.class })
public class URIParamProcessor
             extends ProtocolMethodParameterAnnotationProcessor {

    /**
     * Sole constructor.
     */
    public URIParamProcessor() { super(); }

    @Override
    public void process(RoundEnvironment roundEnv,
                        TypeElement annotation,
                        Element element) throws Exception {
        super.process(roundEnv, annotation, element);
    }
}
