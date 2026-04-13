package com.sre.agent.commons;

import java.util.function.BiConsumer;

/**
 * Thread-local event stream for real-time investigation updates.
 * Tools call the static methods to emit events during execution.
 * The streaming controller sets the consumer; tools don't need to know about SSE.
 */
public class InvestigationEventStream {

    // BiConsumer<eventType, data>
    private static final ThreadLocal<BiConsumer<String, String>> CURRENT = new ThreadLocal<>();

    public static void set(BiConsumer<String, String> eventConsumer) {
        CURRENT.set(eventConsumer);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static boolean isStreaming() {
        return CURRENT.get() != null;
    }

    public static void emit(String event, String data) {
        BiConsumer<String, String> consumer = CURRENT.get();
        if (consumer != null) {
            consumer.accept(event, data);
        }
    }

    public static void status(String message) {
        emit("status", message);
    }

    public static void toolCall(String toolName, String params) {
        emit("tool_call", toolName + ": " + params);
    }

    public static void toolResult(String toolName, String summary) {
        emit("tool_result", toolName + ": " + summary);
    }

    public static void answer(String text) {
        emit("answer", text);
    }

    public static void done(String json) {
        emit("done", json);
    }
}
