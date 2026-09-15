#!/usr/bin/env python3
"""Validate the scenario catalog that drives the chatbot's REST calls.

`scenarios.json` is configuration-as-data: the router offers its entries to
the model as a menu, and `ScenarioExecutor` turns the chosen entry into a
live HTTP request. A mistake in it is therefore a *runtime* failure against
a patient record, not a compile error -- the compiler never sees this file.
These checks are the compiler it doesn't have.

Run locally with:  python3 .github/scripts/validate_scenarios.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

CATALOG = Path("omod/src/main/resources/chatbot/scenarios.json")

# ${patient.uuid} or ${limit|50} -- the part before '|' is the context key.
PLACEHOLDER = re.compile(r"\$\{([^}|]+)(?:\|([^}]*))?\}")
# {uuid} style segments in a path template.
PATH_SEGMENT = re.compile(r"\{([^}]+)\}")

REQUIRED_FIELDS = ("name", "title", "description", "method", "path")
ALLOWED_METHODS = {"GET", "POST"}

errors: list[str] = []
warnings: list[str] = []


def error(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


def placeholders_in(value) -> set[str]:
    """Every context key referenced by a string, or nested in a dict/list."""
    found: set[str] = set()
    if isinstance(value, str):
        found.update(m.group(1) for m in PLACEHOLDER.finditer(value))
    elif isinstance(value, dict):
        for v in value.values():
            found |= placeholders_in(v)
    elif isinstance(value, list):
        for v in value:
            found |= placeholders_in(v)
    return found


def has_default(value: str, key: str) -> bool:
    """True if every reference to `key` in `value` supplies a |default."""
    for m in PLACEHOLDER.finditer(value):
        if m.group(1) == key and m.group(2) is None:
            return False
    return True


def main() -> int:
    if not CATALOG.exists():
        print(f"FAIL: catalog not found at {CATALOG}")
        return 1

    raw = CATALOG.read_text(encoding="utf-8")
    try:
        doc = json.loads(raw)
    except json.JSONDecodeError as exc:
        print(f"FAIL: {CATALOG} is not valid JSON: {exc}")
        return 1

    if not isinstance(doc.get("basePath"), str):
        error("top level: 'basePath' is missing or not a string")
    if "version" not in doc:
        error("top level: 'version' is missing")

    scenarios = doc.get("scenarios")
    if not isinstance(scenarios, list) or not scenarios:
        print("FAIL: top level: 'scenarios' must be a non-empty array")
        return 1

    seen: dict[str, int] = {}

    for index, sc in enumerate(scenarios):
        label = f"scenarios[{index}]"
        if not isinstance(sc, dict):
            error(f"{label}: expected an object")
            continue

        name = sc.get("name")
        if isinstance(name, str) and name:
            label = f"{name}"
            if name in seen:
                # Names are the router's vocabulary and are resolved by
                # findByName(), which returns the first match -- a duplicate
                # silently shadows the later entry.
                error(f"{label}: duplicate name (also at scenarios[{seen[name]}])")
            seen[name] = index
        else:
            error(f"{label}: 'name' is missing or empty")

        for field in REQUIRED_FIELDS:
            if not sc.get(field):
                error(f"{label}: '{field}' is missing or empty")

        method = sc.get("method")
        if method and method not in ALLOWED_METHODS:
            error(f"{label}: method {method!r} is not one of {sorted(ALLOWED_METHODS)}")

        examples = sc.get("examples")
        if not isinstance(examples, list) or not examples:
            # The router prompt is built from title + description + examples;
            # an entry with no examples is much harder for the model to pick.
            warn(f"{label}: no 'examples' -- the router has little to match on")

        # Every {segment} in the path must have a pathParams entry, or the
        # executor issues a request with a literal '{segment}' in the URL.
        path = sc.get("path") or ""
        path_params = sc.get("pathParams") or {}
        for segment in PATH_SEGMENT.findall(path):
            if segment not in path_params:
                error(f"{label}: path has {{{segment}}} but no pathParams entry for it")
        for declared in path_params:
            if "{%s}" % declared not in path:
                warn(f"{label}: pathParams declares {declared!r}, unused in path {path!r}")

        # Placeholders without a default must be satisfiable from
        # requiredContext, or the scenario runs with an empty value.
        required = sc.get("requiredContext") or []
        if not isinstance(required, list):
            error(f"{label}: 'requiredContext' must be an array")
            required = []

        for source_field in ("pathParams", "queryParams", "headers", "body"):
            blob = sc.get(source_field)
            if blob is None:
                continue
            text = json.dumps(blob)
            for key in placeholders_in(blob):
                if key in required:
                    continue
                if has_default(text, key):
                    continue  # ${limit|50} is fine unsatisfied
                warn(
                    f"{label}: {source_field} uses ${{{key}}} with no default "
                    f"and no matching entry in requiredContext"
                )

    for w in warnings:
        print(f"WARN: {w}")
    for e in errors:
        print(f"FAIL: {e}")

    if errors:
        print(f"\n{len(errors)} error(s) in {CATALOG}")
        return 1

    print(
        f"OK: {len(scenarios)} scenarios validated in {CATALOG}"
        + (f" ({len(warnings)} warning(s))" if warnings else "")
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
