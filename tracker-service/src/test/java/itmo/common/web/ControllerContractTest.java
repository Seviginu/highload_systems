package itmo.common.web;

import itmo.label.controller.LabelController;
import itmo.project.controller.ProjectController;
import itmo.project.controller.ProjectMemberController;
import itmo.task.controller.TaskController;
import jakarta.validation.constraints.Max;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerContractTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            ProjectController.class,
            ProjectMemberController.class,
            LabelController.class,
            TaskController.class
    );

    @Test
    void shouldReturnReactiveDtoOrCompletionFromEveryEndpoint() {
        CONTROLLERS.stream()
                .flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .forEach(method -> assertThat(method.getReturnType())
                        .as("%s.%s return type", method.getDeclaringClass().getSimpleName(), method.getName())
                        .isIn(Mono.class, Flux.class));
        CONTROLLERS.stream().flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .forEach(method -> {
                    var publisher = (ParameterizedType) method.getGenericReturnType();
                    Type element = publisher.getActualTypeArguments()[0];
                    assertThat(element).isInstanceOf(Class.class);
                    Class<?> elementClass = (Class<?>) element;
                    assertThat(elementClass == Void.class || elementClass.getPackageName().contains(".dto"))
                            .as("Publisher contains a DTO or Void, not a response wrapper").isTrue();
                    if (method.getName().equals("findAll") || method.getName().equals("findFeed")) {
                        assertThat(method.getReturnType()).isEqualTo(Flux.class);
                    }
                });
    }

    @Test
    void shouldNeverExposeJpaEntities() {
        CONTROLLERS.stream()
                .flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
                .forEach(method -> assertThat(containsEntity(method.getGenericReturnType()))
                        .as("%s.%s return type", method.getDeclaringClass().getSimpleName(), method.getName())
                        .isFalse());
    }

    @Test
    void shouldLimitEveryListEndpointToFiftyItems() {
        CONTROLLERS.stream()
                .flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
                .filter(method -> isListResponse(method.getGenericReturnType()))
                .forEach(method -> assertThat(List.of(method.getParameters()))
                        .as("%s.%s limit", method.getDeclaringClass().getSimpleName(), method.getName())
                        .anySatisfy(parameter -> {
                            Max max = parameter.getAnnotation(Max.class);
                            assertThat(max).isNotNull();
                            assertThat(max.value()).isEqualTo(50);
                        }));
    }

    private boolean isListResponse(Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        return parameterizedType.getRawType() == Flux.class;
    }

    private boolean containsEntity(Type type) {
        if (type instanceof Class<?> typeClass) {
            return typeClass.getPackageName().contains(".entity");
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return containsEntity(parameterizedType.getRawType())
                    || Stream.of(parameterizedType.getActualTypeArguments()).anyMatch(this::containsEntity);
        }
        return false;
    }
}
