package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.polyglot.brackets.config.DatabaseConfig
import dagger.Module
import dagger.Provides
import java.util.UUID
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.impl.DSL

@Module
object TestDatabaseModule {
  @Provides
  @ApplicationScope
  fun databaseConfig(): DatabaseConfig =
      DatabaseConfig(
          jdbcUrl = "jdbc:h2:mem:brackets-test-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
      )

  @Provides
  @ApplicationScope
  fun dataSource(config: DatabaseConfig): DataSource = createMigratedDataSource(config)

  @Provides
  @ApplicationScope
  fun dslContext(dataSource: DataSource, config: DatabaseConfig): DSLContext =
      DSL.using(dataSource, detectDialect(config.jdbcUrl))
}
