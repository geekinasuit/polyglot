package com.geekinasuit.polyglot.brackets.config

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Base class for commands driven by a Hoplite config file with selective CLI overrides.
 *
 * Declare CLI options using [applyTo] to bind each option to a mutation on the config type [C].
 * When the option is provided on the command line, the mutation fires; if absent, the config file
 * value is left unchanged. Call [resolveConfig] inside [run] to obtain the final merged config
 * after all CLI overrides have been applied.
 *
 * Example:
 * ```kotlin
 * class MyCommand : ConfigCommand<MyConfig>("my-command") {
 *   val host by option("--host").applyTo { copy(server = server.copy(host = it)) }
 *   val port by option("--port").int().applyTo { copy(server = server.copy(port = it)) }
 *
 *   override fun run() {
 *     val config = resolveConfig(loadConfig("/my/application.yml"))
 *   }
 * }
 * ```
 *
 * Validation: at startup, [assertAllOptionsAreBound] can be called to confirm every registered
 * Clikt option has a corresponding [applyTo] binding. Any option declared without [applyTo] will be
 * parsed and available, but its value will not be applied to the config automatically.
 */
abstract class ConfigCommand<C : Any>(name: String) : CliktCommand(name = name) {
  private val mutations = mutableListOf<C.() -> C>()

  /**
   * Binds this option to a config mutation. When the option is provided on the CLI, [mutate] is
   * invoked on the current config with the parsed value, and the result replaces the config. When
   * the option is absent, [mutate] is not called and the config is unchanged.
   *
   * Returns a [ReadOnlyProperty] delegate; use with `by` as normal.
   */
  fun <T : Any> OptionWithValues<T?, T, T>.applyTo(
      mutate: C.(T) -> C
  ): MutatingOptionDelegate<T, C> = MutatingOptionDelegate(this, mutations, mutate)

  /**
   * Applies all registered CLI mutations to [base] in declaration order. Call this inside [run]
   * after option parsing is complete.
   */
  protected fun resolveConfig(base: C): C = mutations.fold(base) { cfg, m -> cfg.m() }

  /**
   * Asserts that the number of registered [applyTo] bindings equals the number of non-eager options
   * registered with Clikt. A mismatch means at least one option was declared without a
   * corresponding config binding.
   *
   * Call early in [run] when you want startup-time validation.
   */
  protected fun assertAllOptionsAreBound() {
    // Exclude Clikt's built-in eager options (--help, --version, etc.) from the count.
    val userOptionCount =
        registeredOptions().count { opt ->
          opt.names.any { it.startsWith("--") } &&
              opt.names.none { it == "--help" || it == "--version" }
        }
    check(userOptionCount == mutations.size) {
      "Option/config binding mismatch: $userOptionCount option(s) registered, " +
          "${mutations.size} applyTo binding(s) found. " +
          "Every '--' option should use applyTo { } to bind it to the config."
    }
  }
}

/**
 * A property delegate produced by [ConfigCommand.applyTo].
 *
 * When bound via `by`, it registers the underlying Clikt option with the command (so help text,
 * parsing, and type coercion work as usual) and simultaneously registers a lazy mutation that reads
 * the option's parsed value at [ConfigCommand.resolveConfig] time.
 */
class MutatingOptionDelegate<T : Any, C : Any>(
    private val wrapped: OptionWithValues<T?, T, T>,
    private val mutations: MutableList<C.() -> C>,
    private val mutate: C.(T) -> C,
) {
  operator fun provideDelegate(
      thisRef: ConfigCommand<C>,
      prop: KProperty<*>,
  ): ReadOnlyProperty<CliktCommand, T?> {
    val inner = wrapped.provideDelegate(thisRef, prop)
    mutations.add { inner.getValue(thisRef, prop)?.let { v -> mutate(v) } ?: this }
    return inner
  }
}
