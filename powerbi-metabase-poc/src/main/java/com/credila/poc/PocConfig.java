package com.credila.poc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PocConfig {

    @Value("${poc.target-dialect:postgres}")
    private String targetDialect;

    @Value("${poc.skip-metabase:false}")
    private boolean skipMetabase;

    @Value("${llm.provider:gemini}")
    private String llmProvider;

    public String getTargetDialect() {
        return targetDialect;
    }

    public boolean isSkipMetabase() {
        return skipMetabase;
    }

    public String getLlmProvider() {
        return llmProvider;
    }
}
