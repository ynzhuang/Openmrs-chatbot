### Patient API Catalog (derived from docs/swagger.json)

- **Base path**: `/openmrs/ws/rest/v1`
- **Auth**: Basic auth or session (`/session`), JSON by default
- **Representations**: `v=ref|default|full|custom` (custom e.g., `v=custom:(uuid,identifiers:(identifier))`)
- **Pagination**: `limit`, `startIndex`

---

### patient
- **GET** `/patient`
  - **Summary**: Search for patients (at least one search parameter is typically expected)
  - **Query params**:
    - `limit` (int), `startIndex` (int), `v` (ref|default|full|custom), `q` (string)
    - Similarity/address filters: `matchSimilar`, `country`, `city`, `state`, `postalcode`, `address1`, `address2`, `givenname`, `middlename`, `familyname`, `birthdate`, `gender`
    - Identifier filters: `identifier`, `searchType` (with `identifier`)
    - Misc: `includeDead` (with `q`), `lastviewed`
  - **Response**: `FetchAll` of Patient refs/defaults depending on `v`

- **POST** `/patient`
  - **Create payload (minimal)**: `PatientCreate`
    - `person`: string (uuid) — or use `PatientCreateFull.person` as object to create person inline
    - `identifiers`: array of `PatientIdentifierCreate`
      - Each identifier: `identifier` (string), `identifierType` (uuid), optional `location` (uuid), `preferred` (boolean)
  - **Create payload (full)**: `PatientCreateFull`
    - `person`: `PersonCreate` (see person section)
    - `identifiers`: array of `PatientIdentifierCreate`

- **GET** `/patient/{uuid}`
  - Params: `uuid` (path), `v` (query)
  - Response schema: `PatientGet`

- **POST** `/patient/{uuid}`
  - Partial update payload: `PatientUpdate` (primarily updates nested `person`)

- **DELETE** `/patient/{uuid}`
  - Params: `uuid` (path), `purge` (boolean query)

---

### person (patient demographics live under person)
- **GET** `/person`
  - Params: `q` (required), `limit`, `startIndex`, `v`
- **POST** `/person`
  - `PersonCreate` (required: `gender`, `names`)
    - `names`: array of `PersonNameCreate` (givenName, familyName, ...)
    - Optional: `age`, `birthdate`, `birthdateEstimated`, `dead`, `deathDate`, `causeOfDeath`, `addresses` (array `PersonAddressCreate`), `attributes` (array `PersonAttributeCreate`), `deathdateEstimated`
- **GET** `/person/{uuid}` with `v`
- **POST** `/person/{uuid}` partial update (`PersonUpdate`)
- **DELETE** `/person/{uuid}` with optional `purge`

---

### encounter (patient’s clinical interactions)
- **GET** `/encounter`
  - Params: `limit`, `startIndex`, `v`, `q`
  - Patient filters:
    - `patient` (uuid)
    - Optionally with: `fromdate`, `todate`, `encounterType`, `obsConcept`, `obsValues`, `order`
- **POST/GET/DELETE** `/encounter/{uuid}` per swagger (standard by-uuid operations)

---

### obs (observations)
- Note: In this swagger snapshot, obs list path is not present by name; patient-focused retrieval commonly uses `/encounter` filters or other endpoints. If `/obs` exists on your instance, typical filters are `patient`, `concept`, date range, `limit`, `startIndex`, `v`.

---

### visit (visits per patient)
- **GET** `/visit`
  - Params: `limit`, `startIndex`, `v`, `q`
  - Patient filtering is typically supported via `?patient={uuid}` (confirm on your running instance if not shown in this swagger)
- **GET/POST/DELETE** `/visit/{uuid}` with standard `v`, `purge`

---

### order (drug/test and other orders)
- **GET** `/order`
  - Params: `limit`, `startIndex`, `v`, `q`, `t` (subtype), plus filters including:
    - `patient` (uuid), `orderNumber`, `careSetting`, `orderTypes`, `concepts`, `action`, status/date filters (`isStopped`, `includeVoided`, `excludeDiscontinueOrders`, `excludeCanceledAndExpired`, `includeNullFulfillerStatus`, `accessionNumber`, `fulfillerStatus`, `activatedOnOrBeforeDate`, `activatedOnOrAfterDate`, `canceledOrExpiredOnOrBeforeDate`, `autoExpireOnOrBeforeDate`)
- **GET/POST/DELETE** `/order/{uuid}` with standard `v`, `purge`

---

### condition (patient conditions/problems)
- **GET** `/condition`
  - Params: `limit`, `startIndex`, `v`, `q`
- **GET/POST/DELETE** `/condition/{uuid}` with standard `v`, `purge`

---

### allergy (patient subresource)
- **POST** `/patient/{parent-uuid}/allergy`
  - Body: `PatientAllergyCreate`
    - Required: `allergen`
    - Optional: `severity` (uuid), `comment` (string), `reactions` (array of reaction objects with `reaction.uuid`)
- **GET** `/patient/{parent-uuid}/allergy/{uuid}` with `v`
- **POST** `/patient/{parent-uuid}/allergy/{uuid}` to update

---

### Common query parameters across resources
- `limit` (int), `startIndex` (int)
- `v` representation: `ref`, `default`, `full`, `custom` (use `v=custom:(...)` for tailored payloads)
- `q` free-text search where available
- Some resources may accept `includeAll`/`includeVoided` or similar flags

---

### Example requests
```bash
# Search patients by name or identifier (default rep)
curl -u admin:test \
  "http://localhost:8080/openmrs/ws/rest/v1/patient?q=John&limit=10&startIndex=0"

# Fetch patient full details
curl -u admin:test \
  "http://localhost:8080/openmrs/ws/rest/v1/patient/{patientUuid}?v=full"

# Create patient (person by uuid)
curl -u admin:test -H "Content-Type: application/json" -d '{
  "person": "{personUuid}",
  "identifiers": [
    {"identifier":"P12345","identifierType":"{identifierTypeUuid}","location":"{locationUuid}","preferred":true}
  ]
}' \
  http://localhost:8080/openmrs/ws/rest/v1/patient

# Create patient (inline person)
curl -u admin:test -H "Content-Type: application/json" -d '{
  "person": {
    "gender": "M",
    "names": [{"givenName":"John","familyName":"Doe"}],
    "addresses": [{"address1":"123 Main St","cityVillage":"Nairobi"}]
  },
  "identifiers": [
    {"identifier":"P12345","identifierType":"{identifierTypeUuid}"}
  ]
}' \
  http://localhost:8080/openmrs/ws/rest/v1/patient

# Encounters by patient within date range
curl -u admin:test \
  "http://localhost:8080/openmrs/ws/rest/v1/encounter?patient={patientUuid}&fromdate=2020-01-01&todate=2020-12-31&limit=50"

# Orders by patient (drug orders)
curl -u admin:test \
  "http://localhost:8080/openmrs/ws/rest/v1/order?patient={patientUuid}&t=drugorder&limit=50"

# Add an allergy to patient
curl -u admin:test -H "Content-Type: application/json" -d '{
  "allergen": {"uuid": "{allergenConceptUuid}"},
  "severity": {"uuid": "{severityConceptUuid}"},
  "reactions": [{"reaction": {"uuid": "{reactionConceptUuid}"}}]
}' \
  http://localhost:8080/openmrs/ws/rest/v1/patient/{patientUuid}/allergy
```

---

Notes
- This catalog is generated from the checked-in `docs/swagger.json`. If your running instance has different modules/versions, re-export swagger and update this file.
- For tailored responses, prefer `v=custom:(...)` to minimize payloads and nested expansions.
