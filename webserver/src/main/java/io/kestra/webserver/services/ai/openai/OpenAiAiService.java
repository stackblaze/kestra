package io.kestra.webserver.services.ai.openai;

import java.util.List;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.NamespaceContextTool;
import io.kestra.webserver.services.posthog.PosthogService;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.micronaut.core.annotation.Nullable;

public class OpenAiAiService extends AiService<OpenAiConfiguration> {
    public static final String TYPE = "openai";

    public OpenAiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, @Nullable NamespaceContextTool namespaceContextTool,
        String displayName, List<ChatModelListener> listeners, OpenAiConfiguration openAiConfiguration, ExpressionContextService expressionContextService,
        FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, openAiConfiguration,
            expressionContextService, flowParsingService
        );
    }

    @Override
    protected String baseUrl() {
        return getAiConfiguration().baseUrl();
    }

    @Override
    public ChatModel chatModel(List<ChatModelListener> listeners) {
        OpenAiConfiguration configuration = getAiConfiguration();
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
            .baseUrl(configuration.baseUrl())
            .listeners(listeners)
            .modelName(configuration.modelName())
            .apiKey(configuration.apiKey())
            .temperature(configuration.temperature())
            .topP(configuration.topP())
            .maxTokens(configuration.maxOutputTokens())
            .reasoningEffort(reasoningEffort())
            .logRequests(configuration.logRequests())
            .logResponses(configuration.logResponses())
            .customHeaders(configuration.customHeaders())
            .timeout(configuration.timeout());
        pemHttpClientBuilder(configuration.clientPem(), configuration.caPem()).ifPresent(builder::httpClientBuilder);
        return builder.build();
    }

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        OpenAiConfiguration configuration = getAiConfiguration();
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
            .baseUrl(configuration.baseUrl())
            .listeners(listeners)
            .modelName(configuration.modelName())
            .apiKey(configuration.apiKey())
            .temperature(configuration.temperature())
            .topP(configuration.topP())
            .maxTokens(configuration.maxOutputTokens())
            .reasoningEffort(reasoningEffort())
            .logRequests(configuration.logRequests())
            .logResponses(configuration.logResponses())
            .customHeaders(configuration.customHeaders())
            .timeout(configuration.timeout());
        pemHttpClientBuilder(configuration.clientPem(), configuration.caPem()).ifPresent(builder::httpClientBuilder);
        return builder.build();
    }

    private String reasoningEffort() {
        OpenAiConfiguration configuration = getAiConfiguration();
        return configuration.thinkingEnabled() && configuration.thinkingEffort() != null ? configuration.thinkingEffort().value() : null;
    }
}
