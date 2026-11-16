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
    static final String MAX_PLUGINS = "COPILOT_AI_MAX_PLUGINS";
    /** Used when the provider comes from COPILOT_AI_URL and COPILOT_AI_MAX_PLUGINS is not set. */
    static final int DEFAULT_MAX_PLUGINS = 5;
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
     * The plugin types whose schema is sent to the model, in the order it picked them. The model may pick up to
     * ten, and each schema is thousands of tokens: on a small self-hosted model that is most of the wait.
     * {@code COPILOT_AI_MAX_PLUGINS} sets the limit (0 = no limit); without the env provider nothing is cut.
     */
    public static List<String> capPlugins(List<String> picked, Map<String, String> env) {
        int max = maxPlugins(env);
        return max <= 0 || picked.size() <= max ? picked : List.copyOf(picked.subList(0, max));
    }

    static int maxPlugins(Map<String, String> env) {
        String raw = env.get(MAX_PLUGINS);
        if (raw == null || raw.isBlank()) {
            String url = env.get(PROVIDER_URL);
            return url == null || url.isBlank() ? 0 : DEFAULT_MAX_PLUGINS;
        }
        try {
            return Math.max(Integer.parseInt(raw.trim()), 0);
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_PLUGINS;
        }
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
