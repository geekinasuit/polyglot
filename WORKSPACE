# grpc-kotlin does not support bzlmod, so load it the old fashioned way. TODO: Fix name and bzlmod usage when supported.
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "com_github_grpc_grpc_kotlin",
    repo_mapping = {
        "@io_bazel_rules_kotlin": "@rules_kotlin",
        "@io_grpc_grpc_java": "@grpc-java",
    },
    sha256 = "a218306e681318cbbc3b0e72ec9fe1241b2166b735427a51a3c8921c3250216f",
    strip_prefix = "grpc-kotlin-1.4.2",
    url = "https://github.com/grpc/grpc-kotlin/archive/refs/tags/v1.4.2.zip",
)
