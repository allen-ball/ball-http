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
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static javax.tools.Diagnostic.Kind.ERROR;
import static lombok.AccessLevel.PROTECTED;

/**
 * Abstract base class for {@link javax.ws.rs JSR 311} {@link Annotation}
 * {@link Processor} for {@link Protocol} interface methods.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@NoArgsConstructor(access = PROTECTED)
public abstract class ProtocolJSR311AnnotationProcessor
                      extends AbstractAnnotationProcessor {
    @Override
    public void process(RoundEnvironment roundEnv,
                        TypeElement annotation,
                        Element element) throws Exception {
        switch (element.getKind()) {
        case METHOD:
            TypeElement type = (TypeElement) element.getEnclosingElement();

            switch (type.getKind()) {
            case INTERFACE:
                if (type.getAnnotation(Protocol.class) != null) {
                    check(annotation, type, (ExecutableElement) element);
                }
                break;

            default:
                break;
            }
            break;
        }
    }

    /**
     * Method to check an annotated interface method.
     *
     * @param   annotation      The annotation being processed.
     * @param   type            The interface containing the method.
     * @param   method          The annotated method.
     */
    protected abstract void check(TypeElement annotation,
                                  TypeElement type, ExecutableElement method);

    /**
     * {@link javax.ws.rs JSR 311} method {@link Annotation}
     * {@link Processor}.
     */
    @ServiceProviderFor({ Processor.class })
    @For({
            DELETE.class, GET.class, HEAD.class, OPTIONS.class,
                PATCH.class, POST.class, PUT.class
          })
    @NoArgsConstructor @ToString
    public static class Method extends ProtocolJSR311AnnotationProcessor {
        @Override
        protected void check(TypeElement annotation,
                             TypeElement type, ExecutableElement method) {
            Set<Class<? extends Annotation>> set =
                getSupportedAnnotationTypeList().stream()
                .filter(t -> method.getAnnotation(t) != null)
                .collect(Collectors.toSet());

            switch (set.size()) {
            case 0:
                throw new IllegalStateException();
                /* break; */

            case 1:
                AnnotationMirror mirror =
                    getAnnotationMirror(method, annotation);
                Optional<? extends AnnotationValue> value =
                    mirror.getElementValues().entrySet()
                    .stream()
                    .filter(t -> t.getKey().toString().equals("value()"))
                    .map(t -> t.getValue())
                    .findFirst();
                break;

            default:
                print(ERROR,
                      method,
                      method.getKind() + " may only be annotated with one of "
                      + toString(set));
                break;
            }
        }

        private String toString(Set<Class<? extends Annotation>> set) {
            return (set.stream()
                    .map(t -> AT + t.getSimpleName())
                    .collect(Collectors.toSet())
                    .toString());
        }
    }
}
