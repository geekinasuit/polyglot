<!--COMPRESSED v1; source:2026-04-25-python-parity-implementation-plan.md-->
§META
date:2026-04-25 author:Qwen Code ticket:PY-001 status:draft

§GOAL
parity: Python full feature match to Kotlin reference; Python norms for libs, async, CLI, logging, telemetry

§CONTEXT
Python: core algo+tests; missing: gRPC svc/client, Bazel integration, observability
Kotlin: Armeria+Dagger+Clikt+OTel+coroutines reference

§APPROACH
complexity-parity sprints: coherent chunks impl+test+review independent
TDD throughout; test-first for new components

§STEPS

§SPRINT1: Foundations (Library Selection & Structure)
complexity:low — baseline no deep impl
tickets:PY-009 PY-003
deliver: finalize libs (requirements.txt MODULE.bazel); restructure Pythonic layout (packages tests/); update Bazel targets; merge foundation PR

§SPRINT2: gRPC Infrastructure
complexity:medium — core comm layer
tickets:PY-004 PY-002
deliver: py_grpc_library stubs; fix proto integration (imports foo()); basic gRPC channel/server tests; merge infra PR

§SPRINT3: Service Implementation
complexity:high — biz logic+server
tickets:PY-005 PY-007(service binary)
deliver: BalanceService w/ telemetry logging error handling; py_binary service; CLI host/port/env flags; unit tests; merge service PR

§SPRINT4: Client Implementation
complexity:medium — user client
tickets:PY-006 PY-007(client binary)
deliver: async client w/ telemetry; CLI matching Kotlin; stdin input stdout output; py_binary client; merge client PR

§SPRINT5: Testing & Integration
complexity:medium-high — validation cross-component
tickets:PY-008
deliver: comprehensive unit tests; integration client-server; pytest-asyncio async tests; e2e parity verify; final merge

§RISKS
async patterns Python vs Kotlin coroutines — validate integration tests
OTel setup complexity — test local OTLP collector
Bazel Python rules edge cases — iterate BUILD files

§DONE
all sprints complete; Python interoperable w/ Kotlin+others; full test coverage; exemplar Python quality