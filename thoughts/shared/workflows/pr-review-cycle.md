# PR Review Cycle Workflow

Standard process for taking a local bookmark through review and merge.

## Steps

### 1. Pre-push verification
- Run full test suite locally: `bazel test //...`
- Fix any failures before pushing (saves CI resources)

### 2. Push and open PR

> **Note**: This repo runs the `stilliard/github-task-list-completed` GitHub App. It scans
> **every** body for unchecked checkboxes: the PR description, all PR comments, all review
> submissions, and all inline review comments. Any unchecked `- [ ]` blocks the check.
>
> - Check boxes immediately after verifying (`- [x]`), or mark inapplicable ones with `N/A`,
>   `POST-MERGE`, or `OPTIONAL` (all caps, inline) to exclude them.
> - **Never put unchecked checkboxes in review comments** — use prose instead.
- After `jj commit`, the new commit sits at `@-` (the empty working copy is `@`). Set bookmark to that commit: `jj bookmark set <name> -r @-`
- Push: `jj git push -b <name>`
- If no PR exists: `gh pr create --repo geekinasuit/polyglot --title "..." --body "$(cat <<'EOF' ... EOF)"`

### 3. Critique agent (foreground — wait for completion)

Run the critique agent **first and wait for it to finish** before launching the response agent.
The response agent needs the critique agent's posted comments to exist on GitHub before it runs;
running them in parallel means the response agent sees an empty comment list.

**Critique agent** (subagent_type: general-purpose):
> "You are a code reviewer. Review PR #N at https://github.com/geekinasuit/polyglot/pull/N.
>
> **Step 0 — Scope check (do this first, before any inline review):**
> Compare the PR title and description against the actual diff (`gh pr diff <N>`).
> Check whether the diff contains changes that are NOT described — files modified, added, or
> deleted that have no relationship to the stated purpose of the PR. This can happen when a
> branch is based on another in-flight branch and a squash merge captures ancestor changes
> unintentionally. If you find out-of-scope changes, post a single top-level PR comment
> (not an inline comment) flagging the discrepancy and listing the unexpected files/hunks.
> **Stop and report 'SCOPE MISMATCH' to the caller** — do not proceed with inline review
> until the scope issue is resolved.
>
> **Step 1 — Inline review (only if scope check passes):**
> Read every changed file. Post inline review comments on GitHub for any real issues (bugs,
> security, correctness, style violations, misleading names). Do NOT post praise or nitpicks.
> Use `gh api` to post comments. If there are no issues, output 'LGTM - no issues found' and
> do nothing."

If the critique agent reports **SCOPE MISMATCH**, stop. Notify the PR author to rebase or
re-target the branch so the diff matches the stated purpose, then re-run from Step 3 once
the scope is corrected. Do not proceed with the response agent until the scope check passes.

If the critique agent reports LGTM, skip to Step 8.

### 4. Response agent (after critique agent completes)

**Response agent** (subagent_type: general-purpose):
> "Read PR #N at https://github.com/geekinasuit/polyglot/pull/N. Collect all open review
> comments (from the critique agent AND any AI/human reviewers). For each comment, either
> propose a concrete code fix or explain why no change is needed. Apply fixes directly to
> the files. Do NOT push — report back what was changed and what was left as-is with reasons."

### 5. Evaluate responses
- If response agent made changes: run `bazel test //...`, then push, then re-run from Step 3
- If no changes needed (all LGTM): proceed to Step 6

### 6. Pause for human input if:
- Response agent flagged any comment as requiring design/product judgment
- Tests fail after applying fixes
- Conflicting review comments

### 7. Resolve all review threads

After replying to every comment, resolve each thread — replies alone do not unblock merge:

```bash
# Find unresolved thread IDs
gh api graphql -f query='query($owner:String!,$repo:String!,$pr:Int!){repository(owner:$owner,name:$repo){pullRequest(number:$pr){reviewThreads(first:50){nodes{id isResolved}}}}}' \
  -f owner=geekinasuit -f repo=polyglot -F pr=<N> \
  --jq '.data.repository.pullRequest.reviewThreads.nodes[]|select(.isResolved==false)|.id'

# Resolve each one
gh api graphql -f query='mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{isResolved}}}' \
  -f t=<thread_id>
```

### 8. Merge

When all checks pass:
```
gh pr merge <N> --repo geekinasuit/polyglot --squash --auto
```

> **IMPORTANT — CI failures before merge**: If any check is failing, **stop and report to the
> user regardless of any merge permission already granted**. Permission to merge (i.e. permission
> to proceed without further human review) is NOT permission to merge through failing CI.
>
> **Exception — purely textual, non-load-bearing PRs**: A PR may be merged through *infra*
> CI failures if and only if *every* changed file meets all of these criteria:
> 1. It has a purely prose file extension (`.md`, `.txt`) **or** is a well-known prose file
>    by name/path (e.g. files under `thoughts/`, `AGENTS.md`, `README`, `LICENSE`). A file
>    under `thoughts/` that has a non-prose extension (e.g. `.sh`, `.yaml`) does **not**
>    qualify on path alone.
> 2. It is **not** referenced by any Bazel target (not test data, not website source, not
>    generated into any build output)
> 3. It is **not** a CI/build config file (`.github/`, `.buildkite/`, `.bazelrc`, `MODULE.bazel`,
>    `BUILD.bazel`, `*.bzl`, `Makefile`, etc.)
>
> When this exception applies, **infra failures only** may be treated as non-blocking. Still
> classify the failure (infra vs. real) and note it in your report to the user. Real
> build/test failures (e.g. a markdown-lint or link-checker step actually failing on changed
> content) are blockers even for textual PRs and must be reported and addressed.
>
> For all other PRs, every failing check is a blocker until the user explicitly addresses it:
>
> - **Infra failures** (e.g. Docker image not found, agent offline): report to the user so they
>   can investigate and mitigate — do not merge until they confirm the infra issue is understood
>   and accepted for this specific PR.
> - **Actual build/test failures**: must be diagnosed and fixed before merging.
>
> Describe which checks passed and which failed, classify each failure as infra vs. real, and
> ask the user how to proceed. Do not use `--admin` or any other bypass flag without explicit
> one-time permission for that specific PR.

### 9. Fetch and rebase after every merge

**Always do this immediately after a merge**, before starting the next PR:
```
jj git fetch
jj bookmark set main -r main@origin
# Rebase each in-flight bookmark onto the new main (one per bookmark):
jj rebase -b <next-bookmark> -d main
```

`jj rebase -d main` without `-b` only moves `@` (the working copy commit), not other
in-flight bookmarks. Use `-b <bookmark>` for each bookmark that needs rebasing.
jj will automatically abandon commits whose change IDs already exist on `main`
(squash-merged bookmarks), preventing them from accumulating as stale divergent heads.

## Notes
- One PR at a time: wait for each merge before pushing the next bookmark that depends on it.
