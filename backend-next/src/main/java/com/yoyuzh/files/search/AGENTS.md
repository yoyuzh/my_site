# Files Search AGENTS

## Responsibility

- Own search-facing query composition over workspace, content, and sharing contracts without bypassing their ownership.

## Prohibitions

- Do not directly reach into other modules' internals or raw tables as a shortcut.
- Other modules may depend only on `files.search.api`.
