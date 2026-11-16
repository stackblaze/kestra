package io.kestra.webserver.services.ai.openai;

import java.time.Duration;
import java.util.Map;

import io.kestra.webserver.services.ai.AiConfiguration;
import io.kestra.webserver.services.ai.ThinkingEffort;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.swagger.v3.oas.annotations.media.Schema;

public record OpenAiConfiguration(
    @Schema(description = "Base URL of any OpenAI-compatible API, including the version prefix (e.g. `/v1`)", nullable = true)
    @Nullable String baseUrl,
    @Nullable String apiKey,
    @Bindable(defaultValue = "gpt-4o-mini") String modelName,
    @Bindable(defaultValue = "0.7") Double temperature,
    @Nullable Double topP,
    @Nullable String clientPem,
    @Schema(description = "Not required but can be useful to add further trust", nullable = true)
    @Nullable String caPem,
    @Bindable(defaultValue = "8000") int maxOutputTokens,
    @Bindable(defaultValue = "false") boolean logRequests,
    @Bindable(defaultValue = "false") boolean logResponses,
    @Bindable(defaultValue = "false") boolean thinkingEnabled,
    @Nullable ThinkingEffort thinkingEffort,
    @Nullable
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT, keyFormat = StringConvention.RAW) Map<String, String> customHeaders,
    Duration timeout) implements AiConfiguration {
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    public OpenAiConfiguration {
        if (baseUrl == null || baseUrl.isBlank())
            baseUrl = DEFAULT_BASE_URL;
        if (modelName == null)
            modelName = "gpt-4o-mini";
        if (temperature == null)
            temperature = 0.7;
        if (maxOutputTokens == 0)
            maxOutputTokens = 8000;
    }

    @Override
    public String type() {
        return "openai";
    }
}
