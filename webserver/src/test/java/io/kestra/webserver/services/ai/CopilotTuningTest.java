package io.kestra.webserver.services.ai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotTuningTest {
    @Test
    void shouldOfferEveryPluginWithoutCopilotVariables() {
        assertThat(CopilotTuning.pluginGroups(Map.of())).isEmpty();
        assertThat(CopilotTuning.offered("io.kestra.plugin.aws.s3.Upload", List.of())).isTrue();
    }

    @Test
    void shouldDefaultToASmallSetForTheEnvProvider() {
        List<String> groups = CopilotTuning.pluginGroups(Map.of("COPILOT_AI_URL", "https://gpu.example/v1"));

        assertThat(groups).isEqualTo(CopilotTuning.DEFAULT_GROUPS);
        assertThat(CopilotTuning.offered("io.kestra.plugin.core.log.Log", groups)).isTrue();
        assertThat(CopilotTuning.offered("io.kestra.plugin.jdbc.postgresql.Query", groups)).isTrue();
        assertThat(CopilotTuning.offered("io.kestra.plugin.jdbc.mysql.Query", groups)).isFalse();
        assertThat(CopilotTuning.offered("io.kestra.plugin.aws.s3.Upload", groups)).isFalse();
        // "ai" is a group, not a substring: io.kestra.plugin.airbyte is not offered.
        assertThat(CopilotTuning.offered("io.kestra.plugin.airbyte.connections.Sync", groups)).isFalse();
    }

    @Test
    void shouldReadTheGroupsAndAcceptAll() {
        List<String> groups = CopilotTuning.pluginGroups(Map.of("COPILOT_AI_URL", "x", "COPILOT_AI_PLUGINS", " Core , aws.s3,, io.kestra.plugin.gcp "));

        assertThat(groups).containsExactly("core", "aws.s3", "io.kestra.plugin.gcp");
        assertThat(CopilotTuning.offered("io.kestra.plugin.aws.s3.Upload", groups)).isTrue();
        assertThat(CopilotTuning.offered("io.kestra.plugin.aws.sqs.Publish", groups)).isFalse();
        assertThat(CopilotTuning.offered("io.kestra.plugin.gcp.bigquery.Query", groups)).isTrue();
        assertThat(CopilotTuning.pluginGroups(Map.of("COPILOT_AI_URL", "x", "COPILOT_AI_PLUGINS", "*"))).isEmpty();
    }

    @Test
    void shouldCapThePickedPlugins() {
        List<String> picked = List.of("a", "b", "c", "d", "e", "f", "g");

        assertThat(CopilotTuning.capPlugins(picked, Map.of())).isEqualTo(picked);
        assertThat(CopilotTuning.capPlugins(picked, Map.of("COPILOT_AI_URL", "x"))).containsExactly("a", "b", "c", "d", "e");
        assertThat(CopilotTuning.capPlugins(picked, Map.of("COPILOT_AI_URL", "x", "COPILOT_AI_MAX_PLUGINS", "2"))).containsExactly("a", "b");
        assertThat(CopilotTuning.capPlugins(picked, Map.of("COPILOT_AI_URL", "x", "COPILOT_AI_MAX_PLUGINS", "0"))).isEqualTo(picked);
    }

    @Test
    void shouldQuoteBareExpressions() {
        String yaml = "tasks:\n  - id: a\n    username: {{ secret('PG_USER') }}\n    message: \"{{ outputs.a.value }}\"\n    uri: {{ inputs.x | default(\"y\") }}\n    plain: text";

        assertThat(CopilotTuning.quoteExpressions(yaml)).isEqualTo(
            "tasks:\n  - id: a\n    username: \"{{ secret('PG_USER') }}\"\n    message: \"{{ outputs.a.value }}\"\n    uri: '{{ inputs.x | default(\"y\") }}'\n    plain: text");
    }

    @Test
    void shouldStripFencesAndReadTheRepairLimit() {
        assertThat(CopilotTuning.stripFences("```yaml\nid: a\nnamespace: b\n```")).isEqualTo("id: a\nnamespace: b");
        assertThat(CopilotTuning.repairs(Map.of())).isZero();
        assertThat(CopilotTuning.repairs(Map.of("COPILOT_AI_URL", "x"))).isEqualTo(2);
        assertThat(CopilotTuning.repairs(Map.of("COPILOT_AI_URL", "x", "COPILOT_AI_REPAIRS", "0"))).isZero();
    }

    @Test
    void shouldDropAPropertyTheValidatorDoesNotKnow() {
        String yaml = """
            id: f
            namespace: n
            tasks:
              - id: download
                type: io.kestra.plugin.core.http.Download
                uri: https://example.com/a.csv
                saveAs: a.csv
              - id: size
                type: io.kestra.plugin.core.storage.Size
                uri: "{{ outputs.download.uri }}"
                saveAs:
                  name: x
                  deep: true
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hi""";
        String error = "Unrecognized field \"saveAs\" (class io.kestra.plugin.core.storage.Size), not marked as ignorable (17 known properties: \"uri\")";

        String fixed = CopilotTuning.dropUnknownField(yaml, error);

        // Only in the task of that type, with its nested block; the Download task keeps its own.
        assertThat(fixed).contains("saveAs: a.csv").doesNotContain("deep: true").doesNotContain("name: x");
        assertThat(fixed).contains("uri: \"{{ outputs.download.uri }}\"\n  - id: log");
        assertThat(CopilotTuning.dropUnknownField(yaml, "YAML syntax error")).isNull();
        assertThat(CopilotTuning.dropUnknownField(fixed, error)).isNull();
    }

    @Test
    void shouldTurnThinkingOffOnlyWhenAsked() {
        assertThat(CopilotTuning.customParameters(Map.of())).isEmpty();
        assertThat(CopilotTuning.customParameters(Map.of("COPILOT_AI_THINKING", "true"))).isEmpty();
        assertThat(CopilotTuning.customParameters(Map.of("COPILOT_AI_THINKING", "false")))
            .isEqualTo(Map.of("chat_template_kwargs", Map.of("enable_thinking", false)));
    }
}
