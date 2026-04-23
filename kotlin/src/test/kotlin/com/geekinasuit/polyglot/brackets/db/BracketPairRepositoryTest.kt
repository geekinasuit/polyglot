package com.geekinasuit.polyglot.brackets.db

import com.geekinasuit.polyglot.brackets.db.jooq.Tables.BRACKET_PAIR
import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Paths
import java.util.UUID
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.After
import org.junit.Before
import org.junit.Test

class BracketPairRepositoryTest {
  private lateinit var dataSource: HikariDataSource

  @Before
  fun setUp() {
    val jdbcUrl = "jdbc:h2:mem:repo-test-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val config =
        HikariConfig().apply {
          this.jdbcUrl = jdbcUrl
          maximumPoolSize = 2
        }
    dataSource = HikariDataSource(config)
    val runfilesDir = System.getenv("RUNFILES_DIR") ?: System.getenv("JAVA_RUNFILES") ?: "."
    val migrationsPath = Paths.get(runfilesDir, "_main/db/migrations").toAbsolutePath()
    Flyway.configure()
        .dataSource(dataSource)
        .sqlMigrationPrefix("")
        .sqlMigrationSeparator("_")
        .locations("filesystem:$migrationsPath")
        .load()
        .migrate()
  }

  @After
  fun tearDown() {
    dataSource.close()
  }

  @Test
  fun loadEnabledPairs_returnsAllEnabledRows() {
    val dsl = DSL.using(dataSource, SQLDialect.H2)
    val repo = BracketPairRepositoryImpl(dsl)
    val pairs = repo.loadEnabledPairs()
    assertThat(pairs).containsExactlyEntriesIn(mapOf(')' to '(', ']' to '[', '}' to '{'))
  }

  @Test
  fun loadEnabledPairs_excludesDisabledRows() {
    val dsl = DSL.using(dataSource, SQLDialect.H2)
    dsl.update(BRACKET_PAIR).set(BRACKET_PAIR.ENABLED, false).where(BRACKET_PAIR.ID.eq(1)).execute()
    val repo = BracketPairRepositoryImpl(dsl)
    val pairs = repo.loadEnabledPairs()
    assertThat(pairs).containsExactlyEntriesIn(mapOf(']' to '[', '}' to '{'))
  }
}
