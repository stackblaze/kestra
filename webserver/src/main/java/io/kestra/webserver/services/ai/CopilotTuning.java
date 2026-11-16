package io.kestra.webserver.services.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * StackBlaze: settings that make the Copilot usable on a small self-hosted model, read from plain environment
 * variables like the provider itself ({@code COPILOT_AI_*}).
 * <p>
 * Flow generation sends the model the list of every installed plugin type and asks it to pick the relevant ones:
 * about 2,000 types and 50,000 tokens on the full image. A 14B model on one GPU needs most of a minute to read
 * that, on every call, and a 16k-context model cannot take it at all. {@code COPILOT_AI_PLUGINS} limits the list
 * to the plugin groups the instance actually uses.
 */
public final class CopilotTuning {
    static final String PLUGINS = "COPILOT_AI_PLUGINS";
    static final String THINKING = "COPILOT_AI_THINKING";
    static final String PROVIDER_URL = "COPILOT_AI_URL";
    static final String PLUGIN_PREFIX = "io.kestra.plugin.";

    /** Used when the provider comes from COPILOT_AI_URL and COPILOT_AI_PLUGINS is not set. */
    static final List<String> DEFAULT_GROUPS = List.of("core", "scripts", "ai", "jdbc.postgresql", "email", "fs", "git");

    private CopilotTuning() {
    }

    /** The plugin groups to offer the model; empty = every installed plugin. */
    public static List<String> pluginGroups(Map<String, String> env) {
        String raw = env.get(PLUGINS);
        if (raw == null || raw.isBlank()) {
            String url = env.get(PROVIDER_URL);
            return url == null || url.isBlank() ? List.of() : DEFAULT_GROUPS;
        }
        if (raw.trim().equals("*") || raw.trim().equalsIgnoreCase("all")) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
            .map(group -> group.trim().toLowerCase(Locale.ROOT))
            .filter(group -> !group.isEmpty())
            .toList();
    }

    /**
     * Whether a plugin type belongs to one of the groups. A group is the part of the type after
     * {@code io.kestra.plugin.} ({@code core}, {@code jdbc.postgresql}) or a full package prefix.
     */
    public static boolean offered(String type, List<String> groups) {
        if (groups.isEmpty()) {
            return true;
        }
        String name = type.toLowerCase(Locale.ROOT);
        for (String group : groups) {
            String prefix = group.startsWith("io.") ? group : PLUGIN_PREFIX + group;
            if (name.equals(prefix) || name.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extra request fields for an OpenAI-compatible server. {@code COPILOT_AI_THINKING=false} turns off the
     * hidden reasoning Qwen3-style models write before every answer (vLLM's chat_template_kwargs), which is most
     * of the wait on a short reply. Nothing is added otherwise: api.openai.com rejects unknown fields.
     */
    public static Map<String, Object> customParameters(Map<String, String> env) {
        return "false".equalsIgnoreCase(String.valueOf(env.get(THINKING)).trim())
            ? Map.of("chat_template_kwargs", Map.of("enable_thinking", false))
            : Map.of();
    }
}
