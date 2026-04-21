---
id: CI-001
title: GitHub Action to sync ticket state on issue close
area: automation, github, tickets
status: open
created: 2026-03-06
---

# Feature: GitHub Action to sync ticket state on issue close

## Summary

Implement a GitHub Actions workflow that acts as a safety net for keeping ticket files and `TICKETS.md` in sync when a GitHub issue is closed. The primary mechanism for ticket updates is including them in the PR branch itself; this action handles cases where that didn't happen (manual closes, hotfixes, agent oversights).

## Trigger

`issues` event, `closed` activity type.

## Behavior

1. Parse the closed issue body for the standardized ticket reference line:
   ```
   **Ticket:** `thoughts/shared/tickets/<filename>.md`
   ```
2. If no ticket reference found: no-op (not all issues are linked to ticket files).
3. If ticket file is found and already has `status: resolved`: no-op (PR already did the right thing).
4. If ticket file exists and still has `status: open`:
   - Update the ticket file: set `status: resolved`, add `github_issue: <N>`, add `resolved_date: <date>`, add a `resolution` note referencing the closing PR/issue.
   - Update `TICKETS.md`: move the row from the Open table to the Resolved section.
   - Commit directly to `main` with message: `chore: resolve ticket <filename> (issue #N closed)` — this is an explicit, permitted exception to the no-direct-push rule for bookkeeping-only automation commits.
5. If ticket file does not exist (already deleted or never created): update `TICKETS.md` only if the issue title appears in the Open table.

## Design Notes

- The action must have write access to the repository contents (a `GITHUB_TOKEN` with `contents: write` permission is sufficient for commits to `main`).
- The direct-to-`main` commit is intentional and documented as an exception granted specifically to this GitHub Actions bot. It must be clearly attributable (action bot committer identity) and strictly scoped to ticket file + TICKETS.md changes — no other files. This exception does not extend to AI agents: an agent that notices a ticket is out of sync must open a branch and PR.
- Consider whether the action should also move the ticket file to a `thoughts/shared/tickets/resolved/` archive directory rather than deleting it, to preserve history without cluttering the open tickets list.

## References

- `AGENT.md` — "Safety Net: GitHub Action for Missed Ticket Updates" section
- `thoughts/shared/tickets/TICKETS.md` — "How This Works with GitHub Issues" section
- `.github/workflows/` — action workflow file to be created here
