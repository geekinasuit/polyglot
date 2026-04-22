package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.polyglot.brackets.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Paths
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect

fun createMigratedDataSource(config: DatabaseConfig): DataSource {
  val migrationsPath =
      Paths.get(System.getenv("RUNFILES_DIR") ?: ".", "_main/db/migrations").toAbsolutePath()

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
        .locations("filesystem:$migrationsPath")
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
