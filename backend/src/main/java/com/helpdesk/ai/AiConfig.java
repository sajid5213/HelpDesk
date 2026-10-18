package com.helpdesk.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import com.helpdesk.config.HelpdeskProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AiConfig {

	private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

	@Bean
	AiClient aiClient(HelpdeskProperties properties, JsonMapper json) {
		HelpdeskProperties.Ai ai = properties.ai();
		if (HelpdeskProperties.Ai.CLAUDE_CODE.equals(ai.mode())) {
			log.warn("AI through the Claude Code command line ({}): local testing only; uses your Claude plan's limits",
					String.join(" ", ai.claudeCommand()));
			return new ClaudeCodeAiClient(ai.claudeCommand(), ai.classifyModel(), ai.replyModel(), ai.claudeTimeout(),
					json);
		}
		if (!ai.enabled()) {
			log.warn("AI is off (mode={}, or no ANTHROPIC_API_KEY): agents handle every ticket", ai.mode());
			return new DisabledAiClient();
		}
		log.info("AI on: {} classifies, {} writes replies", ai.classifyModel(), ai.replyModel());
		return new AnthropicAiClient(AnthropicOkHttpClient.builder().apiKey(ai.apiKey()).build(), ai.classifyModel(),
				ai.replyModel());
	}

}
