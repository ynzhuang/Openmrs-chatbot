package org.openmrs.module.chatbot.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.api.context.Context;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

@Controller
@RequestMapping("module/chatbot")
public class ScenarioController {
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	@RequestMapping(value = { "/scenarios", "/scenarios.form" }, method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<String> getScenarios() {
		InputStream in = null;
		try {
			in = getClass().getClassLoader().getResourceAsStream("chatbot/scenarios.json");
			if (in == null) {
				return new ResponseEntity<String>("{\"error\":\"scenarios.json not found\"}", HttpStatus.NOT_FOUND);
			}
			String json = new String(readAll(in), StandardCharsets.UTF_8);
			return new ResponseEntity<String>(json, HttpStatus.OK);
		}
		catch (Exception e) {
			return new ResponseEntity<String>("{\"error\":\"" + e.getMessage() + "\"}", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		finally {
			if (in != null)
				try {
					in.close();
				}
				catch (Exception ignore) {}
		}
	}
	
	@RequestMapping(value = { "/route", "/route.form" }, method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> routeQuestion(@RequestBody Map<String, Object> payload) {
		// payload: { question: string, context: { patient: { uuid: ... }, ... }, max: 2 }
		Map<String, Object> result = new HashMap<String, Object>();
		try {
			String question = String.valueOf(payload.getOrDefault("question", ""));
			int max = ((Number) payload.getOrDefault("max", 2)).intValue();
			JsonNode scenarios = loadScenariosArray();
			
			String instruction = buildRoutingInstruction(scenarios, max);
			java.util.List<java.util.Map<String, Object>> debug = new java.util.ArrayList<java.util.Map<String, Object>>();
			List<String> chosen = callOpenRouterForRouting(question, instruction, scenarios, max, debug);
			if (chosen == null || chosen.isEmpty()) {
				chosen = chooseHeuristic(question, scenarios);
				if (chosen.size() > max)
					chosen = chosen.subList(0, max);
			}
			
			result.put("chosen", chosen);
			result.put("instruction", instruction);
			result.put("question", question);
			result.put("max", max);
			result.put("debug", debug);
			return new ResponseEntity<Map<String, Object>>(result, HttpStatus.OK);
		}
		catch (Exception e) {
			result.put("error", e.getMessage());
			return new ResponseEntity<Map<String, Object>>(result, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = { "/execute", "/execute.form" }, method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> executeScenarios(@RequestBody Map<String, Object> payload,
	        HttpServletRequest request) {
		// payload: { scenarios: [name...], context: {...} }
		Map<String, Object> result = new HashMap<String, Object>();
		try {
			List<String> names = (List<String>) payload.getOrDefault("scenarios", Collections.emptyList());
			Map<String, Object> ctx = (Map<String, Object>) payload.getOrDefault("context", Collections.emptyMap());
			// Always this server. The executor attaches the caller's JSESSIONID to
			// every request it makes, so honouring a caller-supplied base would let
			// an authenticated user aim a cookie-bearing request at any host the
			// server can reach. Not caller-controllable. See SECURITY.md.
			String serverBase = defaultBase(request);
			
			// propagate JSESSIONID if present
			String sid = findCookie(request.getCookies(), "JSESSIONID");
			if (sid != null) {
				ctx = new HashMap<String, Object>(ctx);
				ctx.put("sessionId", sid);
			}
			
			JsonNode all = loadScenariosArray();
			
			ScenarioExecutor executor = new ScenarioExecutor(serverBase);
			List<Map<String, Object>> responses = new ArrayList<Map<String, Object>>();
			java.util.List<java.util.Map<String, Object>> debug = new java.util.ArrayList<java.util.Map<String, Object>>();
			for (String name : names) {
				JsonNode sc = findByName(all, name);
				if (sc == null)
					continue;
				if (!hasRequiredContext(sc, ctx)) {
					Map<String, Object> one = new HashMap<String, Object>();
					one.put("name", name);
					one.put("error", "Missing required context for scenario");
					responses.add(one);
					continue;
				}
				String raw = executor.execute(sc, ctx, debug);
				Map<String, Object> one = new HashMap<String, Object>();
				one.put("name", name);
				one.put("response", raw);
				responses.add(one);
			}
			result.put("results", responses);
			result.put("debug", debug);
			return new ResponseEntity<Map<String, Object>>(result, HttpStatus.OK);
		}
		catch (Exception e) {
			result.put("error", e.getMessage());
			return new ResponseEntity<Map<String, Object>>(result, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@RequestMapping(value = { "/answer", "/answer.form" }, method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> answer(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
		// payload: { question: string, context: {...}, max?:2 }
		Map<String, Object> out = new HashMap<String, Object>();
		try {
			String question = String.valueOf(payload.getOrDefault("question", ""));
			int max = ((Number) payload.getOrDefault("max", 2)).intValue();
			Map<String, Object> ctx = (Map<String, Object>) payload.getOrDefault("context", Collections.emptyMap());
			// Always this server — see the note in executeScenarios().
			String serverBase = defaultBase(request);
			
			// route
			JsonNode scenarios = loadScenariosArray();
			java.util.List<java.util.Map<String, Object>> debug = new java.util.ArrayList<java.util.Map<String, Object>>();
			String instruction = buildRoutingInstruction(scenarios, max);
			List<String> chosen = callOpenRouterForRouting(question, instruction, scenarios, max, debug);
			if (chosen == null || chosen.isEmpty()) {
				chosen = chooseHeuristic(question, scenarios);
				if (chosen.size() > max)
					chosen = chosen.subList(0, max);
			}
			
			// execute
			String sid = findCookie(request.getCookies(), "JSESSIONID");
			if (sid != null) {
				ctx = new HashMap<String, Object>(ctx);
				ctx.put("sessionId", sid);
			}
			// Resolve identifier to uuid now that we have session cookie, before executing
			try {
				ensurePatientUuid(ctx, serverBase);
			}
			catch (Exception ignore) {}
			ScenarioExecutor executor = new ScenarioExecutor(serverBase);
			ArrayNode results = objectMapper.createArrayNode();
			for (String name : chosen) {
				JsonNode sc = findByName(scenarios, name);
				if (sc == null)
					continue;
				if (!hasRequiredContextAllowIdentifier(sc, ctx)) {
					ObjectNode r = objectMapper.createObjectNode();
					r.put("name", name);
					r.put("error", "Missing required context for scenario");
					results.add(r);
					continue;
				}
				String raw = executor.execute(sc, ctx, debug);
				ObjectNode r = objectMapper.createObjectNode();
				r.put("name", name);
				r.put("response", raw);
				results.add(r);
			}
			
			// compose final answer via OpenRouter
			String system = "You are a clinical assistant. Using the provided API JSON responses, answer the user question accurately and concisely. If data is missing, say you cannot determine. Do not invent data.";
			ObjectNode userPayload = objectMapper.createObjectNode();
			userPayload.put("question", question);
			userPayload.set("results", results);
			String finalAnswer = callOpenRouterForAnswer(system, userPayload.toString(), debug);
			if (finalAnswer == null || finalAnswer.trim().length() == 0) {
				java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
				ev.put("step", "ai_answer_fallback");
				ev.put("reason", finalAnswer == null ? "null" : "empty");
				debug.add(ev);
				finalAnswer = "I could not generate an answer at this time.";
			}
			
			out.put("chosen", chosen);
			out.put("results", objectMapper.readValue(results.toString(), java.util.List.class));
			out.put("answer", finalAnswer);
			out.put("debug", debug);
			return new ResponseEntity<Map<String, Object>>(out, HttpStatus.OK);
		}
		catch (Exception e) {
			out.put("error", e.getMessage());
			return new ResponseEntity<Map<String, Object>>(out, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	private boolean hasRequiredContext(JsonNode sc, Map<String, Object> ctx) {
		JsonNode req = sc.path("requiredContext");
		if (!req.isArray())
			return true;
		for (int i = 0; i < req.size(); i++) {
			String key = req.get(i).asText();
			if (lookup(ctx, key) == null)
				return false;
		}
		return true;
	}
	
	private boolean hasRequiredContextAllowIdentifier(JsonNode sc, Map<String, Object> ctx) {
		JsonNode req = sc.path("requiredContext");
		if (!req.isArray())
			return true;
		for (int i = 0; i < req.size(); i++) {
			String key = req.get(i).asText();
			if ("patient.uuid".equals(key)) {
				Object uuid = lookup(ctx, "patient.uuid");
				Object ident = lookup(ctx, "patient.identifier");
				if (uuid == null && ident == null)
					return false;
			} else {
				if (lookup(ctx, key) == null)
					return false;
			}
		}
		return true;
	}
	
	private Object lookup(Map<String, Object> ctx, String dotted) {
		String[] parts = dotted.split("\\.");
		Object cur = ctx;
		for (int i = 0; i < parts.length; i++) {
			if (!(cur instanceof Map))
				return null;
			cur = ((Map) cur).get(parts[i]);
			if (cur == null)
				return null;
		}
		return cur;
	}
	
	private JsonNode loadScenariosArray() throws Exception {
		String scenariosJson;
		InputStream in = getClass().getClassLoader().getResourceAsStream("chatbot/scenarios.json");
		if (in == null)
			throw new IllegalStateException("scenarios.json not found");
		try {
			scenariosJson = new String(readAll(in), StandardCharsets.UTF_8);
		}
		finally {
			try {
				in.close();
			}
			catch (Exception ignore) {}
		}
		JsonNode scenariosRoot = objectMapper.readTree(scenariosJson);
		return scenariosRoot.path("scenarios");
	}
	
	private static byte[] readAll(InputStream in) throws Exception {
		// compatible reader for Java 6/7
		byte[] buf = new byte[8192];
		int n;
		java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
		while ((n = in.read(buf)) != -1)
			baos.write(buf, 0, n);
		return baos.toByteArray();
	}
	
	private String buildRoutingInstruction(JsonNode scenarios, int max) {
		StringBuilder instruction = new StringBuilder();
		instruction.append("You are a router. Given a question, choose up to ").append(max)
		        .append(" scenario names that best answer it. Respond ONLY as a JSON array of names.\\n");
		instruction.append("Scenarios:\\n");
		for (JsonNode sc : scenarios) {
			instruction.append("- ").append(sc.path("name").asText()).append(": ").append(sc.path("title").asText())
			        .append(" — ").append(sc.path("description").asText()).append(" Examples: ");
			JsonNode ex = sc.path("examples");
			List<String> exs = new ArrayList<String>();
			for (int i = 0; i < ex.size(); i++) {
				exs.add(ex.get(i).asText());
			}
			instruction.append(String.join("; ", exs)).append("\\n");
		}
		return instruction.toString();
	}
	
	private List<String> callOpenRouterForRouting(String question, String instruction, JsonNode scenarios, int max) {
		return callOpenRouterForRouting(question, instruction, scenarios, max, null);
	}
	
	private List<String> callOpenRouterForRouting(String question, String instruction, JsonNode scenarios, int max,
	        java.util.List<java.util.Map<String, Object>> debug) {
		try {
			String apiKey = Context.getAdministrationService().getGlobalProperty("chatbot.openrouter.apiKey");
			String model = Context.getAdministrationService().getGlobalProperty("chatbot.openrouter.model",
			    "anthropic/claude-3.5-sonnet");
			if (apiKey == null || apiKey.trim().isEmpty()) {
				if (debug != null) {
					java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
					ev.put("step", "ai_routing");
					ev.put("attempted", Boolean.FALSE);
					ev.put("reason", "missing_api_key");
					debug.add(ev);
				}
				return null;
			}
			OpenRouterClient client = new OpenRouterClient(apiKey, model);
			String response = client.chat(instruction, question, null);
			if (debug != null) {
				java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
				ev.put("step", "ai_routing");
				ev.put("attempted", Boolean.TRUE);
				ev.put("ok", Boolean.TRUE);
				ev.put("bytes", new Integer(response != null ? response.length() : 0));
				debug.add(ev);
			}
			// parse choices[0].message.content as JSON array of names
			JsonNode root = objectMapper.readTree(response);
			String content = root.path("choices").get(0).path("message").path("content").asText();
			JsonNode arr = objectMapper.readTree(content);
			List<String> names = new ArrayList<String>();
			for (int i = 0; i < arr.size(); i++) {
				names.add(arr.get(i).asText());
			}
			return names;
		}
		catch (Exception e) {
			if (debug != null) {
				java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
				ev.put("step", "ai_routing");
				ev.put("attempted", Boolean.TRUE);
				ev.put("ok", Boolean.FALSE);
				ev.put("error", String.valueOf(e.getMessage()));
				debug.add(ev);
			}
			return null;
		}
	}
	
	private String callOpenRouterForAnswer(String system, String userJson) {
		return callOpenRouterForAnswer(system, userJson, null);
	}
	
	private String callOpenRouterForAnswer(String system, String userJson,
	        java.util.List<java.util.Map<String, Object>> debug) {
		try {
			String apiKey = Context.getAdministrationService().getGlobalProperty("chatbot.openrouter.apiKey");
			String model = Context.getAdministrationService().getGlobalProperty("chatbot.openrouter.model",
			    "anthropic/claude-3.5-sonnet");
			if (apiKey == null || apiKey.trim().isEmpty()) {
				if (debug != null) {
					java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
					ev.put("step", "ai_answer");
					ev.put("attempted", Boolean.FALSE);
					ev.put("reason", "missing_api_key");
					debug.add(ev);
				}
				return null;
			}
			OpenRouterClient client = new OpenRouterClient(apiKey, model);
			String response = client.chat(system, userJson, null);
			JsonNode root = objectMapper.readTree(response);
			String content = root.path("choices").get(0).path("message").path("content").asText();
			if (debug != null) {
				java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
				ev.put("step", "ai_answer");
				ev.put("attempted", Boolean.TRUE);
				ev.put("ok", Boolean.TRUE);
				ev.put("bytes", new Integer(response != null ? response.length() : 0));
				ev.put("contentBytes", new Integer(content != null ? content.length() : 0));
				debug.add(ev);
			}
			return content;
		}
		catch (Exception e) {
			if (debug != null) {
				java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
				ev.put("step", "ai_answer");
				ev.put("attempted", Boolean.TRUE);
				ev.put("ok", Boolean.FALSE);
				ev.put("error", String.valueOf(e.getMessage()));
				debug.add(ev);
			}
			return null;
		}
	}
	
	private JsonNode findByName(JsonNode scenarios, String name) {
		for (JsonNode sc : scenarios) {
			if (name.equals(sc.path("name").asText()))
				return sc;
		}
		return null;
	}
	
	private List<String> chooseHeuristic(String question, JsonNode scenarios) {
		String q = question.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<String>();
		for (JsonNode sc : scenarios) {
			String name = sc.path("name").asText();
			String text = (sc.path("title").asText("") + " " + sc.path("description").asText("")).toLowerCase(Locale.ROOT);
			boolean match = false;
			if (q.contains("medication") || q.contains("medications") || q.contains("drug")) {
				match = name.contains("medication") || name.contains("drug_order") || text.contains("drug order");
			}
			if (q.contains("blood pressure") || q.contains("bp")) {
				match = match || name.contains("bp") || text.contains("blood pressure");
			}
			if (q.contains("weight")) {
				match = match || name.contains("weight") || text.contains("weight");
			}
			if (q.contains("temperature") || q.contains("temp")) {
				match = match || name.contains("temperature") || text.contains("temperature");
			}
			if (q.contains("visit") || q.contains("encounter") || q.contains("summary")) {
				match = match || name.contains("visit") || name.contains("encounter");
			}
			if (q.contains("wbc") || q.contains("white blood")) {
				match = match || name.contains("wbc") || text.contains("white blood");
			}
			if (q.contains("identifier") || q.contains("mrn") || q.contains("id")) {
				match = match || name.contains("identifier");
			}
			if (q.contains("profile") || q.contains("demographic") || q.contains("details")) {
				match = match || name.contains("profile");
			}
			if (match)
				out.add(name);
		}
		if (out.isEmpty() && scenarios.size() > 0) {
			out.add(scenarios.get(0).path("name").asText());
		}
		return out;
	}
	
	private static String defaultBase(HttpServletRequest req) {
		String scheme = req.getScheme();
		String host = req.getServerName();
		int port = req.getServerPort();
		return scheme + "://" + host + ((port == 80 || port == 443) ? "" : (":" + port));
	}
	
	private static String findCookie(Cookie[] cookies, String name) {
		if (cookies == null)
			return null;
		for (Cookie c : cookies) {
			if (name.equals(c.getName()))
				return c.getValue();
		}
		return null;
	}
	
	private void ensurePatientUuid(Map<String, Object> ctx, String serverBase) throws Exception {
		Object pObj = ctx.get("patient");
		if (!(pObj instanceof Map))
			return;
		Map p = (Map) pObj;
		Object uuid = p.get("uuid");
		Object identifier = p.get("identifier");
		if ((uuid == null || String.valueOf(uuid).length() == 0) && identifier != null) {
			String sb = serverBase == null ? "" : serverBase;
			if (sb.endsWith("/"))
				sb = sb.substring(0, sb.length() - 1);
			String base = sb + "/openmrs/ws/rest/v1/patient";
			String id = java.net.URLEncoder.encode(String.valueOf(identifier), "UTF-8");
			String[] attempts = new String[] { base + "?identifier=" + id + "&limit=1", base + "?q=" + id + "&limit=1" };
			String sid = null; // no cookie here; endpoints that require login will use session cookie on executor
			for (int i = 0; i < attempts.length; i++) {
				CloseableHttpClient http = HttpClients.createDefault();
				HttpGet get = new HttpGet(attempts[i]);
				Object sidIn = ctx.get("sessionId");
				if (sidIn != null) {
					get.setHeader("Cookie", "JSESSIONID=" + String.valueOf(sidIn));
				}
				CloseableHttpResponse resp = http.execute(get);
				try {
					int code = resp.getStatusLine().getStatusCode();
					String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
					if (code >= 200 && code < 300) {
						JsonNode node = objectMapper.readTree(body);
						JsonNode results = node.path("results");
						if (results.isArray() && results.size() > 0) {
							String resolved = results.get(0).path("uuid").asText(null);
							if (resolved != null) {
								p.put("uuid", resolved);
								return;
							}
						}
					}
				}
				finally {
					try {
						resp.close();
					}
					catch (Exception ignore) {}
					try {
						http.close();
					}
					catch (Exception ignore) {}
				}
			}
		}
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
