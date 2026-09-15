# Architecture

How the module is put together and why. `README.md` covers *what* it does;
this is the level of detail you need before changing it.

---

## 1. The problem

A clinician's question — *"what is this patient on right now?"* — maps onto
one or more OpenMRS REST calls with specific representations, then onto
reading the JSON that comes back. Two naive approaches both fail:

- **Let a model call the API freely.** It invents endpoints, guesses
  representation strings, and you cannot predict or review what it will
  request against a patient record.
- **Hard-code one endpoint per question.** Every new question is a Java
  change, a rebuild, and a redeploy.

This module takes a third path: **a fixed catalog of parameterised REST
calls, with the model choosing among them.** The model's entire authority is
picking names from a closed list. Everything it can cause to happen was
written, reviewed, and shipped by a human.

---

## 2. The pipeline

```
question
   │
   ▼
┌──────────────────────────────────────────────────────────┐
│ ROUTE          ScenarioController.callOpenRouterForRouting│
│   prompt = buildRoutingInstruction(catalog, max)          │
│   model returns  ["current_medications", ...]             │
│   ── on no key / error ──▶ chooseHeuristic()  (keywords)  │
└───────────────────────────┬──────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────┐
│ EXECUTE                        ScenarioExecutor.execute() │
│   findByName() → scenario                                 │
│   hasRequiredContext() → precondition gate                │
│   resolve ${...} → URL + query + body                     │
│   HTTP to the OpenMRS REST API, caller's JSESSIONID       │
└───────────────────────────┬──────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────┐
│ COMPOSE        ScenarioController.callOpenRouterForAnswer │
│   system: answer from this JSON only; do not invent data  │
│   user:   { question, results: [...raw API JSON...] }     │
└───────────────────────────┬──────────────────────────────┘
                            ▼
        { answer, chosen, results, debug }
```

Each stage is also its own endpoint (`/route`, `/execute`, `/answer`), which
is a deliberate testing affordance: you can check routing without spending an
API call on composition, or replay an execution with a hand-picked scenario
list to isolate whether a bad answer came from picking the wrong call or
from misreading a right one.

---

## 3. Why the catalog is data

`omod/src/main/resources/chatbot/scenarios.json` holds 20 entries. Each is
consumed three different ways, which is what makes the single file worth it:

| Consumer | What it reads | Why there |
|---|---|---|
| `buildRoutingInstruction()` | `name`, `title`, `description`, `examples` | renders the model's menu — the model picks a name, never a URL |
| `ScenarioExecutor.execute()` | `method`, `path`, `pathParams`, `queryParams`, `headers`, `body` | builds the actual request |
| `hasRequiredContext()` | `requiredContext` | refuses to dispatch when context is missing |

Adding a capability is a JSON entry. No Java, no recompile of the routing
logic, and the router picks it up because the prompt is generated from the
catalog at request time.

### Placeholder resolution

`${key}` and `${key|default}` are resolved by `ScenarioExecutor.resolve()`
against the request context, with `lookup()` walking dotted paths
(`patient.uuid` → `ctx["patient"]["uuid"]`).

The `|default` form is what makes entries like `${limit|50}` safe without
declaring `limit` as required. A placeholder with **no** default and **no**
`requiredContext` entry resolves to empty — which is why
`.github/scripts/validate_scenarios.py` treats that combination as a
warning. It caught exactly that in the allergy-creation scenario, where three
of four templated UUIDs were unguarded and the module would have POSTed an
allergy with empty concept references.

---

## 4. Graceful degradation

Both model calls are wrapped so failure is a *downgrade*, not an error:

```
callOpenRouterForRouting()  →  null on missing key or any exception
                                ↓
                      chooseHeuristic()  — keyword match over
                                           names + titles + descriptions
```

If routing returns nothing, heuristics choose. If heuristics match nothing,
the first scenario is used. If composition fails, the raw results still come
back with a fallback sentence. **The chat box never shows an error page
because the model was unreachable** — it shows a worse answer.

The cost of this is that a degraded answer looks like a normal one. That's
what the `debug` array is for: every stage records whether it was attempted,
whether it succeeded, and why not — `{"step":"ai_routing","attempted":false,
"reason":"missing_api_key"}`. A confusing answer can be diagnosed from the
response body alone, without server log access.

---

## 5. Trust and authority

Three boundaries, worth being explicit about since this reads patient data:

**The model has no credentials.** It never sees an OpenMRS session, never
issues an HTTP request. It returns names; Java does the rest.

**The module has no privileges of its own.** `ScenarioController` lifts the
caller's `JSESSIONID` from the incoming request and `ScenarioExecutor`
attaches it to each REST call, so OpenMRS applies its normal authorization
to that user. The chatbot cannot read what the user could not.

That mechanism is also why the REST base URL is **not** caller-controllable.
It is derived from the incoming request (`defaultBase(request)`) rather than
read from the payload; a caller-supplied base combined with an attached
session cookie would be an SSRF that leaks the cookie. See `SECURITY.md`.

**The composing model sees data, not the record.** It gets the JSON that was
returned, with an instruction to answer from it or decline. It has no way to
fetch more.

---

## 6. Module layout

Standard OpenMRS two-module Maven layout:

| Module | Contains |
|---|---|
| `api/` | `OpenMRSChatbotActivator` (lifecycle), service + DAO interfaces and impls, `liquibase.xml`, i18n `messages*.properties` |
| `omod/` | web layer: controllers, `chatbot.jsp`, `config.xml` module descriptor, `webModuleApplicationContext.xml`, and the scenario catalog |

`omod` depends on `api`; the packaged `.omod` bundles both plus the Jackson
and HttpClient jars declared in `omod/pom.xml`.

The chatbot logic lives entirely in `omod/.../web/controller/`:

- **`ScenarioController`** — the three endpoints, routing, heuristic
  fallback, composition, identifier→UUID resolution.
- **`ScenarioExecutor`** — scenario to HTTP request: placeholder
  resolution, URL assembly, cookie propagation, response capture.
- **`OpenRouterClient`** — a thin chat-completions client.

---

## 7. Known limitations

Honest notes for whoever reads the code next:

- **`buildRoutingInstruction()` emits literal `\n`** — the Java source has
  `"\\n"`, so the prompt contains those two characters rather than a
  newline. Models tolerate it; it is still not what was meant.
- **The router sees the whole catalog on every request.** At 20 scenarios
  that is fine. It grows linearly, and there is no retrieval step.
- **No caching.** Identical questions re-route, re-execute, and re-compose.
- **No request timeouts** on either the REST or the model calls.
- **Test coverage is thin.** `AdminListExtensionTest` plus the generated
  service/DAO tests; the routing, resolution, and execution paths have no
  unit tests. `validate_scenarios.py` covers the catalog as data, which is
  where the runtime failures actually come from, but it is not a substitute.

### Recently addressed

- **Request JSON is now built with Jackson.** `OpenRouterClient` previously
  concatenated its body and escaped only `\` and `"`, so any control
  character in the input produced a body that was not valid JSON and the
  call failed. A newline — the common case, a question pasted out of a
  clinical note — was enough to break it. Verified: under the old escaper a
  newline, a tab, and a `` each yielded unparseable JSON; all three
  round-trip cleanly now.
- **Dead code removed from `ScenarioController`** — the unused `trimRight()`
  and `encode()` helpers (`ScenarioExecutor` has its own live copies) and
  the two single-argument overloads that nothing called.
