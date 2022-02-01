/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.maven

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.config.MeterFilter
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.openrewrite.Recipe
import org.openrewrite.RecipeTest
import org.openrewrite.internal.LoggingMeterRegistry
import org.openrewrite.maven.tree.Maven
import java.io.File
import java.nio.file.Path

@Suppress("unused")
interface MavenRecipeTest : RecipeTest<Maven> {
    companion object {
        private val meterRegistry = LoggingMeterRegistry.builder().build()

        @BeforeAll
        @JvmStatic
        fun setMeterRegistry() {
            meterRegistry.config()
                .meterFilter(MeterFilter.acceptNameStartsWith("rewrite.maven"))
                .meterFilter(MeterFilter.deny())
                .meterFilter(MeterFilter.ignoreTags("group.id", "artifact.id"))
            Metrics.globalRegistry.add(meterRegistry)
        }

        @AfterAll
        @JvmStatic
        fun unsetMeterRegistry() {
            Metrics.globalRegistry.remove(meterRegistry)
        }
    }

    @AfterEach
    fun printMetrics() {
        meterRegistry.print()
    }

    override val parser: MavenParser
        get() = MavenParser.builder().build()

    fun assertChanged(
        parser: MavenParser = this.parser,
        recipe: Recipe = this.recipe!!,
        @Language("xml") before: String,
        @Language("xml") dependsOn: Array<String> = emptyArray(),
        @Language("xml") after: String,
        cycles: Int = 2,
        expectedCyclesThatMakeChanges: Int = cycles - 1,
        afterConditions: (Maven) -> Unit = { }
    ) {
        super.assertChangedBase(
            parser,
            recipe,
            before,
            dependsOn,
            after,
            cycles,
            expectedCyclesThatMakeChanges,
            afterConditions
        )
    }

    fun assertChanged(
        parser: MavenParser = this.parser,
        recipe: Recipe = this.recipe!!,
        @Language("xml") before: File,
        relativeTo: Path? = null,
        @Language("xml") dependsOn: Array<File> = emptyArray(),
        @Language("xml") after: String,
        cycles: Int = 2,
        expectedCyclesThatMakeChanges: Int = cycles - 1,
        afterConditions: (Maven) -> Unit = { }
    ) {
        super.assertChangedBase(
            parser,
            recipe,
            before,
            relativeTo,
            dependsOn,
            after,
            cycles,
            expectedCyclesThatMakeChanges,
            afterConditions
        )
    }

    fun assertUnchanged(
        parser: MavenParser = this.parser,
        recipe: Recipe = this.recipe!!,
        @Language("xml") before: String,
        @Language("xml") dependsOn: Array<String> = emptyArray()
    ) {
        super.assertUnchangedBase(parser, recipe, before, dependsOn)
    }

    fun assertUnchanged(
        parser: MavenParser = this.parser,
        recipe: Recipe = this.recipe!!,
        @Language("xml") before: File,
        relativeTo: Path? = null,
        @Language("xml") dependsOn: Array<File> = emptyArray()
    ) {
        super.assertUnchangedBase(parser, recipe, before, relativeTo, dependsOn)
    }
}
