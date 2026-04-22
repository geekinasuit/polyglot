package com.geekinasuit.polyglot.brackets.db.codegen

import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Property
import org.jooq.meta.jaxb.Target

/**
 * Codegen runner that takes DDL SQL files and an output directory as CLI arguments, then invokes
 * JOOQ's GenerationTool to produce Java sources from DDL via DDLDatabase.
 *
 * Usage: JooqCodegenRunner <ddl-file>... <output-dir>
 * - All args except the last are DDL input file paths (semicolon-joined for JOOQ)
 * - The last arg is the output directory
 */
fun main(args: Array<String>) {
  require(args.size >= 2) { "Usage: JooqCodegenRunner <ddl-file>... <output-dir>" }

  val ddlFiles = args.dropLast(1).joinToString(";")
  val outputDir = args.last()

  val configuration =
      Configuration()
          .withGenerator(
              Generator()
                  .withDatabase(
                      Database()
                          .withName("org.jooq.meta.extensions.ddl.DDLDatabase")
                          .withProperties(Property().withKey("scripts").withValue(ddlFiles))
                  )
                  .withTarget(
                      Target()
                          .withPackageName("com.geekinasuit.polyglot.brackets.db.jooq")
                          .withDirectory(outputDir)
                  )
          )

  GenerationTool.generate(configuration)
}
