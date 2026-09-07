---
name: github-standards
description: Procedural rules for executing GitHub actions via the GitHub MCP server tools. Use this skill when managing issues, creating branches, checking PR statuses, and handling pull request structures.
compatibility: Requires GitHub MCP Server (Model Context Protocol) with Read/Write Repository access.
metadata:
  version: "1.0"
---

# GitHub Operational & Pull Request Playbook

## 1. Skill Boundaries & Setup Rules
* **Activation:** Trigger this skill the moment a user request involves Git, tracking issues, managing PRs, branches, or checking status checks on GitHub.
* **Credentials & Context:** Rely entirely on the configured GitHub MCP environment variables (do not search for or request passwords/tokens from the user). Resolve `owner` and `repo` from the local git remote (`git remote get-url origin`) or project context.

## 2. Issue Discovery & Context Gathering
Before starting any development task, use the GitHub MCP tools to read the underlying requirements:
* **Tool Invocation:** Use `issue_read` (with `method: "get"`) or `search_issues` to retrieve issue descriptions, acceptance criteria, and labels.
* **Ambiguity Safeguard:** If the target issue is ambiguous, contradictory, or lacks technical definition, do not proceed to write code. Instead, draft a detailed clarifying question and invoke `add_issue_comment` to request guidance from the issue author or human reviewer.
* **Tracking:** Note the exact `#IssueNumber` to pass down to downstream commit messages and PR documentation.

## 3. Branch Lifecycle Standards
* **Isolation Rule:** Never perform work directly on the `main` or `production` branch. Always isolate development within a dedicated feature branch.
* **Tool Sequence:** Call the `create_branch` tool to spin up a branch derived from the latest commit on `main` (or default base branch).
* **Naming Convention:** Enforce standard prefixes:
  - `feature/` -> For new functions or business capabilities (e.g., `feature/user-auth`, `feature/checkout-flow`)
  - `bugfix/` -> For resolving existing issues or bugs (e.g., `bugfix/fix-jwt-expiry`, `bugfix/stock-race-condition`)
  - `refactor/` -> For code cleanup or structural upgrades without behavioral changes (e.g., `refactor/split-commerce-service`)

## 4. Pull Request Formulation
Once all local validation pipelines pass (e.g. `mvn clean test` and `mvn test -Dtest=ArchitectureTest`), use the `create_pull_request` tool. Your PR details must be explicitly structured to match this template:
* **PR Title:** Follow the Conventional Commits format in an imperative tone (e.g., `feat: integrate spring security context`, `fix: resolve optimistic locking on inventory`).
* **PR Description Body:**
  ```markdown
  ### 📝 Summary of Changes
  - [Short bulleted list of actual changes made by the agent]

  ### 🔗 Linked Issues
  - Closes #IssueNumber (Using "Closes" ensures GitHub auto-closes the issue when merged)

  ### 🧪 Verification Performed
  - Local validation run command: [Specify exact tool or maven command run, e.g. mvn clean test]
  ```

## 5. Security & Secret Safeguards
* **Anti-Pattern:** You must never stage or commit `.env`, private cryptographic keys, hardcoded passwords, or unredacted personal identifiers (PII).
* **Tool Validation:** If modified files include configuration or environment files, verify they match patterns defined in `.gitignore` before calling MCP tools to push changes or open a pull request.

## 6. Critical Operational Boundaries (Human-In-The-Loop)
* **Automated Merge Restriction:** You are strictly prohibited from calling the `merge_pull_request` tool autonomously.
* **Hand-off Signal:** After successfully drafting a PR via `create_pull_request`, complete your current chat execution loop by outputting a clean declaration to the human user:
  > *"PR #{number} has been drafted and verified against local standards. It is currently blocked by your Repository protection rules. Please manually review the code changes on GitHub to approve and complete the merge sequence."*

