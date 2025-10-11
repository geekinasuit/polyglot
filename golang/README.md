# Go implementation of brackets checker.

This is implemented both using bazel and go-build/go-generate using a Makefile in the root. This is to attempt to
maintain a consistent build in both build systems. The bazel build pulls from the go workspace to resolve dependencies.
It does not use gazelle to generate the BUILD files, rather maintains build files as first-class citizens of the repo.

## bazel
The bazel build is the default build. Protocol buffers are generated as a part of the build using normal grpc/proto
bazel rules. 

## go-build / Makefile
Go build does not automatically incorporate code generation steps - rather it uses go-generate to generate the code,
via a makefile step.