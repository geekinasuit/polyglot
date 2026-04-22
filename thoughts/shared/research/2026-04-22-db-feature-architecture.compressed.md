<!--COMPRESSED v3; source:2026-04-22-db-feature-architecture.md; revised:2026-04-22-->
§META
date:2026-04-22 revised:2026-04-22 researcher:claude(Architect) topic:Kotlin DB feature architecture
tickets:KT-005,KT-006,KT-007 status:complete(revised)

§ABBREV
src=.md cmp=.compressed.md
ep=BalanceServiceEndpoint repo=BracketPairRepository(interface)
bst=BracketsServiceTelemetry dbc=DatabaseConfig sac=ServiceAppConfig
dsl=DSLContext ds=DataSource
as=@ApplicationScope gcs=@GrpcCallScope
bm=BracketConfigModule dm=DatabaseModule tdm=TestDatabaseModule
gcsgm=GrpcCallScopeGraphModule tgcsgm=TestGrpcCallScopeGraphModule
tag=TestApplicationGraph ag=ApplicationGraph

§REVISION
v2(initial 2026-04-22): BracketPairSource interface; ep @as; BracketConfigModule
v3(this revision 2026-04-22): ep corrected to @gcs; BracketPairSource+bm removed;
  gcsgm provides Map<Char,Char>; bst wrapper introduced for OTel instruments

§SUMMARY
Three-layer Kotlin DB architecture: DB adapter(ds+flyway+dsl), repository(jooq queries behind interface), service(ep receives Map<Char,Char> per-request).
ep is @gcs — corrects bug vs prior @as; matches dagger-grpc framework intent.
ep receives Map<Char,Char> injected by gcsgm once per subcomponent creation(per-request freshness).
gcsgm calls repo.loadEnabledPairs() at subcomponent creation — one DB call per request.
BracketPairSource interface removed — @gcs mechanism already provides per-request freshness.
bm removed — no remaining responsibility; Map<Char,Char> provided by gcsgm.
bst(@as): holds Tracer+LongCounter+LongHistogram; prevents per-request OTel instrument construction.
ep constructor: (pairs:Map<Char,Char>, telemetry:bst)
Repository interface required — isolates JOOQ; enables fakes; is coroutine migration boundary.
Flyway runs inside ds provider; Dagger DAG enforces sequencing.
ep fully shielded from async migration: only gcsgm changes if repo becomes suspend.

§LAYERS
fixed: three layers
1. DB Adapter — ds+flyway+dsl; no biz logic; provided by dm
2. Repository — jooq queries; maps records→Map<Char,Char>(close→open); hidden behind interface; JOOQ never leaks upward
3. Service — ep(@gcs) receives Map<Char,Char>+bst; calls balancedBrackets; no db/repo knowledge

Collaboration chain:
  ep(@gcs)←Map<Char,Char>(gcsgm @Provides; calls repo.loadEnabledPairs() per subcomponent)
          ←bst(@as; holds Tracer+LongCounter+LongHistogram)
  repo(@as impl)←dsl←ds(+flyway)←dbc←sac

§DAGGER
fixed: topology; open: exact provider signatures

dm(DatabaseModule):
  extracts dbc from sac via @Provides unwrapper
  provides ds:@as — HikariCP pool + Flyway.migrate() inside provider
  provides dsl:@as — dialect inferred from JDBC URL
  installed in ag(prod) and overridden by tdm in tag(tests)

gcsgm(GrpcCallScopeGraphModule — extended):
  includes GrpcCallContext.Module(unchanged)
  @Provides Map<Char,Char>:@gcs — calls repo.loadEnabledPairs(); fresh per request[FIXED]
  qualifier recommended(raw Map type; future collision risk); TDD Designer chooses form
  test replacement: tgcsgm provides fixed Map<Char,Char> without DB query

TelemetryModule(extended or BracketsTelemetryModule added):
  @Provides bst:@as — constructs bst(tracer+counter+histogram) from OpenTelemetry
  instruments initialized once at startup; not per-request[FIXED]
  TDD Designer decides placement(TelemetryModule or new module) and bst exact name

REMOVED bm(BracketConfigModule): no remaining responsibility

ag modules: [ApplicationGraphModule,GrpcHandlersModule,InterceptorsModule,TelemetryModule,ServerModule,dm]
ag GrpcCallScopeGraph subcomponent: [gcsgm]
tag modules: [TestApplicationGraphModule,GrpcHandlersModule,InterceptorsModule,FakeTelemetryModule,ServerModule,tdm]
tag GrpcCallScopeGraph subcomponent: [tgcsgm]

§REPOSITORY
fixed: interface required; open: name+signatures

Responsibilities: query bracket_pair where enabled=true; map JOOQ records→Map<Char,Char>(close→open); return domain type
Not responsible for: caching(gcsgm provides scoped map per request); connection mgmt(HikariCP); migrations(Flyway); biz logic

BracketPairSource removed — @gcs mechanism provides per-request freshness directly
  gcsgm calls repo.loadEnabledPairs() at subcomponent creation = once per balance() invocation
  ep unit tests: construct directly with Map<Char,Char> literal — no fake interface needed
  gcsgm unit tests: fake repo(in-memory impl of repo interface) — no H2; no Dagger

Interface required rationale:
  1. gcsgm unit tests need fake repo(empty map→error path) without H2; no interface exposure to ep
  2. JOOQ swap or async migration → only impl changes; gcsgm+ep unchanged
  3. coroutine migration boundary(fun→suspend fun confined to repo interface+gcsgm provider)

Constraint(fixed): JOOQ types+DSLContext never cross interface boundary upward

§LIFECYCLE
fixed

Dagger construction order:
  1. ag.build() triggered
  2. ds provider: configure HikariCP → run Flyway.migrate() → return pool
  3. dsl provider: wrap ds(already migrated) with dialect
  4. repo impl constructed: receives dsl; scoped @as
  5. [request time] armeria calls gRPC adapter → supplier.callScope() creates @gcs subcomponent
  6. gcsgm @Provides Map<Char,Char>: calls repo.loadEnabledPairs() — DB queried once per request
  7. ep constructed with resolved Map<Char,Char>+bst; handles request; subcomponent discarded

Flyway config(fixed — from DBA):
  .sqlMigrationPrefix("").sqlMigrationSeparator("_")
  .locations("filesystem:$migrationsPath")
  migrationsPath = Paths.get(System.getenv("RUNFILES_DIR")?:".", "_main/db/migrations").toAbsolutePath()

Bazel targets running Flyway: data=["//db:migrations"] required

§COROUTINES
fixed: boundary location; open: whether to add suspend now

Current: repo returns Map<Char,Char> synchronously; blocking on gRPC thread is acceptable
Primary boundary: repo interface — only place suspend is added in future migration
ep: FULLY SHIELDED — receives Map<Char,Char>; no interface method to call; unchanged in async migration
gcsgm: one provider method adapts(calls repo in coroutine context); bounded change
bst: unchanged in async migration

Constraint(fixed): no JOOQ types/blocking DB calls above repo interface

§TEST_SURFACES
fixed: layer strategy; open: test class names+fake impls

Repository layer:
  test type:integration(H2 in-memory); no Dagger
  construct repo impl directly with H2 DSLContext
  H2 URL: jdbc:h2:mem:<unique-name>;DB_CLOSE_DELAY=-1[REQUIRED]
  Flyway applied in test setup(same migration config as prod)
  cases: enabled pairs loaded; disabled pairs excluded; empty→empty map; mapping direction correct

Service layer(ep):
  test type:unit; no DB/repo/Dagger
  construct ep with Map<Char,Char> literal + no-op bst(wraps OpenTelemetry.noop())
  cases: known pairs→correct result; empty pairs→error response; unbalanced→correct error; unknown chars→plain text

gcsgm:
  test type:unit; fake repo(in-memory impl of repo interface)
  gcsgm @Provides Map<Char,Char> — tests verify map via fake repo
  cases: empty result→error; non-empty→correct map

Dagger graph(integration):
  tdm replaces dm; tgcsgm replaces gcsgm(for non-DB integration tests)
  tag builds with H2 dbc; repo(or interface) injectable
  end-to-end: disable pair in test H2→characters treated as plain text(uses tdm+real gcsgm)

tdm responsibilities: ds via jdbc:h2:mem:...;DB_CLOSE_DELAY=-1; dbc with H2 URL; Flyway on H2 in ds provider
tgcsgm responsibilities: fixed Map<Char,Char>(same qualifier as gcsgm; no DB query); replaces gcsgm in unit-style integration tests

§DEFERRED
TDD Designer decides (no Architect sign-off needed unless conflicts with fixed above):
  repo interface name+method signatures
  qualifier for Map<Char,Char> in gcsgm(recommended; TDD Designer chooses @BracketPairs or @Named)
  bst exact name+placement(TelemetryModule vs new module; data class vs plain class vs interface)
  bst API surface: consider delegating relevant instrument API methods directly(e.g. bst.recordRequest(attrs)) rather than exposing instrument fields; call-site ergonomics+fake simplicity are both improved; judgment call where APIs don't collide
  no-op bst impl for ep unit tests
  repo test class placement+naming
  fake repo impl details(for gcsgm tests)
  tdm exact provider signatures
  tgcsgm shape(how fixed Map<Char,Char> is configured per-test)
  error shape when no pairs enabled(gRPC status code+message)
  H2 unique db name strategy per test class

FIXED (cannot be deferred; escalate conflicts to Architect):
  three-layer boundary
  ep is @gcs(not @as) — corrects framework misuse bug
  ep depends on Map<Char,Char>(injected per-request by gcsgm) and bst(@as)
  BracketPairSource interface does not exist
  bm does not exist
  gcsgm @Provides Map<Char,Char> by calling repo.loadEnabledPairs()(once per subcomponent creation)
  repo interface required
  JOOQ never leaks past repo interface
  qualifier on Map<Char,Char> recommended(TDD Designer confirms and chooses form)
  Flyway runs inside ds provider
  DB_CLOSE_DELAY=-1 in all test H2 URLs
  tgcsgm replaces gcsgm in integration tests that do not exercise DB behavior
  tdm replaces dm in test graph(not TestApplicationGraphModule)
  bst is @as; holds Tracer+LongCounter+LongHistogram; provided from TelemetryModule or dedicated module
