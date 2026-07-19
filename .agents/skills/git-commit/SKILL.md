---
name: git-commit
description: Create Git commits from workspace changes as small, logically independent units using the required Conventional Commit-style message format. Use when asked to inspect, plan, stage, create, or amend Git commits.
---

# Git Commit

Create commits that each represent one reviewable purpose. Do not combine unrelated changes just to make a single commit.

## Workflow

1. Inspect the branch, working tree, staged changes, relevant diffs, and recent commit messages before staging files.
2. Before staging, create a commit plan. For every group, list its single responsibility, exact files or hunks, and the complete commit message (title and bullet-point body). Present the plan to the user when the grouping is ambiguous.
3. Default to one responsibility per commit. Do not mix API layers such as controller, service, repository, DTO, or tests in one commit unless a smaller commit would fail to build or test. Keep a test with its implementation only when they are inseparable.
4. Stage only the planned files or hunks for one group. Never use blanket staging commands.
5. Run relevant validation, then run `git diff --cached` before every commit. Confirm that the staged diff matches only the planned group.
6. Create one commit per group in dependency order. Leave unrelated or incomplete changes unstaged.

## Required Commit Plan

Use this structure before staging:

```text
1. <type>(scope): <subject>
   - Responsibility: <one purpose>
   - Files/hunks: <exact paths or hunk descriptions>
   - Body:
     - <change one>
     - <change two>
```

Do not stage or commit until every proposed group has a responsibility, files or hunks, and a complete message.

## Commit Message

Use this title format:

```text
<type>(optional scope): <subject>
```

Choose the type that best describes the commit:

| Type | Use for |
| --- | --- |
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation change |
| `style` | Formatting, missing semicolons, or no code behavior change |
| `refactor` | Code refactoring with no intended behavior change |
| `test` | Adding or refactoring tests |
| `chore` | Build work or package-manager change |
| `ci` | CI/CD-related change |

Use a scope only when it clarifies the affected area. Keep the subject concise and specific.

Always include a commit body. Describe the changes using bullet points, with one meaningful change per bullet:

```text
feat(order): add order status endpoint

- Add the order-status response DTO and service lookup.
- Expose the endpoint through the order controller.
- Cover the success response with a controller test.
```

Every `git commit` command must provide both the title and a bullet-point body; never create a title-only commit. For example:

```text
git commit -m "feat(order): add order status endpoint" -m $'- Add the order-status response DTO and service lookup.\n- Expose the endpoint through the order controller.'
```

## Safety

- Never use `git add .`, `git add -A`, or `git commit -am`.
- Never force-push unless the current user message explicitly requests it.
- Do not amend, rebase, reset, or otherwise rewrite history without explicit user authorization.
- Do not commit secrets, generated local files, or unrelated pre-existing changes. Ask for direction when ownership or intent is unclear.
- Report created commit identifiers and the final working tree state when the task is complete.
