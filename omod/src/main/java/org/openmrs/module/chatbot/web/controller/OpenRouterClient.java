package org.openmrs.module.chatbot.web.controller;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

public class OpenRouterClient {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
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
			
			post.setEntity(new StringEntity(buildRequestBody(system, user), StandardCharsets.UTF_8));
			
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
	
	/**
	 * Serialize the chat-completions request with Jackson rather than by hand.
	 *
	 * The previous implementation concatenated the JSON and escaped only
	 * backslash and double-quote, so any control character in the input --
	 * most commonly a newline in a question pasted out of a clinical note --
	 * produced a body that is not valid JSON, and OpenRouter rejected the
	 * call. Jackson escapes the full set.
	 */
	private String buildRequestBody(String system, String user) throws Exception {
		ObjectNode root = MAPPER.createObjectNode();
		root.put("model", model);
		ArrayNode messages = root.putArray("messages");
		messages.add(message("system", system));
		messages.add(message("user", user));
		root.put("temperature", 0);
		return MAPPER.writeValueAsString(root);
	}
	
	private ObjectNode message(String role, String content) {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("role", role);
		node.put("content", content == null ? "" : content);
		return node;
	}
}
