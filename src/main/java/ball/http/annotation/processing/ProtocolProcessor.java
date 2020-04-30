package ball.http.annotation.processing;
/*-
 * ##########################################################################
 * Web API Client (HTTP) Utilities
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2016 - 2020 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
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
import lombok.NoArgsConstructor;
import lombok.ToString;

import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * {@link Protocol} annotation {@link Processor}.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({ Protocol.class })
@NoArgsConstructor @ToString
public class ProtocolProcessor extends AbstractAnnotationProcessor {
    @Override
    protected void process(RoundEnvironment env,
                           TypeElement annotation, Element element) {
        switch (element.getKind()) {
        case INTERFACE:
            break;

        default:
            print(ERROR, element,
                  "%s annotated with @%s but is not a %s",
                  element.getKind(), annotation.getSimpleName(), INTERFACE);
            break;
        }

        AnnotationMirror mirror = getAnnotationMirror(element, annotation);
        AnnotationValue charset =
            mirror.getElementValues().entrySet()
            .stream()
            .filter(t -> t.getKey().toString().equals("charset()"))
            .map(t -> t.getValue())
            .findFirst().get();

        if (charset != null) {
            String string = (String) charset.getValue();

            try {
                Charset.forName(string);
            } catch (Exception exception) {
                print(ERROR, element,
                      "%s annotated with @%s but cannot convert '%s' to %s",
                      element.getKind(), annotation.getSimpleName(),
                      string, Charset.class.getName());
            }
        }
    }
}
