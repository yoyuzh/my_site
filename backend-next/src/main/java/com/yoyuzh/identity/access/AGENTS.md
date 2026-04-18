# Identity Access AGENTS

## Responsibility

- Own account state, session state, login/register rules, role authorization context, and client session exclusivity.

## Prohibitions

- Do not decide file path legality, share expiry details, or storage placement rules.
- Other modules may depend only on `identity.access.api`.
