# Contributing

Thanks for looking at the code. Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
first if you're changing how routing or execution works.

---

## Setup

**Prerequisites:** JDK 8, Maven 3.x.

```sh
mvn clean package     # → omod/target/chatbot-1.0.0-SNAPSHOT.omod
mvn test              # unit tests only
```

Dependencies resolve from Maven Central and the OpenMRS Nexus
(`https://mavenrepo.openmrs.org/public`), both declared in the root `pom.xml`.
If you're behind a restrictive proxy and the OpenMRS Nexus is unreachable,
the build cannot resolve its parent POM — that's a network issue, not a
project one.

For a running instance, use the [OpenMRS SDK](https://wiki.openmrs.org/display/docs/OpenMRS+SDK):

```sh
mvn openmrs-sdk:setup     # first time
mvn openmrs-sdk:run
```

Then install `omod/target/*.omod` via **Administration → Manage Modules**.

You do **not** need an OpenRouter API key to develop. With the key unset the
module falls back to heuristic routing and skips composition — which is also
the only configuration where no patient data leaves your machine.

---

## Adding a scenario

Most new capabilities are a JSON entry, not Java. Add it to
[`omod/src/main/resources/chatbot/scenarios.json`](omod/src/main/resources/chatbot/scenarios.json):

```jsonc
{
  "name": "unique_snake_case_name",     // the router's vocabulary — must be unique
  "title": "Short human title",
  "description": "What it returns. The router sees this.",
  "examples": ["A question this answers", "Another phrasing"],
  "method": "GET",
  "path": "/some/{uuid}/path",
  "pathParams": {"uuid": "${patient.uuid}"},
  "queryParams": {"v": "full", "limit": "${limit|50}"},
  "headers": {"Accept": "application/json"},
  "requiredContext": ["patient.uuid"],
  "notes": "Anything a future reader needs."
}
```

Rules that the validator enforces, and the reasoning behind each:

- **`name` must be unique.** `findByName()` returns the first match, so a
  duplicate silently shadows the other entry.
- **Every `{segment}` in `path` needs a `pathParams` entry.** Otherwise the
  executor issues a request with a literal `{segment}` in the URL.
- **A `${key}` with no `|default` must appear in `requiredContext`.**
  Unresolved placeholders become empty strings. On a `GET` that's a bad
  query; on a `POST` it writes malformed data to a patient record.
- **`examples` matter.** They go into the router's prompt and are most of
  what the model matches on.

Then run the validator — the same one CI runs:

```sh
python3 .github/scripts/validate_scenarios.py
```

Write scenarios (`method: POST`) deserve extra scrutiny: the router selects
from free text with no confirmation step, so anything that can be chosen can
be executed. See the "router can select a write scenario" section of
[`SECURITY.md`](SECURITY.md).

---

## Code style

Spotless enforces the OpenMRS conventions in `OpenMRSFormatter.xml` and runs
during `mvn clean package`. To run it on its own:

```sh
mvn spotless:apply    # format
mvn spotless:check    # verify — CI runs this as its own job
```

Every source file carries the MPL 2.0 header from `license-header.txt`;
Spotless applies it.

---

## Before you push

```sh
mvn clean verify
python3 .github/scripts/validate_scenarios.py
```

That's what CI runs. CI additionally uploads the built `.omod` as an
artifact, so you can download the exact module a commit produced.

---

## Conventions

**Branches.** `feat/…`, `fix/…`, `docs/…`, `refactor/…`, `test/…`.

**Commits.** `<type>(<scope>): <summary>` — imperative, under ~72 chars.
Use the body for *why*: the diff already shows what.

```
fix(scenarios): require all templated UUIDs for allergy creation

requiredContext listed only patient.uuid and allergen.concept.uuid, but
the body also templates severity and reaction UUIDs. With those absent
the precondition check passed and the module POSTed an allergy with
empty concept references instead of failing closed.
```

**Pull requests.** CI must be green: Maven build + tests, Spotless, and the
scenario validator. Call out anything that touches the security boundaries
in `SECURITY.md` — the session-cookie propagation, the REST base URL, or
what gets sent to the model provider.
