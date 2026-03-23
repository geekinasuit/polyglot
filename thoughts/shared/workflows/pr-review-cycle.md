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

### 3. Parallel review agents
Spin two agents **in parallel** (single message, two Agent tool calls):

**Critique agent** (subagent_type: general-purpose):
> "You are a code reviewer. Review PR #N at https://github.com/geekinasuit/polyglot/pull/N.
> Read every changed file. Post inline review comments on GitHub for any real issues (bugs,
> security, correctness, style violations, misleading names). Do NOT post praise or nitpicks.
> Use `gh api` to post comments. If there are no issues, output 'LGTM - no issues found' and
> do nothing."

**Response agent** (subagent_type: general-purpose):
> "Read PR #N at https://github.com/geekinasuit/polyglot/pull/N. Collect all open review
> comments (from the critique agent AND any AI/human reviewers). For each comment, either
> propose a concrete code fix or explain why no change is needed. Apply fixes directly to
> the files. Do NOT push — report back what was changed and what was left as-is with reasons."

### 4. Evaluate responses
- If response agent made changes: run `bazel test //...`, then push, then re-run review agents
- If no changes needed (all LGTM): proceed to merge

### 5. Pause for human input if:
- Response agent flagged any comment as requiring design/product judgment
- Tests fail after applying fixes
- Conflicting review comments

### 6. Resolve all review threads

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

### 7. Merge
```
gh pr merge <N> --repo geekinasuit/polyglot --squash --auto
```
Then fetch and update local main:
```
jj git fetch
jj bookmark set main -r main@origin
```

## Notes
- One PR at a time: wait for each merge before pushing the next bookmark that depends on it.
