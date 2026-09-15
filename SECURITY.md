# Security

This module reads patient records and, when configured with an API key,
sends parts of them to a third-party language model provider. That is the
central security fact about it, so it comes first.

## ⚠️ Patient data is disclosed to the model provider

When `chatbot.openrouter.apiKey` is set, two requests per question go to
`https://openrouter.ai/api/v1/chat/completions`:

1. **Routing** — the clinician's question plus the scenario catalog.
2. **Composing** — the question plus **the JSON returned by the OpenMRS REST
   API**, which is the patient data itself: demographics, identifiers,
   medications, observations, encounters, lab results.

OpenRouter is a broker that forwards the request to an upstream model
provider, so the data may traverse more than one third party depending on
the configured model.

**Before pointing this at real records, you need a legal basis for that
disclosure** — a BAA or the equivalent in your jurisdiction, covering
OpenRouter *and* the upstream provider, plus whatever institutional review
your site requires. Treat this as a disclosure of PHI to an external
processor, because that is what it is.

### Running with no disclosure at all

Leave `chatbot.openrouter.apiKey` unset. The module then:

- routes with `chooseHeuristic()` — keyword matching, entirely local;
- skips the compose stage and returns the raw scenario results;
- makes **no outbound calls** beyond your own OpenMRS REST API.

The `debug` array in each response records `"attempted": false,
"reason": "missing_api_key"`, so you can confirm from the response itself
that nothing was sent. This is the right configuration for a demo, and the
only one that is safe by default.

## Credentials

The API key is stored as an **OpenMRS global property**
(`chatbot.openrouter.apiKey`), read at request time via
`Context.getAdministrationService().getGlobalProperty(...)`.

What that implies, stated plainly:

- The key lives in the OpenMRS database, **in clear text**, like any other
  global property.
- Any user with the *Manage Global Properties* privilege can read it through
  the admin UI. Treat that privilege as equivalent to holding the key.
- It is **not** in source control, and it is not in the `.omod`. Nothing in
  this repository contains a credential; `.gitignore` covers `.env` and
  local properties files.
- Rotate the key if it has been visible in a screen share, a database dump,
  or a support export.

## The privilege model

The module does not hold elevated rights of its own. `ScenarioController`
reads the caller's `JSESSIONID` cookie and propagates it to every REST call
`ScenarioExecutor` makes, so OpenMRS evaluates its normal authorization for
that user.

**A user cannot use the chatbot to read a record they could not open
directly.** If a scenario hits an endpoint the user lacks privileges for,
OpenMRS rejects it and the error surfaces in that scenario's result.

This also means access control is inherited, not reimplemented — there is no
second permission system in this module to keep in sync with OpenMRS's.

## The router can select a write scenario

Worth stating explicitly, because the UI is a question box and the natural
assumption is that it only reads.

The catalog is mostly read-only, but `create_patient_allergy` issues a
`POST` that creates a clinical record. Scenario selection is made by a
language model from the user's free text, so a phrasing like *"add
penicillin allergy"* can route to a write. There is no confirmation step
between routing and execution.

Two things currently constrain it: the model can only pick names that exist
in the catalog, and `requiredContext` must be fully satisfied before the
call is made — for the allergy scenario that means four concept UUIDs the
chat UI does not supply, so it fails closed in normal use.

If you deploy this against real records, decide deliberately whether write
scenarios belong in the catalog at all. Removing the entry from
`scenarios.json` removes the capability — no Java change needed. A
confirmation step for `method: POST` scenarios would be the more complete
fix and does not exist yet.

## Input handling

- **Scenario selection is a closed set.** The model returns *names*, which
  are looked up in `scenarios.json` via `findByName()`. A name that isn't in
  the catalog is skipped. The model cannot cause an arbitrary URL to be
  called by inventing an endpoint — it can only choose among the scenarios
  you shipped.
- **Preconditions are checked before dispatch.** `requiredContext` must be
  satisfied or the scenario returns an error instead of issuing a request.
- **Scenario definitions are trusted input.** `scenarios.json` ships inside
  the module and defines the paths that get called. Treat a change to it as
  a code change and review it as one — an entry added there is an endpoint
  the module will call.

## Generated text is not clinical advice

The composing prompt instructs the model to answer only from the returned
JSON and to say it cannot determine an answer rather than guess. That
reduces fabrication; it does not eliminate it. A model can still
misread or misattribute a value that *is* present in the response.

Every answer ships with the raw `results` it was composed from, so a
clinician can check the generated text against the underlying API response.
Treat the generated answer as a reading aid over the record, never as a
substitute for looking at the record. See the Healthcare Disclaimer in
`LICENSE`.

## Known limitations

Stated rather than implied:

- **No rate limiting or cost control.** Each question issues up to two model
  calls. There is no per-user quota and no spend cap.
- **No audit log of questions.** Questions and answers are not recorded to
  the OpenMRS audit trail. If your governance requires an access log of what
  was asked about which patient, you must add one.
- **Outbound calls are plain `HttpClients.createDefault()`** with no
  configured proxy, timeout, or certificate pinning. In a locked-down
  network you will need to configure egress explicitly.
- **No request timeouts.** A slow REST endpoint or model call ties up the
  serving thread for as long as the socket stays open.

## Fixed: caller-controlled request target (SSRF)

Recorded here because it is the kind of thing a reviewer should be able to
check was addressed rather than take on trust.

`execute` and `answer` previously read the REST base URL from the request
body (`payload.getOrDefault("serverBase", defaultBase(request))`). Because
`ScenarioExecutor` attaches the caller's `JSESSIONID` to every request it
makes, any authenticated user could make the OpenMRS server issue
cookie-bearing requests to an arbitrary host — reaching internal services
behind the firewall that the user could not reach directly, and disclosing
their session cookie to that host.

Both endpoints now derive the base URL from the incoming request only
(`defaultBase(request)`), so the target is always this server and is not
caller-controllable. The module's own `chatbot.jsp` never sent the field, so
the shipped UI is unaffected; a custom client passing `serverBase` will now
have it ignored rather than honoured.

## Reporting a vulnerability

Open a GitHub issue for anything non-sensitive. For a finding that would
expose patient data or credentials, contact the maintainer directly rather
than filing publicly, and include reproduction steps and the commit SHA.
