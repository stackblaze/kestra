package io.kestra.webserver.services.ai.openai;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.AiServiceManager;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@WireMockTest(httpPort = OpenAiAiServiceTest.MODEL_API_PORT)
@Property(name = "kestra.ai.providers[0].id", value = OpenAiAiServiceTest.PROVIDER_ID)
@Property(name = "kestra.ai.providers[0].type", value = "openai")
@Property(name = "kestra.ai.providers[0].configuration.base-url", value = "http://localhost:" + OpenAiAiServiceTest.MODEL_API_PORT + "/v1")
@Property(name = "kestra.ai.providers[0].configuration.model-name", value = "my-local-model")
@Property(name = "kestra.ai.providers[0].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[0].configuration.custom-headers.X-Gateway-Route", value = "internal")
class OpenAiAiServiceTest {
    static final int MODEL_API_PORT = 28185;
    static final String PROVIDER_ID = "openai-compatible";

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private static final String RESPONSE = """
        {"id":"1","object":"chat.completion","created":0,"model":"my-local-model",\
        "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],\
        "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}""";

    private static final String STREAMING_RESPONSE = """
        data: {"id":"1","object":"chat.completion.chunk","created":0,"model":"my-local-model","choices":[{"index":0,"delta":{"role":"assistant","content":"ok"},"finish_reason":null}]}

        data: {"id":"1","object":"chat.completion.chunk","created":0,"model":"my-local-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: [DONE]

        """;

    @Inject
    private AiServiceManager aiServiceManager;

    private AiService<?> configuredProvider() {
        return (AiService<?>) aiServiceManager.getAiService(PROVIDER_ID);
    }

    private static void verifyChatCompletionRequest() {
        verify(
            postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .withHeader("Authorization", equalTo("Bearer fake-key"))
                .withHeader("X-Gateway-Route", equalTo("internal"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("my-local-model")))
        );
    }

    @Test
    void shouldCallConfiguredBaseUrlOnChatRequests() {
        stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(okJson(RESPONSE)));

        String answer = configuredProvider().chatModel(List.of()).chat("hi");

        assertThat(answer).isEqualTo("ok");
        verifyChatCompletionRequest();
    }

    @Test
    void shouldCallConfiguredBaseUrlOnStreamingChatRequests() throws Exception {
        stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(
            aResponse().withStatus(200).withHeader("Content-Type", "text/event-stream").withBody(STREAMING_RESPONSE)
        ));

        CompletableFuture<ChatResponse> answered = new CompletableFuture<>();
        configuredProvider().streamingChatModel(List.of()).chat("hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                answered.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                answered.completeExceptionally(error);
            }
        });

        assertThat(answered.get(10, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("ok");
        verifyChatCompletionRequest();
    }
}
