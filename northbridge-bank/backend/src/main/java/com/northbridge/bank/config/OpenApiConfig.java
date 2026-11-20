package com.northbridge.bank.config;

import com.northbridge.bank.api.ApiError;
import com.northbridge.bank.dispatch.BankOperation;
import com.northbridge.bank.dispatch.OperationHandler;
import com.northbridge.bank.dispatch.OperationRegistry;
import com.northbridge.bank.dispatch.OperationRegistry.RegisteredOperation;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Type;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

/**
 * There is exactly one physical route ({@code POST /api/execute}), so springdoc's normal
 * controller scan can't produce a useful spec - it only sees one generic envelope. This
 * synthesizes one <b>virtual</b> documentation path per entry in {@link OperationRegistry},
 * with request/response schemas read from the handler's actual DTOs and the security policy
 * read from its {@link BankOperation} annotation. Routing is untouched: callers still POST to
 * {@code /api/execute}, and because the catalog is generated from the registry at startup, it
 * cannot drift out of sync with what the dispatcher actually enforces.
 */
@Configuration
public class OpenApiConfig {

    private static final String REAL_PATH = "POST /api/execute";

    @Bean
    OpenAPI bankOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Northbridge Bank API")
                .version("1.0.0")
                .description("""
                        This API has exactly one physical business endpoint: `%s` (envelope \
                        `{operation, payload, meta}`), plus `POST /api/execute/batch` for up to \
                        10 read operations per call. The paths below are **virtual** - one per \
                        registered operation - generated from the server's operation registry so \
                        each operation's real request/response shape and security policy is \
                        documented. To call any of them, POST the shown request body to `%s`, \
                        not to the path shown in the path bar.""".formatted(REAL_PATH, REAL_PATH)));
    }

    @Bean
    OpenApiCustomizer operationCatalogCustomizer(OperationRegistry registry) {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            if (openApi.getPaths() == null) {
                openApi.setPaths(new Paths());
            }
            Schema<?> errorRef = resolveAndRegister(ApiError.class, openApi.getComponents());
            for (RegisteredOperation op : registry.all()) {
                openApi.getPaths().addPathItem("/api/execute/" + op.name(), buildPathItem(op, openApi.getComponents(), errorRef));
            }
        };
    }

    private PathItem buildPathItem(RegisteredOperation op, Components components, Schema<?> errorRef) {
        BankOperation policy = op.policy();
        Schema<?> inputSchema = resolveAndRegister(op.handler().inputType(), components);
        Schema<?> outputSchema = resolveAndRegister(resolveOutputType(op.handler()), components);

        Schema<?> requestSchema = new ObjectSchema()
                .addProperty("operation", new StringSchema()._enum(List.of(op.name())).example(op.name()))
                .addProperty("payload", inputSchema)
                .addProperty("meta", new MapSchema().additionalProperties(new StringSchema())
                        .description("Optional non-business metadata (channel, client version); never used for authorization"))
                .required(List.of("operation"));

        Schema<?> responseSchema = new ObjectSchema()
                .addProperty("operation", new StringSchema().example(op.name()))
                .addProperty("success", new BooleanSchema())
                .addProperty("data", outputSchema)
                .addProperty("error", errorRef)
                .addProperty("correlationId", new StringSchema());

        String tag = op.name().substring(0, op.name().indexOf('.'));
        Operation operation = new Operation()
                .operationId(op.name())
                .addTagsItem(tag)
                .summary(op.name())
                .description(describe(policy))
                .requestBody(new RequestBody().required(true)
                        .content(new Content().addMediaType("application/json", new MediaType().schema(requestSchema))))
                .responses(new ApiResponses()
                        .addApiResponse("200", envelopeResponse(responseSchema, "Success, or a handled failure (see `success` / `error`)"))
                        .addApiResponse("400", errorResponse(errorRef, "Malformed envelope or payload failed validation"))
                        .addApiResponse("403", errorResponse(errorRef,
                                "Unknown operation or missing scope/role - both return the same response so operations cannot be enumerated"))
                        .addApiResponse("404", errorResponse(errorRef, "Record does not exist or is not owned by the caller"))
                        .addApiResponse("422", errorResponse(errorRef, "Business rule violation"))
                        .addApiResponse("429", errorResponse(errorRef, "Rate limit exceeded for this user and operation")));
        operation.addExtension("x-bank-scopes", policy.scopes());
        operation.addExtension("x-bank-any-role", policy.anyRole());
        operation.addExtension("x-bank-audit", policy.audit());
        operation.addExtension("x-bank-rate-per-minute", policy.ratePerMinute());

        return new PathItem().post(operation);
    }

    private String describe(BankOperation policy) {
        String role = policy.anyRole().length == 0
                ? "any authenticated user"
                : "any role of " + List.of(policy.anyRole());
        return "Virtual documentation entry - actually invoked by POSTing this body to `%s`. Requires scopes %s and %s. Rate limit: %d/min per user.%s"
                .formatted(REAL_PATH, List.of(policy.scopes()), role, policy.ratePerMinute(),
                        policy.audit() ? " Audited." : "");
    }

    private ApiResponse envelopeResponse(Schema<?> schema, String description) {
        return new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private ApiResponse errorResponse(Schema<?> errorRef, String description) {
        Schema<?> envelope = new ObjectSchema()
                .addProperty("operation", new StringSchema())
                .addProperty("success", new BooleanSchema())
                .addProperty("error", errorRef)
                .addProperty("correlationId", new StringSchema());
        return envelopeResponse(envelope, description);
    }

    /** Resolves the handler's output type, preserving generics (e.g. {@code List<AccountSummary>}). */
    private Type resolveOutputType(OperationHandler<?, ?> handler) {
        Class<?> targetClass = AopUtils.getTargetClass(handler);
        return ResolvableType.forClass(targetClass).as(OperationHandler.class).getGeneric(1).getType();
    }

    private Schema<?> resolveAndRegister(Type type, Components components) {
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
        resolved.referencedSchemas.forEach(components::addSchemas);
        return resolved.schema;
    }
}
