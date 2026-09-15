package org.openmrs.module.chatbot.web.controller;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;

public class OpenRouterClient {
	
	private final String apiKey;
	
	private final String baseUrl;
	
	private final String model;
	
	public OpenRouterClient(String apiKey, String model) {
		this.apiKey = apiKey;
		this.model = model;
		this.baseUrl = "https://openrouter.ai/api/v1/chat/completions";
	}
	
	public String chat(String system, String user, String jsonTools) throws Exception {
		CloseableHttpClient http = HttpClients.createDefault();
		try {
			HttpPost post = new HttpPost(baseUrl);
			post.setHeader("Authorization", "Bearer " + apiKey);
			post.setHeader("HTTP-Referer", "https://openmrs.org");
			post.setHeader("X-Title", "OpenMRS Chatbot");
			post.setHeader("Content-Type", "application/json");
			
			String body = "{\n" + "  \"model\": \"" + model + "\",\n" + "  \"messages\": [\n"
			        + "    {\"role\":\"system\",\"content\": " + toJson(system) + "},\n"
			        + "    {\"role\":\"user\",\"content\": " + toJson(user) + "}\n" + "  ],\n" + "  \"temperature\": 0\n"
			        + "}";
			post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
			
			CloseableHttpResponse resp = http.execute(post);
			try {
				int code = resp.getStatusLine().getStatusCode();
				String respBody = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
				if (code >= 200 && code < 300)
					return respBody;
				throw new IllegalStateException("OpenRouter HTTP " + code + ": " + respBody);
			}
			finally {
				resp.close();
			}
		}
		finally {
			http.close();
		}
	}
	
	private static String toJson(String s) {
		if (s == null)
			return "\"\"";
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
