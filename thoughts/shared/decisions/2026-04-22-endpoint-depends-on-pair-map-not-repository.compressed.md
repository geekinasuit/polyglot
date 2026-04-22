<!--COMPRESSED v1; source:2026-04-22-endpoint-depends-on-pair-map-not-repository.md-->
§META
date:2026-04-22 status:superseded author:claude(Architect) tickets:KT-006,KT-007
superseded-by:2026-04-22-endpoint-grpccallscope-and-map-injection.md

§SUMMARY
SUPERSEDED — inject BracketPairSource into BalanceServiceEndpoint for per-request freshness.
Reversed because root cause was @ApplicationScope misattribution on endpoint, not missing interface.

§ORIGINAL_DECISION
Option C chosen: inject BracketPairSource (narrow interface with get()) into ep.
ep calls bracketPairSource.get() once per balance() invocation.
Production impl: @ApplicationScope; queries DB on each call.
Rejected alternatives: inject repo directly (A); inject Map at startup (B); inject Provider<Map> (D).

§WHY_SUPERSEDED
ep was @ApplicationScope — wrong. dagger-grpc framework intends service handlers @GrpcCallScope.
BracketPairSource was workaround for the scope error, not a necessary design element.
With scope corrected, @GrpcCallScope mechanism provides per-request freshness directly.
See 2026-04-22-endpoint-grpccallscope-and-map-injection.md for current decision.
