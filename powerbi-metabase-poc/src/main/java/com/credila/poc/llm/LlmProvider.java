package com.credila.poc.llm;

/**
 * Abstraction for LLM APIs used to convert DAX to SQL.
 * Implementations support Claude (Anthropic), OpenAI, or Gemini (Google).
 */
public interface LlmProvider {

    /**
     * Sends a prompt to the LLM and returns the raw text response.
     *
     * @param prompt the user prompt
     * @return the model's text reply (e.g. SQL only, no markdown)
     * @throws IllegalStateException if the provider is not configured (e.g. missing API key)
     * @throws RuntimeException on API or parse errors
     */
    String complete(String prompt);
}
