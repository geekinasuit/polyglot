<!--COMPRESSED v1; source:2026-04-22-endpoint-grpccallscope-and-map-injection.md-->
§META
date:2026-04-22 status:accepted author:claude(Architect) tickets:KT-006,KT-007
supersedes:2026-04-22-endpoint-depends-on-pair-map-not-repository.md

§SUMMARY
Corrects BalanceServiceEndpoint from @ApplicationScope → @GrpcCallScope per dagger-grpc framework intent.
Removes BracketPairSource(was workaround for wrong scope); removes BracketConfigModule(no remaining responsibility).
Introduces BracketsServiceTelemetry(@ApplicationScope) wrapper for OTel instruments.

§DECISIONS
1. ep=@GrpcCallScope — bug fix; dagger-grpc framework intends service handlers in this scope
2. BracketPairSource removed — per-request freshness provided by @GrpcCallScope mechanism directly
3. BracketsServiceTelemetry(@as) — holds Tracer+LongCounter+LongHistogram; constructed once at startup
4. BracketConfigModule removed — no remaining responsibility
5. GrpcCallScopeGraphModule provides Map<Char,Char>:@gcs by calling repo.loadEnabledPairs() per subcomponent creation

§RATIONALE
@GrpcCallScope subcomponent created once per gRPC request → any binding in that scope is fresh per request
→ Map<Char,Char> is simply a scoped binding; BracketPairSource.get() was simulating what Dagger already provides
ep receives resolved Map<Char,Char> at construction; no interface method called at request time
OTel instruments are logically application-scoped; constructing per-request is functionally safe but architecturally wrong
Qualifier on Map<Char,Char> recommended — raw Map type risks future duplicate binding error

§CONSEQUENCES
[+] ep in correct Dagger scope; matches dagger-grpc framework usage
[+] ep fully shielded from async repo migration — only gcsgm provider method adapts
[+] ep unit tests simpler — Map<Char,Char> literal instead of fake interface
[+] BracketConfigModule removed
[-] GrpcCallScopeGraphModule now has runtime dep (repo); acceptable — its role is to provide per-request data
[-] TestGrpcCallScopeGraphModule is new — replaces GrpcCallScopeGraphModule in non-DB integration tests
