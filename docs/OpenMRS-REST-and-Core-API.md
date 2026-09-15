# OpenMRS — REST and Core API Guide


## Contents
- REST for Clients (conventions, auth, paging, representations, examples)
- REST Resources index highlights (1.9-era)
- Core API usage (Context/services, auth, transactions)
- AOP in OpenMRS (before/after/around)

---


## REST for Clients

# OpenMRS REST Web Services API For Clients — Notes

## Quick start
- Base path: `/openmrs/ws/rest/v1`
- Auth: HTTP Basic or session token via `/session` (cookie `JSESSIONID`)
- Formats: JSON (set `Accept: application/json`), XML also available

## Core conventions
- GET `/resource` search via `?q=`, GET `/resource/{uuid}` retrieve, POST create, POST `/resource/{uuid}` partial update, DELETE void/retire, DELETE with `?purge=true` hard delete
- No verbs in URLs; model actions as subresources
- Prefix with `/ws/rest/` and version `/v1`; lowercase ASCII; no extensions

## Pagination & filtering
- Use `limit` (default/absolute configurable in module Settings) and `startIndex` for paging
- Add `includeAll=true` to include retired metadata and voided/deleted data

## Representations
- `ref` (uuid, display, links), `default` (common fields + refs), `full` (all fields + `auditInfo`)
- Custom reps via `v=custom:(uuid,datatype:(uuid,name),conceptClass,names:ref)`

## Date/time
- Always include timezone, e.g. `2016-12-25T19:02:34.232+0700` (milliseconds optional)

## Authentication endpoints
- Obtain session: GET `/openmrs/ws/rest/v1/session` with Basic auth → returns token; send as `JSESSIONID` cookie
- Logout: DELETE `/openmrs/ws/rest/v1/session`
- Change own password: POST `/openmrs/ws/rest/v1/password` with `oldPassword`, `newPassword`
- Admin change password: POST `/openmrs/ws/rest/v1/password/{userUuid}` with `newPassword` (requires `EDIT_USER_PASSWORDS`)

## ETag & caching
- Responses include `ETag`; send `If-None-Match` to receive 304 if unchanged

## Locations prior to auth
- GET `/openmrs/ws/rest/v1/location?tag=Login%20Location` can list login locations without authentication

## Sample calls
```bash
# Search concepts (first 2)
curl -u admin:test -i "http://localhost:8080/openmrs/ws/rest/v1/concept?limit=2"

# Next page using startIndex
curl -i "http://localhost:8080/openmrs/ws/rest/v1/concept?limit=2&startIndex=2"

# Create an obs with timezone
curl -u admin:test -H "Content-Type: application/json"   -d '{"obsDatetime":"2016-12-25T19:02:34.232+0700"}'   http://localhost:8080/openmrs/ws/rest/v1/obs
```

## Tips
- Use representations to minimize round-trips; `v=custom:(...)` to tailor payloads
- Expect server-side limits; always specify `limit` and paginate
- Include timezone to avoid server-local assumptions


---


## REST Resources Index Highlights

# OpenMRS REST Resources Index (1.9-era) — Notes

This file lists key REST resources and patterns. For definitive, live docs see `https://rest.openmrs.org/#openmrs-rest-api`.

## Core patterns
- Endpoints follow: GET list, GET by uuid, POST create, POST `{uuid}` partial update, DELETE void/retire, DELETE `?purge=true` hard delete
- Representations: `ref` (uuid, display, links), `default` (core fields), `full` (adds `auditInfo` and nested data)

## Examples by resource
### Concept
```text
GET /ws/rest/v1/concept
GET /ws/rest/v1/concept/{uuid}
POST /ws/rest/v1/concept
POST /ws/rest/v1/concept/{uuid}
DELETE /ws/rest/v1/concept/{uuid}?!purge
DELETE /ws/rest/v1/concept/{uuid}?purge
```
- Common fields in `default`: `name`, `datatype`, `conceptClass`, `set`, `version`, `retired`, `names`, `descriptions`, `mappings`, `answers`, `setMembers`

### Patient
```text
GET /ws/rest/v1/patient
GET /ws/rest/v1/patient/{uuid}
POST /ws/rest/v1/patient
POST /ws/rest/v1/patient/{uuid}
DELETE /ws/rest/v1/patient/{uuid}
```
- Links to `person`, identifiers, etc.

### Encounter
```text
GET /ws/rest/v1/encounter
GET /ws/rest/v1/encounter/{uuid}
POST /ws/rest/v1/encounter
POST /ws/rest/v1/encounter/{uuid}
DELETE /ws/rest/v1/encounter/{uuid}
```
- Filter: `?patient={uuid}`, `?location={uuid}`

### Order & subtypes
```text
POST /ws/rest/v1/order
```
- Use body with `{"t":"drugorder"}` or `{"t":"testorder"}` to specify subtype
- Filter by subtype: `GET /ws/rest/v1/order?t=drugorder`

### Location
```text
GET /ws/rest/v1/location
GET /ws/rest/v1/location/{uuid}
```
- Unauthenticated login locations: `GET /ws/rest/v1/location?tag=Login%20Location`

## Representations in practice
- `ref`: minimal for links and lists
- `default`: balanced payload; returned unless `v=` specified
- `full`: expands nested objects and includes `auditInfo`

## Tuning responses
- Use `v=custom:(uuid,name,datatype:(uuid,name),conceptClass,names:ref)` to shape nested fields

## Pagination & filtering
- Server applies limits; use `limit` and `startIndex`
- Use `includeAll=true` to include retired/voided

## Notes
- Remember to set `Accept: application/json` and `Content-Type` as appropriate
- See `REST Web Services API For Clients — Notes` for auth, ETag and examples


---


## Core API and AOP
# OpenMRS API — Consolidated Notes (Core, Usage, AOP)


## Scope
- API 1: Core API overview (Java objects, Context, services, auth, transactions)
- API 2: How to use API (webapp, external apps, Maven deps, startup, sessions)
- API 3: AOP in OpenMRS (advice types, examples, module config)

## Core API concepts (from API 1)
- One-to-one mapping between data model tables and Java domain objects
- Access services via `org.openmrs.api.context.Context` (e.g., `Context.getPatientService()`)
- Persist via services (e.g., `personService.savePerson(person)`)
- Program to interfaces; default implementations are `*ServiceImpl`
- Authentication: `Context.authenticate(username, password)`; thread-bound `UserContext` managed by `OpenmrsFilter`
- Transactions: Spring-managed; annotate service interface with `@Transactional`

### Snippets
```java
PersonService personService = Context.getPersonService();
Person p = personService.getPerson(1);
p.setGender("M");
personService.savePerson(p);
```
```java
UserService userService = Context.getUserService();
List<User> users = userService.getUsers("bob");
```

## Using the API (from API 2)
### Within the webapp
- Work with objects (Patient, User, Concept, ...); get services from `Context`
```java
List<Patient> patients = Context.getPatientService().getAllPatients();
for (Patient patient : patients) {
  System.out.println(patient.getGivenName() + " " + patient.getFamilyName());
}
```

### External application (standalone Java)
- Preferred: Use REST Web Services for external integrations; direct API usage is not routinely tested now
- If using API JARs:
  - Include OpenMRS JARs and dependencies (Maven recommended)
  - Startup: `Context.startup(jdbcUrl, dbUser, dbPassword, props)`
  - Surround work with `Context.openSession()` / `Context.closeSession()`
  - Authenticate via `Context.authenticate("admin", "test")`
```java
Context.startup("jdbc:mysql://localhost:3306/db?autoReconnect=true", "user", "pass", props);
try {
  Context.openSession();
  Context.authenticate("admin", "test");
  List<Patient> patients = Context.getPatientService().getPatients("John");
  // ...
} finally {
  Context.closeSession();
}
```

### Maven setup (example)
```xml
<dependency>
  <groupId>org.openmrs.api</groupId>
  <artifactId>openmrs-api</artifactId>
  <version>${openMRSVersion}</version>
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>org.openmrs.web</groupId>
  <artifactId>openmrs-web</artifactId>
  <version>${openMRSVersion}</version>
  <scope>provided</scope>
</dependency>
```

### Runtime and environment
- Consider `application_data_directory`, `module_repository_folder`, working dir to pick modules and runtime properties
- Demarcate transaction boundaries; domain objects valid only within a session

## AOP in OpenMRS (from API 3)
- Used to extend/advise core service methods via modules
- Advice types: before, after, around
- Requirements: program to interfaces; get objects from central factory (no `new ServiceImpl()`)
- Configure module AOP in `config.xml` with `<advice>` entries pointing to service interface and advice class

### AfterReturning example (module)
```java
public class CountingAfterAdvice implements AfterReturningAdvice {
  public void afterReturning(Object ret, Method method, Object[] args, Object target) {
    if (method.getName().equals("getPatient")) { /* ... */ }
  }
}
```

### Around Advisor example
```java
public class PrintingAroundAdvisor extends StaticMethodMatcherPointcutAdvisor implements Advisor {
  public boolean matches(Method method, Class targetClass) {
    return method.getName().startsWith("get");
  }
  public Advice getAdvice() { return new PrintingAroundAdvice(); }
}
```

### Programmatic AOP via Context
```java
Context.addAdvisor(Class cls, Advisor advisor);
Context.removeAdvisor(Class cls, Advisor advisor);
Context.addAdvice(Class cls, Advice advice);
Context.removeAdvice(Class cls, Advice advice);
```

## Cross-links
- Javadocs: `http://api.openmrs.org/`
- REST alternative for external apps: see notes in `source-1-notes.md` and `source-2-notes.md`