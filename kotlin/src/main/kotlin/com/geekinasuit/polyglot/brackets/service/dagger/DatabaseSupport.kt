package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.polyglot.brackets.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Paths
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect

fun createMigratedDataSource(config: DatabaseConfig): DataSource {
  // Determine Flyway migration location based on execution context:
  //   RUNFILES_DIR: set by the Bazel test runner
  //   JAVA_RUNFILES: set by the java_binary shell wrapper
  //   neither: running from a deploy jar; migrations are bundled as classpath resources
  val migrationsLocation =
      (System.getenv("RUNFILES_DIR") ?: System.getenv("JAVA_RUNFILES"))?.let { runfilesDir ->
        "filesystem:${Paths.get(runfilesDir, "_main/db/migrations").toAbsolutePath()}"
      } ?: "classpath:db/migrations"

  val hikariConfig =
      HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        maximumPoolSize = 5
      }
  val dataSource = HikariDataSource(hikariConfig)
  try {
    Flyway.configure()
        .dataSource(dataSource)
        .sqlMigrationPrefix("")
        .sqlMigrationSeparator("_")
        .locations(migrationsLocation)
        .load()
        .migrate()
  } catch (e: Exception) {
    dataSource.close()
    throw e
  }
  return dataSource
}

fun detectDialect(jdbcUrl: String): SQLDialect =
    when {
      jdbcUrl.startsWith("jdbc:h2:") -> SQLDialect.H2
      jdbcUrl.startsWith("jdbc:postgresql:") -> SQLDialect.POSTGRES
      jdbcUrl.startsWith("jdbc:sqlite:") -> SQLDialect.SQLITE
      else -> SQLDialect.DEFAULT
    }
