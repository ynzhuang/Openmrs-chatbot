package org.openmrs.module.chatbot.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

public class ScenarioExecutor {
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	private final String serverBase; // e.g., http://localhost:8080
	
	public ScenarioExecutor(String serverBase) {
		this.serverBase = serverBase;
	}
	
	public String execute(JsonNode scenario, Map<String, Object> context) throws Exception {
		return execute(scenario, context, null);
	}
	
	public String execute(JsonNode scenario, Map<String, Object> context, java.util.List<java.util.Map<String, Object>> debug)
	        throws Exception {
		// Resolve patient identifier to uuid if needed
		ensurePatientUuid(context);
		
		String basePath = scenario.path("basePath").asText(null);
		String method = scenario.path("method").asText("GET");
		String path = scenario.path("path").asText("");
		
		// Resolve placeholders in path
		JsonNode pathParams = scenario.path("pathParams");
		if (pathParams.isObject()) {
			Iterator<String> fields = pathParams.fieldNames();
			while (fields.hasNext()) {
				String key = fields.next();
				String value = resolve(pathParams.path(key).asText(), context);
				path = path.replace("{" + key + "}", value);
			}
		}
		
		String url = trimRight(serverBase) + trimRight(basePath != null ? basePath : "/openmrs/ws/rest/v1") + path;
		
		StringBuilder query = new StringBuilder();
		JsonNode queryParams = scenario.path("queryParams");
		if (queryParams.isObject()) {
			Iterator<String> fields = queryParams.fieldNames();
			while (fields.hasNext()) {
				String key = fields.next();
				String value = resolve(queryParams.path(key).asText(), context);
				if (value != null && !value.isEmpty()) {
					query.append(query.length() == 0 ? "?" : "&").append(key).append("=").append(encode(value));
				}
			}
		}
		url += query.toString();
		
		CloseableHttpClient http = HttpClients.createDefault();
		try {
			HttpRequestBase req;
			String body = null;
			if ("POST".equalsIgnoreCase(method)) {
				HttpPost post = new HttpPost(url);
				JsonNode bodyNode = scenario.path("body");
				if (!bodyNode.isMissingNode() && !bodyNode.isNull()) {
					String templated = resolve(objectMapper.writeValueAsString(bodyNode), context);
					body = templated;
					post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
					post.setHeader("Content-Type", "application/json");
				}
				req = post;
			} else {
				req = new HttpGet(url);
			}
			
			// Headers
			boolean hasCookie = false;
			JsonNode headers = scenario.path("headers");
			if (headers.isObject()) {
				Iterator<String> fields = headers.fieldNames();
				while (fields.hasNext()) {
					String key = fields.next();
					String value = resolve(headers.path(key).asText(), context);
					req.setHeader(key, value);
					if ("cookie".equalsIgnoreCase(key))
						hasCookie = true;
				}
			}
			Object sid = lookup(context, "sessionId");
			if (!hasCookie && sid != null) {
				req.setHeader("Cookie", "JSESSIONID=" + sid);
			}
			
			CloseableHttpResponse resp = http.execute(req);
			try {
				int code = resp.getStatusLine().getStatusCode();
				String respBody = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
				if (debug != null) {
					java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
					ev.put("step", "rest_call");
					ev.put("method", method);
					ev.put("url", url);
					ev.put("status", new Integer(code));
					ev.put("bytes", new Integer(respBody != null ? respBody.length() : 0));
					ev.put("requestBodyBytes", new Integer(body != null ? body.length() : 0));
					ev.put("ok", Boolean.valueOf(code >= 200 && code < 300));
					debug.add(ev);
				}
				if (code >= 200 && code < 300)
					return respBody;
				throw new IllegalStateException("OpenMRS REST HTTP " + code + ": " + respBody);
			}
			finally {
				resp.close();
			}
		}
		finally {
			http.close();
		}
	}
	
	private void ensurePatientUuid(Map<String, Object> context) throws Exception {
		Object patientObj = context.get("patient");
		if (!(patientObj instanceof Map))
			return;
		Map p = (Map) patientObj;
		Object uuid = p.get("uuid");
		Object identifier = p.get("identifier");
		if ((uuid == null || String.valueOf(uuid).isEmpty()) && identifier != null) {
			// Search patient by identifier/name
			String base = trimRight(serverBase) + "/openmrs/ws/rest/v1/patient?q=" + encode(String.valueOf(identifier))
			        + "&limit=1";
			CloseableHttpClient http = HttpClients.createDefault();
			HttpGet get = new HttpGet(base);
			CloseableHttpResponse resp = http.execute(get);
			try {
				int code = resp.getStatusLine().getStatusCode();
				String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
				if (code >= 200 && code < 300) {
					JsonNode node = objectMapper.readTree(body);
					JsonNode results = node.path("results");
					if (results.isArray() && results.size() > 0) {
						String resolved = results.get(0).path("uuid").asText(null);
						if (resolved != null)
							p.put("uuid", resolved);
					}
				}
			}
			finally {
				resp.close();
				http.close();
			}
		}
	}
	
	private String resolve(String template, Map<String, Object> context) {
		if (template == null)
			return null;
		String out = template;
		// naive ${key.path} replacement
		int idx = 0;
		while ((idx = out.indexOf("${", idx)) >= 0) {
			int end = out.indexOf('}', idx + 2);
			if (end < 0)
				break;
			String expr = out.substring(idx + 2, end);
			String key = expr;
			int pipe = expr.indexOf('|');
			if (pipe >= 0)
				key = expr.substring(0, pipe);
			Object val = lookup(context, key);
			String replacement = val == null ? "" : String.valueOf(val);
			out = out.substring(0, idx) + replacement + out.substring(end + 1);
			idx += replacement.length();
		}
		return out;
	}
	
	private Object lookup(Map<String, Object> ctx, String dotted) {
		String[] parts = dotted.split("\\.");
		Object cur = ctx;
		for (String p : parts) {
			if (!(cur instanceof Map))
				return null;
			cur = ((Map) cur).get(p);
			if (cur == null)
				return null;
		}
		return cur;
	}
	
	private static String trimRight(String s) {
		if (s == null)
			return "";
		return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
	}
	
	private static String encode(String v) {
		return v.replace(" ", "%20");
	}
}
