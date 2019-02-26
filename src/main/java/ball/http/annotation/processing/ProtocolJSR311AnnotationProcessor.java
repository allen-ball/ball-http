/*
 * $Id$
 *
 * Copyright 2017 - 2019 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

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
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
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
