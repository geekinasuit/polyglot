load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

# Copied from https://github.com/grpc/grpc-kotlin/blob/a969a91ca37867fa28d83fca234a2b4ced7b1540/kt_jvm_grpc.bzl#L319
# which is also Apache 2.0 licensed.
#
# There is no proper kt_proto_library or whatever, so this wraps kt_library and proto library, achieving a similar
# effect.

def _get_real_short_path(file):
    """Returns the correct short path file name to be used by protoc."""
    short_path = file.short_path
    if short_path.startswith("../"):
        second_slash = short_path.index("/", 3)
        short_path = short_path[second_slash + 1:]

    virtual_imports = "_virtual_imports/"
    if virtual_imports in short_path:
        short_path = short_path.split(virtual_imports)[1].split("/", 1)[1]
    return short_path

def _kt_jvm_proto_library_helper_impl(ctx):
    transitive_set = depset(
        transitive =
            [dep[ProtoInfo].transitive_descriptor_sets for dep in ctx.attr.proto_deps],
    )
    proto_sources = []
    for dep in ctx.attr.proto_deps:
        for file in dep[ProtoInfo].direct_sources:
            proto_sources.append(_get_real_short_path(file))

    gen_src_dir = ctx.actions.declare_directory(ctx.label.name + "/ktproto")

    protoc_args = ctx.actions.args()
    protoc_args.set_param_file_format("multiline")
    protoc_args.use_param_file("@%s")
    protoc_args.add("--kotlin_out=" + gen_src_dir.path)
    protoc_args.add_joined(
        transitive_set,
        join_with = ctx.configuration.host_path_separator,
        format_joined = "--descriptor_set_in=%s",
    )
    protoc_args.add_all(proto_sources)

    ctx.actions.run(
        inputs = depset(transitive = [transitive_set]),
        outputs = [gen_src_dir],
        executable = ctx.executable._protoc,
        arguments = [protoc_args],
        progress_message = "Generating kotlin proto extensions for " +
                           ", ".join([
                               str(dep.label)
                               for dep in ctx.attr.proto_deps
                           ]),
    )

    # Because protoc outputs an unknown number of files we need to zip them into a srcjar.
    args = ctx.actions.args()
    args.add("c")
    args.add(ctx.outputs.srcjar)
    args.add_all([gen_src_dir])
    ctx.actions.run(
        arguments = [args],
        executable = ctx.executable._zip,
        inputs = [gen_src_dir],
        mnemonic = "KtProtoSrcJar",
        outputs = [ctx.outputs.srcjar],
    )

_kt_jvm_proto_library_helper = rule(
    attrs = dict(
        proto_deps = attr.label_list(
            providers = [ProtoInfo],
        ),
        deps = attr.label_list(
            providers = [JavaInfo],
        ),
        exports = attr.label_list(
            allow_rules = ["java_proto_library"],
        ),
        _protoc = attr.label(
            default = Label("@protobuf//:protoc"),
            cfg = "exec",
            executable = True,
        ),
        _zip = attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
        ),
    ),
    implementation = _kt_jvm_proto_library_helper_impl,
    outputs = dict(
        srcjar = "%{name}.srcjar",
    ),
)

def kt_jvm_proto_library(
        name,
        deps = None,
        java_deps = None,
        tags = None,
        testonly = None,
        compatible_with = None,
        restricted_to = None,
        visibility = None,
        flavor = None,
        deprecation = None,
        features = []):
    """
    This rule accepts any number of proto_library targets in "deps", translates them to Kotlin and
    returns the compiled Kotlin.

    See also https://developers.google.com/protocol-buffers/docs/kotlintutorial for how to interact
    with the generated Kotlin representation.

    If "java_deps" are set, they must be exactly the java_proto_library targets for "deps". If
    "java_deps" are not set, this rule will generate and export the java protos for all "deps",
    using "flavor" to determine their type.

    For standard attributes, see:
      https://docs.bazel.build/versions/master/be/common-definitions.html#common-attributes

    Args:
      name: A name for the target
      deps: One or more proto_library targets to turn into Kotlin.
      java_deps: (optional) java_proto_library targets corresponding to deps
      tags: Standard attribute
      testonly: Standard attribute
      compatible_with: Standard attribute
      restricted_to: Standard attribute
      visibility: Standard attribute
      flavor: "normal" (default) for normal proto runtime, or "lite" for the lite runtime
        (for Android usage)
      deprecation: Standard attribute
      features: Standard attribute
    """
    if (java_deps != None and len(java_deps) > 0):
        java_protos = java_deps
        java_exports = []
    else:
        java_proto_target = ":%s_DO_NOT_DEPEND_java_proto" % name
        if flavor == "lite":
            native.java_lite_proto_library(
                name = java_proto_target[1:],
                deps = deps,
                testonly = testonly,
                compatible_with = compatible_with,
                visibility = ["//visibility:private"],
                restricted_to = restricted_to,
                tags = tags,
                deprecation = deprecation,
                features = features,
            )
        else:
            native.java_proto_library(
                name = java_proto_target[1:],
                deps = deps,
                testonly = testonly,
                compatible_with = compatible_with,
                visibility = ["//visibility:private"],
                restricted_to = restricted_to,
                tags = tags,
                deprecation = deprecation,
                features = features,
            )
        java_protos = [java_proto_target]
        java_exports = [java_proto_target]

    helper_target = ":%s_DO_NOT_DEPEND_kt_proto" % name
    _kt_jvm_proto_library_helper(
        name = helper_target[1:],
        proto_deps = deps,
        deps = java_protos,
        testonly = testonly,
        compatible_with = compatible_with,
        visibility = ["//visibility:private"],
        restricted_to = restricted_to,
        tags = tags,
        deprecation = deprecation,
        features = features,
    )

    kt_jvm_library(
        name = name,
        srcs = [helper_target + ".srcjar"],
        deps = [
            "@maven//:com_google_protobuf_protobuf_kotlin",
        ] + java_protos,
        exports = java_exports,
        testonly = testonly,
        compatible_with = compatible_with,
        visibility = visibility,
        restricted_to = restricted_to,
        tags = tags,
        deprecation = deprecation,
        features = features,
    )
