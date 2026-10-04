package pe.edu.pucp.sisrap.config;

import java.util.Arrays;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestHeader;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI sisrapOpenApi() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("SISRAP API")
                                .version("1.0.0")
                                .description(
                                        "API REST del sistema SISRAP para gestión logística, "
                                        + "planificación y simulación de entregas."
                                )
                                .contact(
                                        new Contact()
                                                .name("Equipo SISRAP")
                                )
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_AUTH,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat(
                                                        "Token de sesión SISRAP"
                                                )
                                                .description(
                                                        "Token devuelto por POST /api/sesiones. "
                                                        + "Swagger lo enviará como "
                                                        + "Authorization: Bearer <token>."
                                                )
                                )
                );
    }

    @Bean
    public OperationCustomizer bearerAuthOperationCustomizer() {

        return (operation, handlerMethod) -> {

            boolean requiereToken =
                    Arrays.stream(
                                    handlerMethod.getMethodParameters()
                            )
                            .map(
                                    parameter ->
                                            parameter.getParameterAnnotation(
                                                    RequestHeader.class
                                            )
                            )
                            .filter(
                                    annotation ->
                                            annotation != null
                            )
                            .anyMatch(annotation -> {

                                String nombre =
                                        !annotation.name().isBlank()
                                                ? annotation.name()
                                                : annotation.value();

                                return "Authorization"
                                        .equalsIgnoreCase(nombre);
                            });

            if (!requiereToken) {
                return operation;
            }

            operation.addSecurityItem(
                    new SecurityRequirement()
                            .addList(BEARER_AUTH)
            );

            if (operation.getParameters() != null) {

                operation.setParameters(
                        operation.getParameters()
                                .stream()
                                .filter(
                                        parameter ->
                                                !"Authorization"
                                                        .equalsIgnoreCase(
                                                                parameter
                                                                        .getName()
                                                        )
                                )
                                .toList()
                );
            }

            return operation;
        };
    }
}