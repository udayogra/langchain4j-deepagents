---
name: security-smoke
description: Quick pass for obvious secrets, injection, and unsafe defaults in workspace files.
---

# Security smoke skill

When asked for a lightweight security check:

- Scan for hardcoded API keys, passwords, or tokens in source.
- Flag obvious injection sinks (string concat into queries/shell) if visible in context.
- Call out dangerous defaults (e.g. debug flags left on) when you see them.

This is not a full audit; escalate unknowns clearly.
