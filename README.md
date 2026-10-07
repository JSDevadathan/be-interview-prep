# Harness

A project starter with GitHub templates, CI and repository conventions for building production-quality code.

## What's Included

| Path | Purpose |
|------|---------|
| `.github/PULL_REQUEST_TEMPLATE.md` | PR checklist linked to an issue |
| `.github/ISSUE_TEMPLATE/` | Bug and feature forms that require acceptance criteria |
| `.github/workflows/ci.yml` | PR title check and stack-detected lint, test and build (Node, Maven, Gradle, Python, Go) |
| `.github/dependabot.yml` | Weekly updates for GitHub Actions versions |
| `.editorconfig`, `.gitattributes` | Consistent formatting and line endings |

## Getting Started

1. Enable branch protection on `main`: require a pull request, require the `CI` checks to pass, and allow squash merges only.

## Workflow

1. Open an issue with clear acceptance criteria.
2. Branch from `main` as `<type>/<issue>-<short-summary>`.
3. Commit using [Conventional Commits](https://www.conventionalcommits.org).
4. Open a PR that links the issue with `Closes #N`, and get it reviewed.
5. Squash-merge once CI passes and the PR is approved.
