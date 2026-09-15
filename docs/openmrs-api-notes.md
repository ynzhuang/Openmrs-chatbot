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