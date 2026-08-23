#!/usr/bin/env python3
"""Exhaustively validate a project-defined Internal Code registry and inventories."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any


VALID_STATUSES = {"active", "retired"}
VALID_OCCURRENCE_KINDS = {
    "business-throw",
    "validation-throw",
    "persistence-wrap",
    "transaction-completion",
    "framework-fallback",
    "event-publication",
    "event-dispatch",
    "event-consumption",
    "event-recovery",
    "saga-forward",
    "saga-compensation",
    "saga-recovery",
    "external-operation",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("registry", type=Path, help="Internal Code registry JSON")
    parser.add_argument(
        "--root", type=Path, default=Path.cwd(), help="Project root for source paths"
    )
    parser.add_argument(
        "--declarations",
        type=Path,
        help="Required JSON inventory generated from actual Java declarations",
    )
    parser.add_argument(
        "--occurrences",
        type=Path,
        help="Required JSON inventory generated from semantic Internal Code occurrences",
    )
    parser.add_argument(
        "--declared-symbols",
        type=Path,
        help=argparse.SUPPRESS,
    )
    return parser.parse_args()


def load_json_object(path: Path, label: str, errors: list[str]) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        errors.append(f"cannot read {label} {path}: {exception}")
        return {}
    if not isinstance(value, dict):
        errors.append(f"{label} root must be a JSON object")
        return {}
    return value


def compile_owner(owner: Any, index: int, errors: list[str]) -> dict[str, Any] | None:
    label = f"owners[{index}]"
    if not isinstance(owner, dict):
        errors.append(f"{label} must be an object")
        return None

    name = owner.get("name")
    if not isinstance(name, str) or not name.strip():
        errors.append(f"{label}.name must be a non-empty string")
        return None

    prefix = owner.get("prefix")
    minimum = owner.get("min")
    maximum = owner.get("max")
    has_prefix = prefix is not None
    has_range = minimum is not None or maximum is not None
    if has_prefix == has_range:
        errors.append(f"{label} must define exactly one namespace: prefix or numeric min/max")
        return None

    compiled: dict[str, Any] = {"name": name}
    if has_prefix:
        if not isinstance(prefix, str) or not prefix:
            errors.append(f"{label}.prefix must be a non-empty string")
            return None
        compiled["prefix"] = prefix
    else:
        if isinstance(minimum, bool) or not isinstance(minimum, (int, float)):
            errors.append(f"{label}.min must be a number")
            return None
        if isinstance(maximum, bool) or not isinstance(maximum, (int, float)):
            errors.append(f"{label}.max must be a number")
            return None
        if minimum > maximum:
            errors.append(f"{label}.min must not exceed max")
            return None
        compiled["min"] = minimum
        compiled["max"] = maximum

    pattern = owner.get("pattern")
    if pattern is not None:
        if not isinstance(pattern, str):
            errors.append(f"{label}.pattern must be a string")
            return None
        try:
            compiled["pattern"] = re.compile(pattern)
        except re.error as exception:
            errors.append(f"{label}.pattern is invalid: {exception}")
            return None
    return compiled


def validate_owner_namespaces(owners: list[dict[str, Any]], errors: list[str]) -> None:
    for index, left in enumerate(owners):
        for right in owners[index + 1 :]:
            if "prefix" in left and "prefix" in right:
                if left["prefix"].startswith(right["prefix"]) or right["prefix"].startswith(
                    left["prefix"]
                ):
                    errors.append(
                        f"owner namespaces overlap: {left['name']!r} prefix {left['prefix']!r} "
                        f"and {right['name']!r} prefix {right['prefix']!r}"
                    )
            elif "min" in left and "min" in right:
                if max(left["min"], right["min"]) <= min(left["max"], right["max"]):
                    errors.append(
                        f"owner namespaces overlap: {left['name']!r} range "
                        f"[{left['min']}, {left['max']}] and {right['name']!r} range "
                        f"[{right['min']}, {right['max']}]"
                    )


def owner_matches(owner: dict[str, Any], value: str | int) -> bool:
    if "prefix" in owner:
        if not isinstance(value, str) or not value.startswith(owner["prefix"]):
            return False
    else:
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            return False
        if value < owner["min"] or value > owner["max"]:
            return False
    pattern = owner.get("pattern")
    return pattern is None or pattern.fullmatch(str(value)) is not None


def normalize_location(location: Any, label: str, errors: list[str]) -> tuple[str, int] | None:
    if not isinstance(location, dict):
        errors.append(f"{label} must be an object with path and line")
        return None
    path = location.get("path")
    line = location.get("line")
    if not isinstance(path, str) or not path:
        errors.append(f"{label}.path must be a non-empty string")
        return None
    if isinstance(line, bool) or not isinstance(line, int) or line < 1:
        errors.append(f"{label}.line must be a positive integer")
        return None
    return path, line


def validate_source_location(
    root: Path, location: tuple[str, int], label: str, symbol: str, errors: list[str]
) -> None:
    relative_path, line_number = location
    path = (root / relative_path).resolve()
    try:
        path.relative_to(root)
    except ValueError:
        errors.append(f"{label}.path escapes project root: {relative_path}")
        return
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exception:
        errors.append(f"cannot read {label}.path {relative_path}: {exception}")
        return
    if line_number > len(lines):
        errors.append(f"{label}.line {line_number} exceeds {relative_path} length")
        return
    if symbol.rsplit(".", 1)[-1] not in lines[line_number - 1]:
        errors.append(f"{label} does not reference {symbol} at {relative_path}:{line_number}")


def load_inventory(
    path: Path | None, key: str, label: str, errors: list[str]
) -> list[dict[str, Any]]:
    if path is None:
        errors.append(f"--{key} is required for exhaustive validation")
        return []
    document = load_json_object(path, label, errors)
    if document.get("version") != 1:
        errors.append(f"{label}.version must be 1")
    items = document.get(key)
    if not isinstance(items, list):
        errors.append(f"{label}.{key} must be an array")
        return []
    return items


def parse_declarations(
    raw_items: list[dict[str, Any]], root: Path, errors: list[str]
) -> dict[str, tuple[str, int]]:
    declarations: dict[str, tuple[str, int]] = {}
    for index, item in enumerate(raw_items):
        label = f"declarations[{index}]"
        if not isinstance(item, dict):
            errors.append(f"{label} must be an object")
            continue
        symbol = item.get("symbol")
        if not isinstance(symbol, str) or not symbol.strip():
            errors.append(f"{label}.symbol must be a non-empty string")
            continue
        location = normalize_location(item, label, errors)
        if location is None:
            continue
        if symbol in declarations:
            errors.append(f"declaration inventory contains duplicate symbol: {symbol}")
            continue
        declarations[symbol] = location
        validate_source_location(root, location, label, symbol, errors)
    return declarations


def parse_occurrences(
    raw_items: list[dict[str, Any]], root: Path, errors: list[str]
) -> dict[str, list[tuple[str, str, int]]]:
    occurrences: dict[str, list[tuple[str, str, int]]] = {}
    seen_locations: set[tuple[str, str, int]] = set()
    for index, item in enumerate(raw_items):
        label = f"occurrences[{index}]"
        if not isinstance(item, dict):
            errors.append(f"{label} must be an object")
            continue
        symbol = item.get("symbol")
        kind = item.get("kind")
        if not isinstance(symbol, str) or not symbol.strip():
            errors.append(f"{label}.symbol must be a non-empty string")
            continue
        if kind not in VALID_OCCURRENCE_KINDS:
            errors.append(
                f"{label}.kind must be one of: {', '.join(sorted(VALID_OCCURRENCE_KINDS))}"
            )
            continue
        location = normalize_location(item, label, errors)
        if location is None:
            continue
        occurrence = (kind, *location)
        location_key = (symbol, *location)
        if location_key in seen_locations:
            errors.append(
                f"occurrence inventory contains duplicate location for {symbol}: "
                f"{location[0]}:{location[1]}"
            )
            continue
        seen_locations.add(location_key)
        occurrences.setdefault(symbol, []).append(occurrence)
        validate_source_location(root, location, label, symbol, errors)
    return occurrences


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    root = args.root.resolve()
    if args.declared_symbols is not None:
        errors.append(
            "--declared-symbols is no longer sufficient; generate JSON inventories and use "
            "--declarations and --occurrences"
        )

    registry = load_json_object(args.registry, "registry", errors)
    if registry.get("version") != 1:
        errors.append("registry.version must be 1")

    raw_owners = registry.get("owners")
    if not isinstance(raw_owners, list) or not raw_owners:
        errors.append("registry.owners must be a non-empty array")
        raw_owners = []
    owners = [
        compiled
        for index, owner in enumerate(raw_owners)
        if (compiled := compile_owner(owner, index, errors)) is not None
    ]
    owner_names = [owner["name"] for owner in owners]
    duplicates = sorted(name for name, count in Counter(owner_names).items() if count > 1)
    if duplicates:
        errors.append(f"duplicate owner names: {', '.join(duplicates)}")
    validate_owner_namespaces(owners, errors)

    raw_declarations = load_inventory(
        args.declarations, "declarations", "declaration inventory", errors
    )
    raw_occurrences = load_inventory(
        args.occurrences, "occurrences", "occurrence inventory", errors
    )
    declarations = parse_declarations(raw_declarations, root, errors)
    occurrences = parse_occurrences(raw_occurrences, root, errors)

    raw_codes = registry.get("codes")
    if not isinstance(raw_codes, list):
        errors.append("registry.codes must be an array")
        raw_codes = []

    values: dict[tuple[str, str], str] = {}
    registry_symbols: dict[str, str] = {}
    active_symbols: set[str] = set()
    retired_symbols: set[str] = set()

    for index, code in enumerate(raw_codes):
        label = f"codes[{index}]"
        if not isinstance(code, dict):
            errors.append(f"{label} must be an object")
            continue
        value, symbol, owner_name, status = (
            code.get("value"), code.get("symbol"), code.get("owner"), code.get("status")
        )
        if isinstance(value, bool) or not isinstance(value, (str, int)):
            errors.append(f"{label}.value must be a string or integer")
            continue
        if not isinstance(symbol, str) or not symbol.strip():
            errors.append(f"{label}.symbol must be a non-empty string")
            continue
        if not isinstance(owner_name, str) or not owner_name:
            errors.append(f"{label}.owner must be a non-empty string")
            continue
        if status not in VALID_STATUSES:
            errors.append(f"{label}.status must be active or retired")
            continue

        value_key = (type(value).__name__, json.dumps(value, ensure_ascii=False))
        if value_key in values:
            errors.append(f"duplicate code value {value!r}: {values[value_key]} and {symbol}")
        else:
            values[value_key] = symbol
        if symbol in registry_symbols:
            errors.append(f"duplicate symbol {symbol}: {registry_symbols[symbol]} and {label}")
        else:
            registry_symbols[symbol] = label

        matching_owners = [owner["name"] for owner in owners if owner_matches(owner, value)]
        if owner_name not in owner_names:
            errors.append(f"{label}.owner {owner_name!r} is not declared")
        elif matching_owners != [owner_name]:
            errors.append(
                f"{label}.value {value!r} must match only owner {owner_name!r}; "
                f"matched {matching_owners}"
            )

        registry_declaration = normalize_location(
            code.get("declaration"), f"{label}.declaration", errors
        ) if status == "active" or code.get("declaration") is not None else None
        registry_occurrence = normalize_location(
            code.get("occurrence"), f"{label}.occurrence", errors
        ) if status == "active" else None

        if status == "active":
            active_symbols.add(symbol)
            actual_declaration = declarations.get(symbol)
            if actual_declaration is None:
                errors.append(f"active registry symbol missing from declarations: {symbol}")
            elif registry_declaration != actual_declaration:
                errors.append(
                    f"stale declaration metadata for {symbol}: registry={registry_declaration}, "
                    f"inventory={actual_declaration}"
                )
            actual_occurrences = occurrences.get(symbol, [])
            if len(actual_occurrences) == 0:
                errors.append(f"active code has no semantic occurrence: {symbol}")
            elif len(actual_occurrences) > 1:
                errors.append(
                    f"active code has multiple semantic occurrences: {symbol} ({len(actual_occurrences)})"
                )
            else:
                _, path, line = actual_occurrences[0]
                if registry_occurrence != (path, line):
                    errors.append(
                        f"stale occurrence metadata for {symbol}: registry={registry_occurrence}, "
                        f"inventory={(path, line)}"
                    )
        else:
            retired_symbols.add(symbol)
            if code.get("occurrence") is not None:
                errors.append(f"{label} is retired and must not have an occurrence")
            if occurrences.get(symbol):
                errors.append(f"retired code is used by an active occurrence: {symbol}")
            if registry_declaration is not None and declarations.get(symbol) != registry_declaration:
                errors.append(
                    f"stale declaration metadata for retired {symbol}: "
                    f"registry={registry_declaration}, inventory={declarations.get(symbol)}"
                )

    unregistered_declarations = sorted(set(declarations) - set(registry_symbols))
    if unregistered_declarations:
        errors.append(
            "declared symbols missing from registry: " + ", ".join(unregistered_declarations)
        )
    unregistered_occurrences = sorted(set(occurrences) - set(registry_symbols))
    if unregistered_occurrences:
        errors.append(
            "semantic occurrences missing from registry: " + ", ".join(unregistered_occurrences)
        )

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(
        f"validated {len(raw_codes)} Internal Codes ({len(active_symbols)} active, "
        f"{len(retired_symbols)} retired), {len(declarations)} declarations, "
        f"{sum(map(len, occurrences.values()))} semantic occurrences across {len(owners)} owners"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
