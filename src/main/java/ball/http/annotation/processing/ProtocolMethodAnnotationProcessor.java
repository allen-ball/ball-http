/*
 * $Id$
 *
 * Copyright 2017 Allen D. Ball.  All rights reserved.
 */
package ball.http.annotation.processing;

import ball.annotation.ServiceProviderFor;
import ball.annotation.processing.AbstractAnnotationProcessor;
import ball.annotation.processing.For;
import ball.http.annotation.DELETE;
import ball.http.annotation.GET;
import ball.http.annotation.HEAD;
import ball.http.annotation.OPTIONS;
import ball.http.annotation.PATCH;
import ball.http.annotation.POST;
import ball.http.annotation.PUT;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashSet;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static ball.http.ProtocolInvocationHandler.SUPPORTED_RETURN_TYPES;
import static ball.util.MapUtil.getByKeyToString;
import static ball.util.StringUtil.isNil;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * HTTP Message {@link Annotation} {@link Processor}
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision$
 */
@ServiceProviderFor({ Processor.class })
@For({
        DELETE.class, GET.class, HEAD.class, OPTIONS.class,
            PATCH.class, POST.class, PUT.class
      })
public class ProtocolMethodAnnotationProcessor
             extends AbstractAnnotationProcessor {

    /**
     * Sole constructor.
     */
    public ProtocolMethodAnnotationProcessor() { super(); }

    @Override
    public void process(RoundEnvironment roundEnv,
                        TypeElement annotation,
                        Element element) throws Exception {
        switch (element.getKind()) {
        case METHOD:
            TypeElement type = (TypeElement) element.getEnclosingElement();

            switch (type.getKind()) {
            case INTERFACE:
                LinkedHashSet<Class<? extends Annotation>> set =
                    new LinkedHashSet<>();

                for (Class<? extends Annotation> cls :
                         getSupportedAnnotationTypeList()) {
                    if (element.getAnnotation(cls) != null) {
                        set.add(cls);
                    }
                }

                switch (set.size()) {
                case 0:
                    throw new IllegalStateException();
                    /* break; */

                case 1:
                    AnnotationMirror mirror =
                        getAnnotationMirror(element, annotation);
                    AnnotationValue value =
                        getByKeyToString(mirror.getElementValues(), "value()");
                    break;

                default:
                print(ERROR,
                      element,
                      element.getKind() + " may only be annotated with one of "
                      + toString(set));
                    break;
                }
                break;

            default:
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " but is not a METHOD of an INTERFACE");
                break;
            }

            TypeMirror mirror = ((ExecutableElement) element).getReturnType();

            if (! isSupported(mirror)) {
                print(ERROR,
                      element,
                      element.getKind() + " annotated with "
                      + AT + annotation.getSimpleName()
                      + " returns unsupported type: "
                      + mirror);
            }
            break;
        }
    }

    private String toString(Collection<Class<? extends Annotation>> collection) {
        LinkedHashSet<String> set = new LinkedHashSet<>();

        for (Class<? extends Annotation> annotation : collection) {
            set.add(AT + annotation.getSimpleName());
        }

        return set.toString();
    }

    private boolean isSupported(TypeMirror mirror) {
        boolean isSupported = false;

        for (Class<?> type : SUPPORTED_RETURN_TYPES) {
            isSupported |= isSameType(mirror, type);

            if (isSupported) {
                break;
            }
        }

        return isSupported;
    }
}
