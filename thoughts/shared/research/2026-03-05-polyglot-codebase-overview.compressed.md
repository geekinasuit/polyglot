<!--COMPRESSED-RESEARCH v1; reconstruct→2026-03-05-polyglot-codebase-overview.md-->
§META
date:2026-03-05T00:00:00-08:00 commit:3b2a7997f63a0a8c39747000a9e9daa4b291e9e5
branch:HEAD(detached) repo:polyglot researcher:claude-opus-4-6 status:complete
topic:codebase-overview(KT-GRPC,BZL,multi-lang)

§ABBREV
pb=protobuf/ k=kotlin/ g=golang/ j=java/ py=python/ r=rust/ u=util/
smk=src/main/kotlin/ cgpb=com/geekinasuit/polyglot/brackets/
BSE=BalanceServiceEndpoint BB=BalanceBrackets BR=BalanceRequest BRes=BalanceResponse
KT=Kotlin BZL=Bazel MOD=MODULE.bazel RC=.bazelrc GRPC=gRPC
//pb=//protobuf BZL-target

§PURPOSE
Multi-lang exemplar: balanced-bracket-check algo in Go/Java/KT/Py/Rust. Single proto schema. BZL bzlmod.
KT=ONLY full GRPC svc+client; others=algo+proto-msg only.
Server:Armeria(NOT grpc-netty). Client:grpc-kotlin CoroutineStub. Custom Starlark macro for KT proto-only codegen.

§TREE
ROOT: MOD(116L,bzlmod-all-langs) BUILD.bazel(empty,pins-root) RC Makefile(native-Go-only)
  go.work(2mods) Cargo.toml(1member:r/bracketslib) requirements.txt(pytruth,protobuf,six,wheel)
pb/: BUILD.bazel(proto_library::protobuf+:balance_rpc) example.proto brackets_service.proto
k/: PRIMARY-full-GRPC
j/ g/ py/ r/: algo+proto-msg-only [py:proto-import-disabled]
u/: BUILD.bazel(deliberate-empty) kt_jvm_proto.bzl(custom-macro)
thoughts/: scaffold-only(shared/research,plans,handoffs,tickets)

§KT_TREE
k/BUILD.bazel: proto/grpc-codegen-targets+java_binary-wrappers
k/smk/bracketskt/: BUILD.bazel(kt_jvm_lib+custom-kt_jvm_proto_lib) brackets.kt(algo-lib)
k/smk/cgpb/client/: BUILD.bazel(client_lib) client.kt
k/smk/cgpb/service/: BUILD.bazel(service_lib) BSE.kt(svc-impl) service.kt(Armeria-startup)
k/test/kotlin/bracketskt/: BUILD.bazel BracketsTest.kt

§KT_SERVER
FILE:k/smk/cgpb/service/BSE.kt
impl: BalanceBracketsGrpc.AsyncService+io.grpc.BindableService
balance(): StreamObserver-CALLBACK(NOT-coroutines); onNext(L37)+onCompleted(L38)
builder: BRes.newBuilder().apply{}.build()
resp-logic: ok→isBalanced=T,succeeded=T | BracketsNotBalancedException→isBalanced=F,succeeded=T,error=msg | Exception→succeeded=F,error=msg
bindService(L41): delegates→BalanceBracketsGrpc.bindService(this)

FILE:k/smk/cgpb/service/service.kt
class BracketsService:CliktCommand; opts: host(def=localhost) port(def=8888) [delegated-props]
server(L26): Armeria Server.builder().http(port).service(wrapService(BSE())).build()
lifecycle: closeOnJvmShutdown()+start().join()[blocking-main]
wrapService(L34-39): (BindableService,vararg ServerInterceptor)→GrpcService.builder().addService().intercept(*interceptors).build() [always-empty;hook-only]
main(L32): fun main(vararg args)=BracketsService().main(args) →synthesizes ServiceKt

§KT_CLIENT
FILE:k/smk/cgpb/client/client.kt
class BracketsClient:CliktCommand; opts: host(def=localhost) port(def=8888) text(inputStream-arg)
run(): runBlocking{} [bridges Clikt-blocking→coroutine-scope]
channel(L36): ManagedChannelBuilder.forAddress(host,port).usePlaintext().build() [grpc-java,NO Armeria]
stub(L37): BalanceBracketsGrpcKt.BalanceBracketsCoroutineStub(channel)
call(L41): stub.balance(request) [suspend-fn inside runBlocking]
resp: when{!succeeded→stderr | isBalanced→"balanced" | else→"NOT balanced:error"}
main: fun main(vararg args)=BracketsClient().main(args) →synthesizes ClientKt

§KT_PATTERNS
ext-fn: brackets.kt:41 ArrayDeque<E>.push=addLast
apply: BSE.kt:18 builder-inline
runBlocking: client.kt:28 Clikt-run()→coroutine
trailing-lambda: service.kt:27 thenRun{log.info{}}
elvis-throw: brackets.kt:27 removeLastOrNull()?:throw
vararg+spread: service.kt:35,38 interceptor-hook
delegated-cli: client.kt:24-26,service.kt:21-22 by option()/argument()
proto-dsl: brackets.kt:44-49 something{id=2;labels+="a"}
kt-logging: service.kt:27 log.info{"..."}
withIndex: brackets.kt:20 for((i,c)in...)
in-when: brackets.kt:22,25 in openParentheses

§KT_LIBS
armeria+armeria-grpc:1.26.4 HTTP2-server
grpc-netty-shaded:1.71.0 client-transport
grpc-kotlin-stub:1.4.1 CoroutineStub-base
kotlinx-coroutines-core:1.10.1 runBlocking
clikt-jvm:5.0.1 CLI
kotlin-logging-jvm:5.1.0 SLF4J-wrapper
protobuf-kotlin:4.29.3 proto-DSL-extensions
slf4j-jdk14:2.0.11 runtime-only(client+service)
truth:1.4.4+junit:4.13.2 test-only

§PROTOS
pb/example.proto: pkg=example_protos java=com.geekinasuit.polyglot.example.protos outer=ExampleProtos
  gopkg=github.com/geekinasuit/polyglot/golang/pkg/libs/brackets py_generic_services=true
  msg Something{int64 id=1, string name=2, repeated string labels=3}

pb/brackets_service.proto: pkg=brackets_service java=com.geekinasuit.polyglot.brackets.service.protos
  outer=BalanceBracketsService java_multiple_files=true
  gopkg=github.com/geekinasuit/polyglot/golang/pkg/services/brackets/proto
  svc BB{rpc Balance(BR{string statement=1})→BRes{bool succeeded=1,string error=2,bool is_balanced=3}}

§BZL_MODULES
bazel_skylib:1.8.2 rules_go:0.57.0 gazelle:0.45.0 rules_kotlin:2.1.9 rules_jvm_external:6.8
rules_java:8.14.0 rules_rust:0.65.0 rules_rust_prost:0.65.0 rules_python:1.6.3
protobuf:32.1 grpc:1.74.1 grpc-java:1.74.0 grpc_kotlin:1.5.0 protoc-gen-validate:1.2.1.bcr.1
go-deps: gazelle go_deps ext ← go.work (gods,cobra,testify)
maven: truth:1.4.4 junit:4.13.2 [test]; armeria,grpc-netty,grpc-kotlin-stub,clikt,kotlin-logging,protobuf-kotlin,coroutines,guava,kotlinpoet [runtime]
rust: edition=2021 ver=1.85.0; crates←Cargo.lock; manifests=Cargo.toml+r/bracketslib/Cargo.toml
python: ver=3.11; pip←requirements.txt; hub=py_deps

§BZL_WORKAROUND
grpc_kotlin:1.5.0 NOT bzlmod-native; hardcodes repo-names(com_google_guava_guava etc)
RC: --enable_workspace=true [required]
MOD:65-70: guava+kotlinpoet declared as maven artifacts w/ legacy names
MOD:79: use_repo names com_google_guava_guava,maven,grpc_kotlin_maven explicitly
MOD:4-7: explanatory comment

§RC
common --enable_workspace=true --java_runtime_version=21
[RESOLVED: disk_cache+remote_cache removed from repo .bazelrc; devs use user.bazelrc/~/.bazelrc]

§BZL_PROTO_CODEGEN
java: java_proto_library(@protobuf//bazel)←//pb:protobuf→Java-msg
kt-msg-custom: kt_jvm_proto_library(//u:kt_jvm_proto.bzl)←//pb:protobuf→KT-DSL-ext
kt-msg-grpc: kt_jvm_proto_library(@grpc_kotlin)←//pb:balance_rpc→KT-msg
kt-grpc: kt_jvm_grpc_library(@grpc_kotlin)←//pb:balance_rpc→CoroutineStub+AsyncService
go-msg: go_proto_library(@rules_go//proto)←//pb:protobuf→Go-proto-pkg
go-grpc: go_grpc_library(@rules_go//proto)←//pb:protobuf[BUG:should=balance_rpc;TICKET]→Go-GRPC
py: py_proto_library(@protobuf//bazel)←//pb:protobuf→Py-proto
rust: rust_prost_library(@rules_rust_prost)←//pb:protobuf→prost-types

§BZL_CONVENTIONS
kt-binary: java_binary(runtime_deps=kt_lib) NOT kt_jvm_binary [rules_kotlin-pattern]
kt-two-proto-paths: bracketskt→custom-macro(light,no-GRPC-dep); svc→@grpc_kotlin-native
go-embed: go_library(embed=[":proto"]) merges generated+handwritten same importpath
kt-test: associates=["//k/smk/bracketskt"] grants internal-member access in tests
pkg-pin: empty BUILD.bazel at //,//r,//py,//u [package-boundary only]
generated-code: NEVER committed; always derived from //pb targets at build time

§MACRO_kt_jvm_proto.bzl
src: copied grpc/grpc-kotlin@a969a91 Apache-2.0
purpose: KT proto-DSL codegen ONLY (not GRPC stubs)
impl(_kt_jvm_proto_library_helper_impl L21-67):
  1. collect ProtoInfo transitive_descriptor_sets depset
  2. declare_directory(<name>/ktproto)
  3. ctx.actions.run(protoc --kotlin_out=dir --descriptor_set_in=...) [param-file multiline @%s]
  4. zip .kt files→<name>.srcjar via @bazel_tools//tools/zip:zipper [mnemonic:KtProtoSrcJar]
public(kt_jvm_proto_library L97):
  5. create java_proto_library OR java_lite_proto_library named <name>_DO_NOT_DEPEND_java_proto
  6. run helper as <name>_DO_NOT_DEPEND_kt_proto
  7. kt_jvm_library(srcs=[srcjar]) dep=@maven//:protobuf-kotlin; exports java_proto_library
path-helper(_get_real_short_path L9-19): strips ../<repo>/ prefix + _virtual_imports/<name>/ infix

§OTHER_LANGS
all: stack-based bracket-check + foo() proto-demo; NO GRPC svc/client
algo-errors[identical across all langs]:
  close-no-opener: "closing bracket {c} with no opening bracket at char {i+1}"
  mismatch: "closing bracket {c} at char {i+1} mismatched with last opening bracket {open}"
  unclosed: "opening brackets without closing brackets found: [{stack}]"

go: 2mods(g/pkg/libs/brackets; g/cmd/brackets)
  stack=linkedliststack(emirpasic/gods) CLI=cobra def-text="Hello, (world)!"
  proto-demo: Something{Id:2,Name:"blah",Labels:["Q","R"]} struct-literal
  native-build: generate.go(//go:generate protoc x2); Makefile targets: go-generate,go-build,go-test,go-run
  BZL: generate.go in go_library srcs(L8) w/ comment "native go build only not bazel"

java: stack=ArrayDeque<Character>
  proto-demo: Something.newBuilder() id=1 name="Foo" addAllLabels(["A","B"])
  tests: 8 Truth+JUnit4; no GRPC; only java_proto_library for example.proto

py: stack=list(append/pop)
  proto-demo: foo()→None [#import protobuf.example_pb2 as pb commented out]
  tests: unittest.TestCase+pytruth(truth.truth.AssertThat); testFoo→pass
  BZL: example_proto_py(py_proto_library) exists as dep; unused at runtime [TICKET:python-proto-incomplete]

rust: stack=Vec<char>
  proto: prost via rust_prost_library; use protobuf::example_protos::Something
  proto-demo: foo()→Option<Something>{id:1,name:"foo",labels:["a","b"]} struct-literal
  tests: assertor(anyhow feature) #[cfg(test)] mod tests
  Cargo.toml: NO tonic; proto-codegen=BZL-only(rust_prost_library)

§CODE_REFS
pb/brackets_service.proto:12-14 BB-svc-def+Balance-RPC
pb/example.proto:14-18 Something-msg
k/BUILD.bazel:4-15 kt_jvm_proto_library+kt_jvm_grpc_library
k/BUILD.bazel:17-33 java_binary wrappers(client+service)
k/smk/cgpb/service/BSE.kt:15 AsyncService+BindableService impl
k/smk/cgpb/service/service.kt:26 Armeria-server-construction
k/smk/cgpb/service/service.kt:34-39 wrapService()
k/smk/cgpb/client/client.kt:28 runBlocking
k/smk/cgpb/client/client.kt:36-37 channel+stub construction
k/smk/bracketskt/brackets.kt:41 push-extension
k/smk/bracketskt/BUILD.bazel:13-19 custom-macro usage
k/test/kotlin/bracketskt/BUILD.bazel:6 associates
u/kt_jvm_proto.bzl:9-19 _get_real_short_path
u/kt_jvm_proto.bzl:21-67 protoc+srcjar impl
u/kt_jvm_proto.bzl:97 macro public sig
g/pkg/libs/brackets/generate.go //go:generate directives
g/pkg/libs/brackets/BUILD.bazel:4-16 go_library(embed=[":proto"])
MOD:4-7 grpc_kotlin workaround comment
MOD:65-70 guava/kotlinpoet aliases
RC:1 --enable_workspace
RC:5 [removed; was machine-specific disk_cache; resolved]

§ARCH
1. KT=reference-impl; others=algo+proto-only; future-work to bring up
2. Armeria intentional: higher-level HTTP2; svc-impl uses std grpc-java ifaces→portable
3. asymmetric: server=Armeria; client=grpc-netty-shaded+CoroutineStub
4. hybrid-GRPC: svc-impl=StreamObserver(Java-CB-style); client=coroutine-stub [AsyncService is generated-Java iface]
5. two-KT-proto-paths: avoid grpc-kotlin heavy deps in simple msg lib
6. bzlmod+escape-hatch: grpc_kotlin:1.5.0 needs --enable_workspace; MOD:4-7 documents
7. dual-build-go: bazel OR make; generate.go in both build graphs
8. proto-as-contract: //pb:protobuf+//pb:balance_rpc=sole sources; generated never committed

§TICKETS
grpc-kotlin-bzlmod-migration [low] when bzlmod-native grpc_kotlin available
go-grpc-library-wrong-proto-target [low] BUILD.bazel:35 //pb:protobuf→should be //pb:balance_rpc
python-proto-integration-incomplete [low] import+foo() disabled
kotlin-grpc-interceptors-not-implemented [low] wrapService() vararg hook unused
go-grpc-client-server [med] svc+client; prereq:go-grpc-library-fix; ref:KT
java-grpc-client-server [med] svc+client; ref:KT
python-grpc-client-server [med] svc+client; prereq:python-proto-fix; ref:KT
rust-grpc-client-server [med] add tonic; svc+client; ref:KT
typescript-grpc-openapi-variant [med] new-lang; GRPC-svc+client(KT-compat)+OpenAPI-REST; dual yarn/npm+BZL; BZL-structure-ref:KT

§HISTORY
first research doc for project; thoughts/ scaffold was empty prior to this session
