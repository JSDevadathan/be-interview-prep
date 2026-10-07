# Engineering Standards

This file is the contract for every change in this repository. Treat each rule as a requirement, not a suggestion. Write code the way a senior engineer would put their name on it.

## Project Commands

Fill these in once the stack is chosen. Run them before saying any task is done.

| Purpose | Command |
|---------|---------|
| Install | `<install command>` |
| Lint    | `<lint command>` |
| Format  | `<format command>` |
| Test    | `<test command>` |
| Build   | `<build command>` |

## Code Quality

- **Correct first, then clear, then fast.** Do not optimize unless you have measured a problem.
- **Small, single-purpose units.** If a function needs "and" to describe it, split it. Keep nesting to 3 levels at most; prefer early returns.
- **Names carry the meaning.** Use intention-revealing names for variables, functions and types. No abbreviations beyond widely known ones (`id`, `url`, `dto`). Booleans read as predicates (`isActive`, `hasAccess`).
- **No magic values.** Pull literals that have business meaning into named constants or configuration.
- **Explicit error handling.** Never swallow exceptions. Fail fast on invalid input at system boundaries. Error messages state what failed and with which input, and never contain secrets.
- **Immutability by default.** Mutate state only where the design needs it.
- **Dependency direction.** Domain logic does not depend on frameworks, transport or persistence. Inject dependencies; do not reach for globals or singletons.
- **No dead code.** Delete unused functions, imports, parameters, feature flags and commented-out blocks. Git keeps the history.
- **No speculative generality.** Build what the current requirement needs. Three similar lines beat an early abstraction.
- **Reuse before writing.** Search the codebase for an existing utility, component or pattern before adding a new one.
- **Match the surrounding code.** Follow the file's existing conventions even when you would choose differently in a new file.
- **Security is not optional.** Validate and sanitize all external input. Use parameterized queries. Never log secrets, tokens or PII. Never commit credentials; read them from the environment.
- **Tests come with the change.** New behaviour gets tests. Bug fixes get a regression test that fails without the fix. Test behaviour, not implementation details. Tests are deterministic: no real network, clock or randomness without control.

## Comments Policy

The code explains *what* and *how*. A comment exists only to explain *why*, when the reason cannot be expressed in code.

**Allowed**
- Why a non-obvious decision was made (a workaround for a library bug, a performance constraint, a regulatory rule).
- Public API documentation (JSDoc / Javadoc / docstrings) on exported or public members, where the project uses it.
- `TODO(#123): ...` that references a tracked issue.
- Tooling directives (`eslint-disable-next-line`, `@ts-expect-error`, `noqa`, `@SuppressWarnings`), each with a reason.

**Forbidden**
- Comments that restate the code (`// increment counter`, `// get user by id`).
- Commented-out code.
- Changelog or authorship notes in code (`// added by`, `// updated for ticket`, `// new logic`, `// fixed bug`).
- Conversational or AI-assistant narration (`// Here we...`, `// Now let's...`, `// As requested`).
- Decorative banners and section dividers (`// ======`, `/* ***** */`).
- `TODO` / `FIXME` / `HACK` without an issue reference.

If you feel the need to write a comment, first try to remove the need: rename, extract a function or introduce a type. If the local harness hook `.claude/hooks/comment-guard.mjs` is installed, it flags violations on every edit. Fix what it reports; do not work around it.

## Git Workflow

- **Never commit directly to `main` / `master` / `develop`.** Branch from the latest default branch.
- **Branch names:** `<type>/<issue-number>-<short-kebab-summary>`, for example `feat/42-user-invite-flow` or `fix/87-null-total-on-empty-cart`.
- **Commits** follow [Conventional Commits](https://www.conventionalcommits.org): `<type>(<scope>): <imperative summary>`, 72 characters at most. Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `revert`.
- One logical change per commit. No "wip", "fix typo again" or "address comments" commits on the final branch; squash them.
- **Never** use `--no-verify`, `--force` against shared branches, or rewrite published history.
- Every PR links an issue (`Closes #N`). Every issue has acceptance criteria.

## Definition of Done

A task is done only when all of these are true:

1. Lint, format, test and build commands pass locally.
2. New or changed behaviour is covered by tests.
3. No forbidden comments, debug output (`console.log`, `System.out.println`, `print`), or dead code remain.
4. No secrets, credentials or environment-specific values are hard-coded.
5. Public interfaces, configuration and migrations are documented where users of the code need them.
6. The diff contains only changes related to the task, with no drive-by reformatting.

Report the result honestly. If a check fails or was skipped, say so with the output.

## Workflow Skills

These skills, the `senior-code-reviewer` agent and the guard hooks live in `.claude/`, which is kept out of git. When they are not installed, follow the same steps by hand: the rules in this file and the PR template apply either way.

| Skill | Use it to |
|-------|-----------|
| `/issue-create` | Draft and open a well-formed GitHub issue with acceptance criteria |
| `/pr-create` | Run quality gates, push the branch and open a PR linked to its issue |
| `/pr-review` | Review a PR at senior level and post the review on GitHub |
| `/pr-merge` | Check merge readiness, squash-merge and clean up |

The `senior-code-reviewer` agent (`.claude/agents/`) is the reviewer that `/pr-review` uses. You can also invoke it directly on local changes.
