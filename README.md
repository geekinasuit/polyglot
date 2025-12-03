# polyglot
An exemplar project showing some basic cases in multiple languages, 
using bazel, intended for easy IDE integration

# Tooling Requirements

To run the build, some tools need to be installed. Most of these aren't
needed for the bazel build (other than the jdk and bazel/bazelisk itself)
as bazel downloads appropriate tooling for its rules, but are needed
for given languages' "native" builds (go build, cargo, turbo/yarn, etc.)

1. A java development kit, version 21
2. `bazelisk` (this will download the appropriate bazel version
   on demand) or `bazel` (see [.bazelversion](.bazelversion) for
   which version of bazel)
3. `go` language (> 1.24)
4. `protoc-gen-openapi` (v0.7.0)

