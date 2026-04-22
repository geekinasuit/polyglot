package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.polyglot.brackets.config.DatabaseConfig
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import dagger.Module
import dagger.Provides
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.impl.DSL

private val log = KotlinLogging.logger {}

@Module
object DatabaseModule {
  @Provides
  @ApplicationScope
  fun databaseConfig(config: ServiceAppConfig): DatabaseConfig = config.db

  @Provides
  @ApplicationScope
  fun dataSource(config: DatabaseConfig): DataSource {
    if (config.jdbcUrl.contains(":h2:")) {
      log.warn {
        "DatabaseConfig.jdbcUrl points to an in-memory H2 database -- data will not persist across restarts"
      }
    }
    return createMigratedDataSource(config)
  }

  @Provides
  @ApplicationScope
  fun dslContext(dataSource: DataSource, config: DatabaseConfig): DSLContext =
      DSL.using(dataSource, detectDialect(config.jdbcUrl))
}
