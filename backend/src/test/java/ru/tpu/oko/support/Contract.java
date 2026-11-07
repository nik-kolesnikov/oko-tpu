package ru.tpu.oko.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolverFactory;
import com.atlassian.oai.validator.report.ValidationReport;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Проверка «ответ проходит схему» по contracts/oko-openapi.yaml. Путь к спецификации задаёт surefire
 * (oko.contract.path), при запуске из IDE — относительно backend/.
 */
public final class Contract {

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(specUrl())
            .withBasePathOverride("/api/v1")
            // Схемы с allOf (CurrentUser, ApiKeyIssued, ProductPage…) при проверке additionalProperties дают ложные
            // ошибки — известное ограничение валидатора. Лишние поля в ответах ловит ревью и сверка со springdoc.
            .withLevelResolver(LevelResolverFactory.withAdditionalPropertiesIgnored())
            .build();

    private Contract() {
    }

    public static ResultMatcher matchesContract() {
        return result -> {
            MockHttpServletRequest request = result.getRequest();
            MockHttpServletResponse response = result.getResponse();
            SimpleResponse.Builder builder = SimpleResponse.Builder.status(response.getStatus());
            String body = response.getContentAsString(StandardCharsets.UTF_8);
            if (!body.isEmpty()) {
                builder.withBody(body);
            }
            for (String header : response.getHeaderNames()) {
                builder.withHeader(header, response.getHeaders(header));
            }
            ValidationReport report = VALIDATOR.validateResponse(request.getRequestURI(),
                    Request.Method.valueOf(request.getMethod()), builder.build());
            if (report.hasErrors()) {
                throw new AssertionError("Ответ " + request.getMethod() + " " + request.getRequestURI() + " ("
                        + response.getStatus() + ") не соответствует contracts/oko-openapi.yaml:\n"
                        + report.getMessages().stream()
                                .filter(m -> m.getLevel() == ValidationReport.Level.ERROR)
                                .map(m -> " - " + m.getKey() + ": " + m.getMessage())
                                .collect(Collectors.joining("\n"))
                        + "\nТело: " + body);
            }
        };
    }

    private static String specUrl() {
        String path = System.getProperty("oko.contract.path", "../contracts/oko-openapi.yaml");
        return Path.of(path).toAbsolutePath().normalize().toUri().toString();
    }
}
