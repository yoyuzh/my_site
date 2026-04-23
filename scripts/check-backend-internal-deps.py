#!/usr/bin/env python3
"""Report production imports that cross into another module's internal package."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "backend" / "src" / "main" / "java"

MODULES = sorted(
    (
        "app.android",
        "boot",
        "files.content",
        "files.search",
        "files.sharing",
        "files.upload",
        "files.workspace",
        "identity.access",
        "ops.admin",
        "platform.job",
        "platform.storage",
        "shared.kernel",
        "transfer",
    ),
    key=len,
    reverse=True,
)

PACKAGE_RE = re.compile(r"^package\s+com\.yoyuzh(?:\.(?P<package>[\w.]+))?\s*;")
IMPORT_RE = re.compile(r"^import\s+com\.yoyuzh\.(?P<import>[\w.]+)\s*;")


@dataclass(frozen=True)
class Violation:
    path: Path
    line_number: int
    source_module: str
    target_module: str
    imported_type: str


def module_of(package_name: str) -> str:
    for module in MODULES:
        if package_name == module or package_name.startswith(f"{module}."):
            return module
    return package_name.split(".", 1)[0] if package_name else "<root>"


def package_of(lines: list[str]) -> str:
    for line in lines:
        match = PACKAGE_RE.match(line.strip())
        if match:
            return match.group("package") or "<root>"
    return "<unknown>"


def scan_file(path: Path) -> list[Violation]:
    lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    source_module = module_of(package_of(lines))
    violations: list[Violation] = []

    for line_number, line in enumerate(lines, start=1):
        match = IMPORT_RE.match(line.strip())
        if not match:
            continue

        imported_type = match.group("import")
        if ".internal." not in imported_type:
            continue

        target_module = module_of(imported_type)
        if target_module == source_module:
            continue

        violations.append(
            Violation(
                path=path.relative_to(ROOT),
                line_number=line_number,
                source_module=source_module,
                target_module=target_module,
                imported_type=f"com.yoyuzh.{imported_type}",
            )
        )

    return violations


def main() -> int:
    violations = [
        violation
        for path in sorted(JAVA_ROOT.rglob("*.java"))
        for violation in scan_file(path)
    ]

    if not violations:
        print("0 cross-module internal imports found.")
        return 0

    print(f"{len(violations)} cross-module internal imports found:")
    for violation in violations:
        print(
            f"{violation.path}:{violation.line_number} "
            f"[{violation.source_module} -> {violation.target_module}] "
            f"import {violation.imported_type};"
        )

    return 1


if __name__ == "__main__":
    sys.exit(main())
