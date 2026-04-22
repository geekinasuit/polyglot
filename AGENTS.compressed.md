<!--COMPRESSED v1; source:AGENTS.md-->
§META
layer:repo scope:polyglot-repo

§ABBREV
ts=thoughts/shared
opt=/opt/geekinasuit/agents int=$opt/internal pub=$opt/public
kt=kotlin

§PURPOSE
exemplar project: idiomatic production-quality gRPC+Protobuf across multiple langs, single shared schema, Bazel build, IDE integration
goal: good example of how to do things well, not just proof-of-concept

§DOCS
$ts/ docs have two forms: <name>.md(human) + <name>.compressed.md(token-efficient,lossless)
compressed uses §SECTION markers + §ABBREV table at top

§NOINLINE [NON-OPTIONAL — mechanical harness enforcement; violations block every call requiring hand-permission]
FORBIDDEN in any Bash tool call:
  heredocs: cmd << 'EOF'...EOF
  inline multi-line strings: -m "line1\nline2" or -F body="text with `backticks`"
  command substitution: cmd $(other_cmd)
  any string containing backticks|---| asterisks passed as a shell argument
REQUIRED pattern — no exceptions:
  write body/prompt/message → /tmp/<repo>-<branch>-<purpose>.md via Write tool
  reference by path: --body-file /tmp/... | -F body=@/tmp/... | Agent prompt:"Read instructions from /tmp/..."
unique tmp names: include repo+purpose+context (e.g. /tmp/polyglot-pr-body-32.md); never /tmp/pr-body.md — collides
use Write tool for /tmp files (unique names prevent exists-error); cat > heredoc is also FORBIDDEN
THIS RULE APPLIES TO SUBAGENTS TOO: embed it verbatim at the top of every prompt written to /tmp before spawning

§LAYOUT
$ts/research/   read before implementing
$ts/tickets/    TICKETS.md index + ticket files; check before starting new work
$ts/plans/      implementation plans
$ts/handoffs/   session handoff documents
before non-trivial impl: check $ts/tickets/TICKETS.md + $ts/research/ for prior work

§TICKETS
TICKETS.md=canonical index; do NOT maintain ticket inventories elsewhere
any ticket file create|modify|resolve|delete → update TICKETS.md in same op

ID categories (sequential within each):
  GO JAVA KT PY RUST TS = language-specific work
  BUILD = build system+tooling(Bazel,bzlmod,rules)
  CI = CI,automation,testing infrastructure
  OPT = performance optimization
  AGENT = agentic workflow+tooling changes
  check existing tickets in category for next number

ticket file conventions:
  filename: <ID>-<kebab>.md in $ts/tickets/
  frontmatter: id title area status(open|resolved) created; optional: github_issue:N
  required sections: Summary, Current State|Resolution, Goals|acceptance criteria, References(file:line)
  resolved ONLY when ALL work complete(incl. manual|migration|verification steps)

GitHub Issues:
  create when starting work on ticket(not before, not at PR-open)
    $kt $opt/tools/gh-ticket.main.kts -- create <ticket-path>
  PR ref: "see #N" — NEVER fixes|closes; close manually at full DoD

Safety net: GitHub Action(CI-001-github-action-ticket-close-sync.md) may commit direct→main for bookkeeping only
  exception is bot-only; agents MUST still use branch+PR for any ticket sync

§IMPL
$kt = reference implementation
  model gRPC service/client design, Bazel build structure, CLI (CliktCommand) after $kt variant
  all langs must be interoperable: $kt client ↔ any-lang server; any-lang client ↔ $kt server

§BUILD
dual-build: every lang variant → Bazel(canonical) + native tool(ergonomics/IDE)
  Go: go modules+go build|make; Java: Maven|Gradle; TS: yarn|npm; Rust: cargo; Python: pip|pyproject
  neither build requires the other
  file in both graphs → add comment explaining dual role

Bazel conventions:
  bzlmod only (MODULE.bazel); no WORKSPACE
  --enable_workspace=true = compatibility shim for grpc_kotlin only; remove when bzlmod-native
  all $kt executables: java_binary(runtime_deps=...) not kt_jvm_binary
  package-pinning BUILD.bazel at lang roots: intentional; do not add targets without reason
  NEVER commit generated proto code → .gitignore
  $kt tests: associates=[...] for internal member access; do not change visibility to work around

proto as contract boundary:
  //protobuf:protobuf(example.proto) + //protobuf:balance_rpc(brackets_service.proto) = sole canonical sources
  contract changes: proto first → propagate to all lang variants

§TESTING
prefer unit tests first; bracket algorithm=pure functions, no infrastructure needed
prefer fakes|stubs over mock frameworks; mocks couple to impl; fakes test behavior
  mock frameworks: sparingly, only for boundaries you don't own

integration+contract tests:
  prefer pairwise contract tests(client↔server, one lang) over full multi-lang suites
  cover use-case scenarios(balanced,unbalanced,error paths,connection failure); not coverage metrics
  cross-lang interop: scope to contract surface only

test parity: each lang=equivalent coverage
  baseline(Go+Java): 8 cases — empty, balanced, unclosed opener, extra closer, mismatch, complex sentence, mixed, complex mismatch

§STYLE
ktfmt(Google/Meta formatter, default style) on ALL $kt files before commit:
  ktfmt <changed-kt-files>  |or|  ktfmt $(find $kt -name "*.kt")
  install: brew install ktfmt (macOS)
  formatting-only commit=valid standalone PR; never mix formatting with behavioral changes
  if editing unformatted file: format in separate preceding commit

idiomatic first, structurally familiar second:
  each lang: idiomatic naming+layout+error handling+tooling; do NOT mechanically port $kt idioms
  mirror $kt structure where natural: algo lib|service|client|tests; analogous depth+naming
  CLI: same flags(--host,--port,input arg)+same defaults across all langs
  error+success output: semantically equivalent across langs
  shared proto interface+error message strings: fixed; do not diverge

minimal deps: std lib or well-established ecosystem libs; no heavy frameworks for simple tasks
  Armeria: intentional, considered — do not change without clear reason
no interceptors yet: wrapService() has vararg interceptors hook; none passed; ticketed; do NOT add speculatively

§RESEARCH
research docs: $ts/research/ | YYYY-MM-DD-<description>.md + .compressed.md companion; always produce both
frontmatter: date researcher git_commit branch repository topic tags status last_updated last_updated_by

§REFS
| Concern | Reference |
|---|---|
| gRPC service impl | $kt/src/main/$kt/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt |
| gRPC server startup | $kt/src/main/$kt/com/geekinasuit/polyglot/brackets/service/service.kt |
| gRPC client | $kt/src/main/$kt/com/geekinasuit/polyglot/brackets/client/client.kt |
| Bazel binary target | $kt/BUILD.bazel — java_binary + runtime_deps |
| Bazel proto codegen | $kt/BUILD.bazel — kt_jvm_proto_library + kt_jvm_grpc_library |
| Unit tests | $kt/src/test/$kt/bracketskt/BracketsTest.kt |
| Custom Bazel macro | util/kt_jvm_proto.bzl |
| Proto definitions | protobuf/brackets_service.proto, protobuf/example.proto |

§CHAIN Bootstrap — execute exactly one branch (stop after match):
when {
  $int/AGENTS.compressed.md exists → read+follow; exit chain
  $int/AGENTS.md exists → read+follow; exit chain
  $pub/AGENTS.compressed.md exists → read+follow; exit chain
  $pub/AGENTS.md exists → read+follow; exit chain
  ~/.geekinasuit/agents/public/AGENTS.compressed.md exists → read+follow; exit chain
  ~/.geekinasuit/agents/public/AGENTS.md exists → read+follow; exit chain
  else → skip; bootstrap complete
}
Do not read multiple branches. Stop immediately after first match.
If none exist: skip — not required for contributor work; will not be present on most machines.
